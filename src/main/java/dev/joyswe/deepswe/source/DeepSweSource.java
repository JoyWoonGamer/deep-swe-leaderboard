package dev.joyswe.deepswe.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import dev.joyswe.deepswe.cache.LeaderboardCacheStore;
import dev.joyswe.deepswe.model.LeaderboardEntryVo;
import dev.joyswe.deepswe.model.LeaderboardMetaVo;
import dev.joyswe.deepswe.model.LeaderboardPayload;
import dev.joyswe.deepswe.model.LeaderboardRow;

/**
 * DeepSWE 排行榜数据源。
 *
 * <p>抓取链路（多级降级）：live 实时站点（版本探测）→ GitHub 每日镜像。
 * 两者均失败则返回 empty，由 {@link AbstractLeaderboardSource} 记录失败计数。
 * 展示行 = 每个模型取其最佳推理强度配置，按 pass_rate 降序。</p>
 */
public class DeepSweSource extends AbstractLeaderboardSource {

    public static final String MIRROR_URL =
        "https://raw.githubusercontent.com/benchget/deepswe/main/data/leaderboard-live.json";

    private static final String LIVE_URL =
        "https://deepswe.datacurve.ai/artifacts/%s/leaderboard-live.json";

    /** 站点数据页：HTML 内嵌版本导航（aria-label="Benchmark version"），可解析出当前全部可用版本。 */
    private static final String DATA_PAGE_URL =
        "https://deepswe.datacurve.ai/data/v1.1";

    private static final String[] VERSION_FALLBACKS = {"v1.1", "v1", "v1.2", "v2", "v1.0"};

    /** 版本发现结果缓存时长（毫秒）：官方版本季度级变更，6 小时足够且避免每次抓取多一次请求。 */
    private static final long VERSION_TTL_MS = 6 * 60 * 60 * 1000L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebClient webClient = WebClient.create();

    private final String preferredVersion;

    /** 版本发现缓存：volatile 写 + 时间戳判断 TTL。 */
    private volatile List<String> discoveredVersions = List.of();
    private volatile long versionsDiscoveredAt;
    /** 探测防重入（并发保护：同一时刻只发起一次探测）。 */
    private final java.util.concurrent.atomic.AtomicBoolean versionProbeRunning =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    public DeepSweSource(LeaderboardCacheStore cacheStore, String preferredVersion) {
        super(cacheStore);
        this.preferredVersion = preferredVersion;
    }

    @Override
    public String id() {
        return "deepswe";
    }

    @Override
    public String displayName() {
        return "DeepSWE";
    }

    @Override
    public String description() {
        return "长程软件工程基准 · 模型 Pass@1 / 成本 / 输出 Token / Agent 步数";
    }

    @Override
    protected Mono<FetchResult> doFetch() {
        return liveOrEmpty(preferredVersion)
            .map(p -> new FetchResult(p, "live"))
            .switchIfEmpty(fetchFromMirror().map(p -> new FetchResult(p, "mirror")));
    }

    private Mono<LeaderboardPayload> fetchFromMirror() {
        return getJson(MIRROR_URL);
    }

    private Mono<LeaderboardPayload> fetchFromLive(String version) {
        return getJson(String.format(LIVE_URL, version));
    }

    private Mono<LeaderboardPayload> liveOrEmpty(String preferred) {
        List<String> versions = buildCandidateVersions(preferred);
        return Flux.fromIterable(versions)
            .concatMap(this::fetchFromLive)
            .next();
    }

    /** 候选版本顺序：后台手填首选 > 站点探测到的可用版本 > 硬编码兜底列表（均去重）。 */
    private List<String> buildCandidateVersions(String preferred) {
        List<String> versions = new ArrayList<>();
        if (preferred != null && !preferred.isBlank()) {
            versions.add(preferred.trim());
        }
        for (String v : discoverVersions()) {
            if (!versions.contains(v)) {
                versions.add(v);
            }
        }
        for (String v : VERSION_FALLBACKS) {
            if (!versions.contains(v)) {
                versions.add(v);
            }
        }
        return versions;
    }

    /**
     * 返回当前可用版本列表：缓存有效直接返回；缓存过期时异步发起探测（不阻塞调用线程），
     * 本次先返回既有缓存/空列表，探测结果下次抓取生效。
     */
    private List<String> discoverVersions() {
        long now = System.currentTimeMillis();
        if (!discoveredVersions.isEmpty() && now - versionsDiscoveredAt < VERSION_TTL_MS) {
            return discoveredVersions;
        }
        // 缓存过期：非阻塞发起探测（WebFlux event loop 线程不能被 block 污染）。
        if (versionProbeRunning.compareAndSet(false, true)) {
            webClient.get()
                .uri(DATA_PAGE_URL)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(timeout())
                .map(this::parseVersionsFromHtml)
                .doOnNext(found -> {
                    if (!found.isEmpty()) {
                        discoveredVersions = List.copyOf(found);
                        versionsDiscoveredAt = System.currentTimeMillis();
                        logInfo("版本探测成功: " + discoveredVersions);
                    }
                })
                .doFinally(sig -> versionProbeRunning.set(false))
                .subscribe(null, e -> logError("版本探测失败（使用既有版本列表）", e));
        }
        return discoveredVersions;
    }

    /** 从数据页 HTML 解析版本导航中的版本号列表（去重保序）。 */
    private List<String> parseVersionsFromHtml(String html) {
        List<String> found = new ArrayList<>();
        if (html == null) {
            return found;
        }
        // 版本导航：<a href="/data/v1.1" ...>v1.1 (latest)</a> 或 <a href="/data/v1">v1</a>
        java.util.regex.Pattern pattern =
            java.util.regex.Pattern.compile("href=\"/data/(v[^\"?#]+)\"",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String v = matcher.group(1);
            if (!found.contains(v)) {
                found.add(v);
            }
        }
        return found;
    }

    private Mono<LeaderboardPayload> getJson(String url) {
        return webClient.get()
            .uri(url)
            .retrieve()
            .onStatus(status -> status.isError(),
                resp -> Mono.error(new IllegalStateException("HTTP " + resp.statusCode() + " " + url)))
            .bodyToMono(String.class)
            .timeout(timeout())
            .map(body -> {
                LeaderboardPayload payload;
                try {
                    payload = MAPPER.readValue(body, LeaderboardPayload.class);
                } catch (Exception e) {
                    throw new IllegalStateException("JSON parse failed for " + url, e);
                }
                if (payload == null || payload.rows().isEmpty()) {
                    throw new IllegalStateException("empty rows for " + url);
                }
                return payload;
            })
            .onErrorResume(err -> {
                logError("抓取失败 url=" + url, err);
                return Mono.empty();
            });
    }

    @Override
    protected LeaderboardMetaVo buildMeta() {
        LeaderboardMetaVo meta = new LeaderboardMetaVo();
        LeaderboardPayload p = (LeaderboardPayload) currentPayload();
        if (p != null) {
            meta.setNTasks(p.nTasksInSet() == null ? 0 : p.nTasksInSet());
        }
        return meta;
    }

    @Override
    protected Instant parseGeneratedAt(Object payload) {
        if (payload instanceof LeaderboardPayload p && p.generatedAt() != null) {
            try {
                return Instant.parse(p.generatedAt());
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    protected String buildError() {
        return "所有数据源均不可用（实时站点与镜像均抓取失败）";
    }

    /** 每个模型取其最佳推理强度配置，按 pass_rate 降序。 */
    @Override
    protected List<LeaderboardEntryVo> toVoList() {
        LeaderboardPayload p = (LeaderboardPayload) currentPayload();
        if (p == null) {
            return List.of();
        }
        Map<String, LeaderboardRow> best = new LinkedHashMap<>();
        for (LeaderboardRow row : p.rows()) {
            if (row.model() == null || row.passRate() == null) {
                continue;
            }
            LeaderboardRow cur = best.get(row.model());
            if (cur == null || row.passRate() > cur.passRate()) {
                best.put(row.model(), row);
            }
        }
        return best.values().stream()
            .sorted(Comparator.comparingDouble((LeaderboardRow row) -> row.passRate()).reversed())
            .map(this::toVo)
            .toList();
    }

    private LeaderboardEntryVo toVo(LeaderboardRow row) {
        LeaderboardEntryVo vo = new LeaderboardEntryVo();
        vo.setModel(row.model());
        vo.setDisplayName(humanize(row.model()));
        vo.setEffort(row.reasoningEffort() == null ? "" : row.reasoningEffort());
        vo.setProvider(row.provider() == null ? "" : row.provider());
        double passRate = row.passRate() == null ? 0 : row.passRate();
        vo.setPassRate(passRate);
        vo.setPassRatePct((int) Math.round(passRate * 100));
        vo.setCiHalfPct(row.ciHalf() == null ? 0 : Math.round(row.ciHalf() * 1000) / 10.0);
        vo.setCost(row.meanCostUsd() == null ? 0 : Math.round(row.meanCostUsd() * 100) / 100.0);
        vo.setOutTok(row.meanOutputTokens() == null ? 0 : Math.round(row.meanOutputTokens()));
        vo.setSteps(row.meanAgentSteps() == null ? 0 : Math.round(row.meanAgentSteps()));
        vo.setDurationSeconds(row.meanDurationSeconds() == null ? 0 : Math.round(row.meanDurationSeconds()));
        vo.setNAttempted(row.nAttempted() == null ? 0 : row.nAttempted());
        vo.setNTasksPassedAny(row.nTasksPassedAny() == null ? 0 : row.nTasksPassedAny());
        return vo;
    }

    private static String humanize(String model) {
        return model == null ? "" : model.replace('_', ' ');
    }
}
