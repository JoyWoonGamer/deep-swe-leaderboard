package dev.joyswe.deepswe.source.hf;

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
 * <p>统一接口：{@code GET {host}/api/datasets/{owner}/{dataset}/leaderboard}，
 * 返回 [{rank, modelId, value, verified, author{name,avatarUrl}, ...}]。</p>
 *
 * <p>{@code huggingface.co} 在无代理网络下不可达（被墙），故按顺序尝试国内镜像
 * {@code hf-mirror.com} 后再回退官方站点，任一路径可用即返回数据。</p>
 */
public class HuggingFaceLeaderboardClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String[] HOSTS = {
        "https://hf-mirror.com",
        "https://huggingface.co"
    };

    private final WebClient webClient = WebClient.create();

    /** 抓取指定数据集的排行榜。所有 host 均失败返回 empty。 */
    public Mono<List<HfEntry>> fetch(String datasetId) {
        return fetchFrom(0, datasetId);
    }

    private Mono<List<HfEntry>> fetchFrom(int idx, String datasetId) {
        if (idx >= HOSTS.length) {
            return Mono.empty();
        }
        String url = HOSTS[idx] + "/api/datasets/" + datasetId + "/leaderboard";
        return webClient.get()
            .uri(url)
            .retrieve()
            .onStatus(status -> status.isError(),
                resp -> Mono.error(new IllegalStateException("HTTP " + resp.statusCode() + " " + url)))
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(25))
            .flatMap(body -> {
                List<HfEntry> list = parse(body);
                return list.isEmpty() ? fetchFrom(idx + 1, datasetId) : Mono.just(list);
            })
            .onErrorResume(err -> fetchFrom(idx + 1, datasetId));
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
