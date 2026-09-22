package dev.joyswe.deepswe.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import dev.joyswe.deepswe.cache.LeaderboardCacheStore;
import dev.joyswe.deepswe.model.LeaderboardEntryVo;
import dev.joyswe.deepswe.model.LeaderboardMetaVo;

/**
 * Artificial Analysis「综合智能指数」排行榜数据源（artificialanalysis.ai）。
 *
 * <p>数据来源：官方站点 {@code https://artificialanalysis.ai/leaderboards/models} 页面内嵌的
 * Next.js RSC flight payload（无公开 REST API）。抓取页面 → 拼接 RSC 片段 → 定位
 * {@code "models":[...]} 完整数组 → 解析每行：{@code slug / name / deprecated /
 * intelligenceIndex / isOpenWeights / modelCreatorName / contextWindowTokens /
 * price1mInputTokens / price1mOutputTokens / medianOutputTokensPerSecond /
 * medianTimeToFirstTokenSeconds ...}</p>
 *
 * <p>展示口径：只展示非 deprecated（活跃）模型；同一基础模型换推理档位（如
 * Claude Opus 5.5 的 Max/Xhigh/High/Medium/Low Effort）会生成多行，取
 * intelligenceIndex 最高的配置行，effort 列记录该档位名——与 DeepSWE /
 * Terminal-Bench 的「每模型最佳配置」口径一致，避免同模型换档刷屏。</p>
 */
public class ArtificialAnalysisSource extends AbstractLeaderboardSource {

    /** 官方综合智能指数排行榜页面（RSC 内嵌数据）。 */
    public static final String LIVE_URL = "https://artificialanalysis.ai/leaderboards/models";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** AA 页面约 2.5MB（RSC payload），WebClient 默认 256KB 缓冲上限会截断，需调大。 */
    private final WebClient webClient = WebClient.builder()
        .codecs(config -> config.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
        .build();

    /** 解析出的活跃模型行（按 intelligenceIndex 降序）。 */
    private volatile List<AaRow> rows = List.of();

    public ArtificialAnalysisSource(LeaderboardCacheStore cacheStore) {
        super(cacheStore);
    }

    @Override
    public String id() {
        return "artanalysis";
    }

    @Override
    public String displayName() {
        return "Artificial Analysis";
    }

    @Override
    public String description() {
        return "国际综合智能指数榜 · 覆盖全球主流闭源 + 开源模型（能力 / 价格 / 速度）";
    }

    @Override
    protected Mono<FetchResult> doFetch() {
        return webClient.get()
            .uri(LIVE_URL)
            .retrieve()
            .onStatus(status -> status.isError(),
                resp -> Mono.error(new IllegalStateException("HTTP " + resp.statusCode() + " " + LIVE_URL)))
            .bodyToMono(String.class)
            .timeout(timeout())
            .map(html -> {
                List<AaRow> list = parseHtmlStatic(html);
                this.rows = list;
                return new FetchResult(list, "artificialanalysis.ai");
            })
            .onErrorResume(err -> {
                logError("抓取失败 url=" + LIVE_URL, err);
                return Mono.empty();
            });
    }

    @Override
    protected LeaderboardMetaVo buildMeta() {
        LeaderboardMetaVo meta = new LeaderboardMetaVo();
        // 综合智能指数 + 常用维度（能力/价格/速度/上下文），meta 不展示维度明细
        meta.setNTasks(4);
        return meta;
    }

    @Override
    protected String buildError() {
        return "artificialanalysis.ai 页面抓取失败或 models 数据缺失（页面结构变更？）";
    }

    /** 展示行：非 deprecated 且每基础模型取 intelligenceIndex 最高的配置。 */
    @Override
    protected List<LeaderboardEntryVo> toVoList() {
        Map<String, AaRow> best = new LinkedHashMap<>();
        for (AaRow row : rows) {
            if (row.deprecated) {
                continue;
            }
            AaRow cur = best.get(row.baseName);
            if (cur == null || row.intelligenceIndex > cur.intelligenceIndex) {
                best.put(row.baseName, row);
            }
        }
        return best.values().stream()
            .sorted((a, b) -> Double.compare(b.intelligenceIndex, a.intelligenceIndex))
            .map(this::toVo)
            .toList();
    }

    private LeaderboardEntryVo toVo(AaRow row) {
        LeaderboardEntryVo vo = new LeaderboardEntryVo();
        vo.setModel(row.baseName);
        vo.setDisplayName(row.baseName);
        vo.setEffort(row.effort);
        vo.setProvider(row.modelCreator);
        double idx = row.intelligenceIndex;
        vo.setPassRate(idx / 100.0);
        vo.setPassRatePct((int) Math.round(idx));
        vo.setCiHalfPct(0);
        vo.setCost(Math.round(row.price1mInput * 100) / 100.0);
        vo.setOutTok((long) Math.round(row.outputTokensPerSec));
        vo.setSteps(0);
        vo.setDurationSeconds(Math.round(row.ttftSeconds));
        // 副信息行：开源/闭源 · 上下文 · 输入/输出价
        StringBuilder sb = new StringBuilder();
        sb.append(row.isOpenWeights ? "开源权重" : "闭源 API");
        if (row.contextWindowTokens > 0) {
            sb.append(" · 上下文 ").append(formatTokens(row.contextWindowTokens));
        }
        if (row.price1mOutput > 0) {
            sb.append(" · 输出 $").append(Math.round(row.price1mOutput * 100) / 100.0).append("/M");
        }
        vo.setSummary(sb.toString());
        return vo;
    }

    private static String formatTokens(long tokens) {
        if (tokens >= 1_000_000) {
            long m = Math.round(tokens / 100_000.0) / 10;
            return m + "M";
        }
        if (tokens >= 1_000) {
            return (tokens / 1000) + "K";
        }
        return String.valueOf(tokens);
    }

    // ------------------------------------------------------------------
    // HTML → rows 解析（静态，便于单元测试）
    // ------------------------------------------------------------------

    /** 从页面 HTML 解析模型行（按 intelligenceIndex 降序）。解析失败抛 IllegalStateException。 */
    public static List<AaRow> parseHtmlStatic(String html) {
        String flight = RscFlightParser.concatFlightPayload(html);
        String modelsJson = RscFlightParser.extractArrayContaining(flight, "models", "intelligenceIndex");
        JsonNode modelsNode;
        try {
            modelsNode = MAPPER.readTree(modelsJson);
        } catch (Exception e) {
            throw new IllegalStateException("models JSON parse failed", e);
        }
        List<AaRow> out = new ArrayList<>();
        if (!modelsNode.isArray()) {
            return out;
        }
        for (JsonNode m : modelsNode) {
            String name = m.path("name").asText("");
            if (name.isBlank()) {
                continue;
            }
            // 基础模型名 = 去掉括号配置后缀（如 "GPT-6 Sol (max)" → "GPT-6 Sol"）
            String base = baseName(name);
            String effort = effortName(name, base);
            AaRow row = new AaRow(
                m.path("slug").asText(""),
                name,
                base,
                effort,
                m.path("deprecated").asBoolean(false),
                m.path("isOpenWeights").asBoolean(false),
                m.path("modelCreatorName").asText(""),
                m.path("intelligenceIndex").asDouble(0),
                m.path("contextWindowTokens").asLong(0),
                m.path("price1mInputTokens").asDouble(0),
                m.path("price1mOutputTokens").asDouble(0),
                m.path("medianOutputTokensPerSecond").asDouble(0),
                m.path("medianTimeToFirstTokenSeconds").asDouble(0));
            out.add(row);
        }
        out.sort((a, b) -> Double.compare(b.intelligenceIndex, a.intelligenceIndex));
        return out;
    }

    /** 基础模型名：去掉尾部括号配置（"GPT-6 Sol (max)" → "GPT-6 Sol"）。
     *  若括号不在尾部或无法匹配则不裁剪。 */
    private static String baseName(String name) {
        String trimmed = name.strip();
        int open = trimmed.lastIndexOf(" (");
        if (open > 0 && trimmed.endsWith(")")) {
            return trimmed.substring(0, open).strip();
        }
        return trimmed;
    }

    /** 配置档位名：括号外为模型名时返回括号内容（"GPT-6 Sol (max)" → "max"）。 */
    private static String effortName(String name, String base) {
        String trimmed = name.strip();
        int open = trimmed.lastIndexOf(" (");
        if (open > 0 && trimmed.endsWith(")") && trimmed.substring(0, open).strip().equals(base)) {
            return trimmed.substring(open + 2, trimmed.length() - 1).strip();
        }
        return "";
    }

    /** 模型行（intelligenceIndex 为官方综合智能指数，0-100 量级）。 */
    public record AaRow(String slug, String name, String baseName, String effort, boolean deprecated,
                 boolean isOpenWeights, String modelCreator, double intelligenceIndex,
                 long contextWindowTokens, double price1mInput, double price1mOutput,
                 double outputTokensPerSec, double ttftSeconds) {
    }
}