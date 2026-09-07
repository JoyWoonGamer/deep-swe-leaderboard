package run.halo.plugin.deepswe.source.hf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * HuggingFace Dataset Leaderboard API 客户端。
 *
 * <p>统一接口：{@code GET https://huggingface.co/api/datasets/{owner}/{dataset}/leaderboard}，
 * 返回 [{rank, modelId, value, verified, author{name,avatarUrl}, ...}]。
 * 国内可直连，是解决"数据更新不到"的稳定数据源。</p>
 */
public class HuggingFaceLeaderboardClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebClient webClient = WebClient.create();

    /** 抓取指定数据集的排行榜。失败返回 empty。 */
    public Mono<List<HfEntry>> fetch(String datasetId) {
        String url = "https://huggingface.co/api/datasets/" + datasetId + "/leaderboard";
        return webClient.get()
            .uri(url)
            .retrieve()
            .onStatus(status -> status.isError(),
                resp -> Mono.error(new IllegalStateException("HTTP " + resp.statusCode() + " " + url)))
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(25))
            .map(body -> parse(body))
            .onErrorResume(err -> Mono.empty());
    }

    private List<HfEntry> parse(String body) {
        List<HfEntry> out = new ArrayList<>();
        try {
            JsonNode arr = MAPPER.readTree(body);
            if (!arr.isArray()) {
                return out;
            }
            for (JsonNode n : arr) {
                HfEntry e = new HfEntry();
                e.rank = n.path("rank").asInt(0);
                e.modelId = n.path("modelId").asText("");
                e.value = n.path("value").asDouble(0);
                e.verified = n.path("verified").asBoolean(false);
                JsonNode author = n.path("author");
                if (author != null && !author.isNull()) {
                    e.authorName = author.path("name").asText("");
                    e.avatarUrl = author.path("avatarUrl").asText("");
                }
                if (!e.modelId.isBlank()) {
                    out.add(e);
                }
            }
        } catch (Exception ignored) {
            // 解析失败返回空列表
        }
        return out;
    }

    /** 归一化后的 HF 榜单条目。 */
    public static class HfEntry {
        public int rank;
        public String modelId;
        public double value;
        public boolean verified;
        public String authorName;
        public String avatarUrl;
    }
}
