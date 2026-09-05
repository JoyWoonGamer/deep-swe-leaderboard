package run.halo.plugin.deepswe.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.plugin.deepswe.client.DeepSweClient;
import run.halo.plugin.deepswe.model.LeaderboardEntryVo;
import run.halo.plugin.deepswe.model.LeaderboardMetaVo;
import run.halo.plugin.deepswe.model.LeaderboardPayload;
import run.halo.plugin.deepswe.model.LeaderboardRow;

/**
 * 排行榜缓存与刷新服务。
 * 内存中保存最近一次成功抓取的数据，供 Finder / REST 接口读取。
 * 定时任务（{@link run.halo.plugin.deepswe.scheduler.LeaderboardScheduler}）按配置的间隔触发刷新。
 */
@Service
public class LeaderboardService {

    private final DeepSweClient client;
    private final ReactiveSettingFetcher settingFetcher;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile LeaderboardPayload payload;
    private volatile String source;
    private volatile Instant fetchedAt;
    private volatile Instant generatedAt;
    private volatile String error;

    public LeaderboardService(DeepSweClient client, ReactiveSettingFetcher settingFetcher) {
        this.client = client;
        this.settingFetcher = settingFetcher;
    }

    /** 读取配置，若已启用且到达刷新间隔则抓取一次。 */
    public Mono<Void> refreshIfNeeded() {
        return fetchSetting()
            .flatMap(cfg -> {
                if (!cfg.enabled()) {
                    return Mono.empty();
                }
                long intervalMs = cfg.refreshMinutes() * 60_000L;
                Instant now = Instant.now();
                boolean due = fetchedAt == null || now.isAfter(fetchedAt.plusMillis(intervalMs));
                return due ? doFetch(cfg) : Mono.empty();
            })
            .onErrorResume(e -> {
                error = "读取配置失败: " + e.getMessage();
                return Mono.empty();
            })
            .then();
    }

    /** 非阻塞 SWR：遇到请求时若缓存过期/为空，后台异步重拉一次，不阻塞当前读取（读仍取旧缓存）。 */
    public void refreshIfNeededAsync() {
        try {
            refreshIfNeeded().subscribe();
        } catch (RuntimeException ignored) {
            // 触发失败不影响当前请求
        }
    }

    private Mono<Void> doFetch(DeepSweSetting cfg) {
        if (!running.compareAndSet(false, true)) {
            return Mono.empty();
        }
        return client.fetch(cfg)
            .doOnNext(result -> {
                this.payload = result.payload();
                this.source = result.source();
                this.fetchedAt = Instant.now();
                this.generatedAt = parseInstant(result.payload().generatedAt());
                this.error = null;
            })
            .switchIfEmpty(Mono.defer(() -> {
                this.error = "所有数据源均不可用（镜像与实时站点均抓取失败）";
                return Mono.empty();
            }))
            .doFinally(signal -> running.set(false))
            .then();
    }

    private Mono<DeepSweSetting> fetchSetting() {
        return settingFetcher.fetch(DeepSweSetting.GROUP, DeepSweSetting.class)
            .map(setting -> setting == null ? new DeepSweSetting() : setting)
            .switchIfEmpty(Mono.just(new DeepSweSetting()));
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 全部行，按 pass_rate 降序。 */
    public List<LeaderboardEntryVo> all() {
        LeaderboardPayload current = payload;
        if (current == null) {
            return List.of();
        }
        return current.rows().stream()
            .filter(row -> row.passRate() != null)
            .sorted(Comparator.comparingDouble((LeaderboardRow row) -> row.passRate()).reversed())
            .map(this::toVo)
            .toList();
    }

    /** 每个模型取其最佳推理强度配置，按 pass_rate 降序，取前 n 个。 */
    public List<LeaderboardEntryVo> top(int n) {
        if (n <= 0) {
            n = 20;
        }
        LeaderboardPayload current = payload;
        if (current == null) {
            return List.of();
        }
        Map<String, LeaderboardRow> best = new LinkedHashMap<>();
        for (LeaderboardRow row : current.rows()) {
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
            .limit(n)
            .map(this::toVo)
            .toList();
    }

    public LeaderboardMetaVo meta() {
        LeaderboardMetaVo meta = new LeaderboardMetaVo();
        LeaderboardPayload current = payload;
        meta.setAvailable(current != null);
        meta.setSource(source);
        meta.setFetchedAt(fetchedAt == null ? null : fetchedAt.toString());
        meta.setGeneratedAt(generatedAt == null ? null : generatedAt.toString());
        if (current != null) {
            meta.setNTasks(current.nTasksInSet() == null ? 0 : current.nTasksInSet());
            meta.setRowCount(current.rows().size());
            meta.setModelCount((int) current.rows().stream()
                .map(LeaderboardRow::model)
                .filter(Objects::nonNull)
                .distinct()
                .count());
        }
        meta.setError(error);
        return meta;
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
        vo.setDurationSeconds(row.meanDurationSeconds() == null
            ? 0
            : Math.round(row.meanDurationSeconds()));
        vo.setNAttempted(row.nAttempted() == null ? 0 : row.nAttempted());
        vo.setNTasksPassedAny(row.nTasksPassedAny() == null ? 0 : row.nTasksPassedAny());
        return vo;
    }

    private static String humanize(String model) {
        if (model == null) {
            return "";
        }
        return model.replace('_', ' ');
    }
}
