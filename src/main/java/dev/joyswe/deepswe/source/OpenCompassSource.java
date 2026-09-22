package dev.joyswe.deepswe.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import dev.joyswe.deepswe.cache.LeaderboardCacheStore;
import dev.joyswe.deepswe.model.LeaderboardEntryVo;
import dev.joyswe.deepswe.model.LeaderboardMetaVo;

/**
 * 司南 OpenCompass「能力榜单 · 官方评测集」数据源（上海人工智能实验室/ OpenCompass）。
 *
 * <p>数据来源：司南站点（{@code rank.opencompass.org.cn}）把榜单数据以静态 JSON 发布在
 * 阿里云 OSS（{@code opencompass.oss-cn-shanghai.aliyuncs.com/assets/llm/...}），
 * 无鉴权、无签名、可直接抓取。官方评测集综合 5 张表（Overall/Knowledge/Reason/Math/Code），
 * 统一按综合均分 {@code Average} 降序展示。</p>
 *
 * <p>该榜自带 {@code org}（厂商）与 {@code open_source}（开源/闭源）字段，是
 * 「国产权威全能力评测 + 中外模型同场」的代表数据源；展示行每行还会把四维度均分
 * 拼入 {@code summary}，供前端作为副信息行展示。</p>
 */
public class OpenCompassSource extends AbstractLeaderboardSource {

    /** 官方评测集静态数据（OSS，文件名固定；上游更新内容、不换文件名）。 */
    public static final String OSS_URL =
        "https://opencompass.oss-cn-shanghai.aliyuncs.com/assets/llm/data-llm-ability_official.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/M/d");

    private final WebClient webClient = WebClient.create();

    public OpenCompassSource(LeaderboardCacheStore cacheStore) {
        super(cacheStore);
    }

    @Override
    public String id() {
        return "compass";
    }

    @Override
    public String displayName() {
        return "司南 OpenCompass";
    }

    @Override
    public String description() {
        return "上海 AI Lab 权威全能力榜 · 官方评测集综合均分（知识 / 推理 / 数学 / 代码）";
    }

    @Override
    protected Mono<FetchResult> doFetch() {
        return fetchJson(OSS_URL)
            .map(node -> new FetchResult(node, "OpenCompass OSS"));
    }

    private Mono<JsonNode> fetchJson(String url) {
        return webClient.get()
            .uri(url)
            .retrieve()
            .onStatus(status -> status.isError(),
                resp -> Mono.error(new IllegalStateException("HTTP " + resp.statusCode() + " " + url)))
            .bodyToMono(String.class)
            .timeout(timeout())
            .map(body -> {
                JsonNode root;
                try {
                    root = MAPPER.readTree(body);
                } catch (Exception e) {
                    throw new IllegalStateException("JSON parse failed for " + url, e);
                }
                JsonNode table = root == null ? null : root.path("OverallTable");
                if (table == null || !table.isArray() || table.isEmpty()) {
                    throw new IllegalStateException("empty OverallTable for " + url);
                }
                return root;
            })
            .onErrorResume(err -> {
                logError("抓取失败 url=" + url, err);
                return Mono.empty();
            });
    }

    @Override
    protected LeaderboardMetaVo buildMeta() {
        LeaderboardMetaVo meta = new LeaderboardMetaVo();
        // 官方评测集含综合/知识/推理/数学/代码 5 张维度表
        meta.setNTasks(5);
        return meta;
    }

    @Override
    protected Instant parseGeneratedAt(Object payload) {
        if (!(payload instanceof JsonNode root)) {
            return null;
        }
        JsonNode first = root.path("OverallTable").path(0);
        String date = first.path("update_date").asText(null);
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim(), DATE_FORMAT)
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @Override
    protected String buildError() {
        return "司南官方评测集抓取失败（OSS 静态数据不可用）";
    }

    /** 官方评测集按综合均分（Average）降序输出全部模型。 */
    @Override
    protected List<LeaderboardEntryVo> toVoList() {
        JsonNode root = (JsonNode) currentPayload();
        if (root == null) {
            return List.of();
        }
        JsonNode table = root.path("OverallTable");
        List<LeaderboardEntryVo> rows = new ArrayList<>();
        for (JsonNode node : table) {
            String model = node.path("model").asText(null);
            if (model == null || model.isBlank()) {
                continue;
            }
            rows.add(toVo(node));
        }
        rows.sort(Comparator.comparingDouble(LeaderboardEntryVo::getPassRate).reversed());
        return rows;
    }

    private LeaderboardEntryVo toVo(JsonNode node) {
        LeaderboardEntryVo vo = new LeaderboardEntryVo();
        String model = node.path("model").asText("");
        vo.setModel(model);
        vo.setDisplayName(model);
        vo.setProvider(node.path("org").asText(""));
        String openSource = node.path("open_source").asText("");
        vo.setEffort("YES".equalsIgnoreCase(openSource) ? "开源权重" : "闭源 API");
        double average = node.path("Average").asDouble(0);
        vo.setPassRate(average / 100);
        vo.setPassRatePct((int) Math.round(average));
        vo.setCiHalfPct(0);
        vo.setCost(0);
        vo.setOutTok(0);
        vo.setSteps(0);
        vo.setDurationSeconds(0);
        // 副信息行：知识 / 推理 / 数学 / 代码 四维度均分
        StringBuilder sb = new StringBuilder();
        appendDim(sb, "知识", node, "Knowledge");
        appendDim(sb, "推理", node, "Reasoning");
        appendDim(sb, "数学", node, "Math");
        appendDim(sb, "代码", node, "Coding");
        if (!sb.isEmpty()) {
            vo.setSummary(sb.toString());
        }
        return vo;
    }

    private static void appendDim(StringBuilder sb, String label, JsonNode node, String field) {
        double v = node.path(field).asDouble(Double.NaN);
        if (Double.isNaN(v) || node.path(field).isMissingNode()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(" · ");
        }
        sb.append(label).append(' ').append(Math.round(v));
    }
}