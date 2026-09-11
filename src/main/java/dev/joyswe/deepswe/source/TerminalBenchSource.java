package dev.joyswe.deepswe.source;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.joyswe.deepswe.cache.LeaderboardCacheStore;
import dev.joyswe.deepswe.model.LeaderboardEntryVo;
import dev.joyswe.deepswe.model.LeaderboardMetaVo;

/**
 * Terminal-Bench 排行榜数据源（tbench.ai，Terminal-Bench 4.0 官方榜）。
 *
 * <p>数据来源：官方站点 {@code https://www.tbench.ai/} 页面内嵌的 React Query 脱水 JSON
 * （Next.js RSC flight payload，无公开 REST API）。抓取页面 → 定位 {@code leaderboard}
 * 对象 → 解析 {@code rows[]}（含 rank / metadata{model_display, agent_display,
 * reasoning_effort, model_org} / metrics{accuracy, accuracy_ci95_half_width,
 * total_cost_usd, output_tokens, total_tokens, n_trials, successes,
 * avg_trial_duration_sec}）。</p>
 *
 * <p>展示行 = 每个（模型 × Agent）组合取 accuracy 最高的配置，按 accuracy 降序。
 * 与 DeepSWE 的「每模型最佳配置」口径一致，避免同一模型换 Agent / 换推理档位刷屏。</p>
 */
public class TerminalBenchSource extends AbstractLeaderboardSource {

    private static final String LIVE_URL = "https://www.tbench.ai/";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebClient webClient = WebClient.create();

    /** 解析出的原始行（按 rank 升序），仅当页面上有数据时非空。 */
    private volatile List<TbRow> rows = List.of();

    /** leaderboard 对象的 updated_at（页面抓取时刻的榜单生成时间）。 */
    private volatile Instant leaderboardUpdatedAt;

    public TerminalBenchSource(LeaderboardCacheStore cacheStore) {
        super(cacheStore);
    }

    @Override
    public String id() {
        return "tbench";
    }

    @Override
    public String displayName() {
        return "Terminal-Bench";
    }

    @Override
    public String description() {
        return "真实 Shell 终端任务 · 模型 + Agent 组合 Accuracy（CLI 智能体基准）";
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
                List<TbRow> list = parseHtmlStatic(html);
                this.leaderboardUpdatedAt = parseUpdatedAtStatic(html);
                this.rows = list;
                return new FetchResult(list, "tbench.ai");
            })
            .onErrorResume(err -> {
                logError("抓取失败 url=" + LIVE_URL, err);
                return Mono.empty();
            });
    }

    @Override
    protected LeaderboardMetaVo buildMeta() {
        return new LeaderboardMetaVo();
    }

    @Override
    protected Instant parseGeneratedAt(Object payload) {
        return leaderboardUpdatedAt;
    }

    @Override
    protected String buildError() {
        return "tbench.ai 页面抓取失败或 leaderboard 数据缺失（页面结构变更？）";
    }

    /** 展示行：每个（模型 × Agent）取最高 accuracy，按 accuracy 降序。 */
    @Override
    protected List<LeaderboardEntryVo> toVoList() {
        Map<String, TbRow> best = new LinkedHashMap<>();
        for (TbRow row : rows) {
            String key = row.model + "\u0000" + row.agent;
            TbRow cur = best.get(key);
            if (cur == null || row.accuracy > cur.accuracy) {
                best.put(key, row);
            }
        }
        return best.values().stream()
            .sorted(Comparator.comparingDouble((TbRow row) -> row.accuracy).reversed())
            .map(this::toVo)
            .toList();
    }

    private LeaderboardEntryVo toVo(TbRow row) {
        LeaderboardEntryVo vo = new LeaderboardEntryVo();
        vo.setModel(row.model);
        vo.setDisplayName(row.model);
        vo.setEffort(row.agent + " · " + row.reasoningEffort);
        vo.setProvider(row.modelOrg);
        double acc = row.accuracy;
        vo.setPassRate(acc / 100.0);
        vo.setPassRatePct((int) Math.round(acc));
        vo.setCiHalfPct(Math.round(row.ciHalf * 10) / 10.0);
        vo.setCost(Math.round(row.costUsd * 100) / 100.0);
        vo.setOutTok(row.outputTokens);
        vo.setSteps(0); // Terminal-Bench 无 agent 步数指标
        vo.setDurationSeconds(Math.round(row.avgTrialDurationSec));
        vo.setNAttempted((int) row.nTrials);
        vo.setNTasksPassedAny((int) row.successes);
        return vo;
    }

    // ------------------------------------------------------------------
    // HTML → rows 解析（静态，便于单元测试）
    // ------------------------------------------------------------------

    /** 从首页 HTML 解析榜单行（按 rank 升序）。解析失败抛 IllegalStateException。 */
    public static List<TbRow> parseHtmlStatic(String html) {
        String flight = concatFlightPayload(html);
        String rowsJson = extractRowsArray(flight);
        JsonNode rowsNode;
        try {
            rowsNode = MAPPER.readTree(rowsJson);
        } catch (Exception e) {
            throw new IllegalStateException("rows JSON parse failed", e);
        }
        List<TbRow> out = new ArrayList<>();
        if (!rowsNode.isArray()) {
            return out;
        }
        for (JsonNode r : rowsNode) {
            JsonNode md = r.path("metadata");
            JsonNode metrics = r.path("metrics");
            String model = md.path("model_display").path("label").asText("");
            String agent = md.path("agent_display").path("label").asText("");
            if (model.isBlank()) {
                continue;
            }
            TbRow row = new TbRow(
                r.path("rank").asInt(0),
                model,
                agent,
                md.path("reasoning_effort").asText(""),
                md.path("model_org").path("label").asText(""),
                metrics.path("accuracy").asDouble(0),
                metrics.path("accuracy_ci95_half_width").asDouble(0),
                metrics.path("total_cost_usd").asDouble(0),
                metrics.path("output_tokens").asLong(0),
                metrics.path("total_tokens").asLong(0),
                metrics.path("n_trials").asLong(0),
                metrics.path("successes").asLong(0),
                metrics.path("avg_trial_duration_sec").asDouble(0));
            out.add(row);
        }
        return out;
    }

    /** 从首页 HTML 解析 leaderboard 的 updated_at（无则 null）。 */
    public static Instant parseUpdatedAtStatic(String html) {
        try {
            String flight = concatFlightPayload(html);
            String json = extractLeaderboardObject(flight);
            String updatedAt = MAPPER.readTree(json).path("updated_at").asText("");
            return updatedAt.isBlank() ? null : Instant.parse(updatedAt);
        } catch (Exception e) {
            return null;
        }
    }

    /** 拼接所有 flight 片段并反转义，得到可检索的 RSC 文本。
     *  <p>线性手工扫描（不用正则），避免 Java regex 对超长 flight 内容灾难性回溯。</p> */
    private static String concatFlightPayload(String html) {
        StringBuilder sb = new StringBuilder(html.length());
        String marker = "self.__next_f.push([1,\"";
        int from = 0;
        while (true) {
            int start = html.indexOf(marker, from);
            if (start < 0) {
                break;
            }
            int i = start + marker.length();
            StringBuilder raw = new StringBuilder();
            boolean escaped = false;
            while (i < html.length()) {
                char c = html.charAt(i);
                if (escaped) {
                    // 保留转义对（\" \\ 等），交给 unescapeJson 统一反转义
                    raw.append('\\').append(c);
                    escaped = false;
                    i++;
                } else if (c == '\\') {
                    escaped = true;
                    i++;
                } else if (c == '"') {
                    break; // flight 字符串字面量闭合引号
                } else {
                    raw.append(c);
                    i++;
                }
            }
            sb.append(unescapeJson(raw.toString()));
            from = i + 1;
        }
        if (sb.isEmpty()) {
            throw new IllegalStateException("flight payload not found in tbench.ai page");
        }
        return sb.toString();
    }

    /** 在 RSC 文本中定位 "leaderboard":{ 并以括号配对提取完整 JSON 对象（含 updated_at 等元信息）。 */
    private static String extractLeaderboardObject(String flight) {
        int idx = flight.indexOf("\"leaderboard\":{");
        if (idx < 0) {
            throw new IllegalStateException("leaderboard payload not found in tbench.ai page");
        }
        int start = flight.indexOf('{', idx);
        return extractBalanced(flight, start);
    }

    /** 在 RSC 文本中定位 "rows":[ 并以括号配对提取完整 JSON 数组。
     *  注意 rows 与 leaderboard 是兄弟字段（非嵌套）。 */
    private static String extractRowsArray(String flight) {
        int idx = flight.indexOf("\"rows\":[");
        if (idx < 0) {
            throw new IllegalStateException("\"rows\" array not found in tbench.ai flight payload");
        }
        int start = flight.indexOf('[', idx);
        return extractBalanced(flight, start);
    }

    /** 从 start 处的 '{' 或 '[' 开始，返回配对的完整 JSON 对象/数组（跳过字符串与转义）。
     *  通过首个开括号字符决定结束符号（'{' → '}'，'[' → ']'）。 */
    private static String extractBalanced(String s, int start) {
        char open = s.charAt(start);
        char close = (open == '{') ? '}' : ']';
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
            } else if (c == '"') {
                inStr = true;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return s.substring(start, i + 1);
                }
            }
        }
        throw new IllegalStateException("unbalanced leaderboard json");
    }

    /** 反转义 JSON 字符串字面量（\" \\ \/ \n \r \t \u005cXXXX 形式）。 */
    private static String unescapeJson(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                out.append(c);
                continue;
            }
            char n = s.charAt(i + 1);
            switch (n) {
                case '"' -> {
                    out.append('"');
                    i++;
                }
                case '\\' -> {
                    out.append('\\');
                    i++;
                }
                case '/' -> {
                    out.append('/');
                    i++;
                }
                case 'n' -> {
                    out.append('\n');
                    i++;
                }
                case 'r' -> {
                    out.append('\r');
                    i++;
                }
                case 't' -> {
                    out.append('\t');
                    i++;
                }
                case 'u' -> {
                    if (i + 5 < s.length()) {
                        try {
                            out.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                        } catch (NumberFormatException e) {
                            out.append('\\');
                        }
                        i += 5;
                    } else {
                        out.append('\\');
                    }
                }
                default -> out.append('\\');
            }
        }
        return out.toString();
    }

    /** 原始榜单行（按 rank 升序）。 */
    public record TbRow(int rank, String model, String agent, String reasoningEffort, String modelOrg,
                 double accuracy, double ciHalf, double costUsd, long outputTokens,
                 long totalTokens, long nTrials, long successes, double avgTrialDurationSec) {
    }
}