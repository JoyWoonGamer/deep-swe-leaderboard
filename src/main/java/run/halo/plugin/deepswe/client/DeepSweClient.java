package run.halo.plugin.deepswe.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.plugin.deepswe.model.LeaderboardPayload;
import run.halo.plugin.deepswe.service.DeepSweSetting;

/**
 * 从 DeepSWE 抓取排行榜 JSON。
 *
 * 数据源：
 *  - 镜像：https://raw.githubusercontent.com/benchget/deepswe/main/data/leaderboard-live.json（每日 06:00 UTC 更新，最稳）
 *  - 实时：https://deepswe.datacurve.ai/artifacts/{version}/leaderboard-live.json（版本号可能变化，需回退探测）
 */
@Component
public class DeepSweClient {

    public static final String MIRROR_URL =
        "https://raw.githubusercontent.com/benchget/deepswe/main/data/leaderboard-live.json";

    public static final String LIVE_URL =
        "https://deepswe.datacurve.ai/artifacts/%s/leaderboard-live.json";

    private static final Logger log = LoggerFactory.getLogger(DeepSweClient.class);

    private static final String[] VERSION_FALLBACKS = {"v1.1", "v1", "v1.2", "v2", "v1.0"};

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebClient webClient;

    public DeepSweClient() {
        // WebClient 由宿主 WebFlux 环境提供；整体超时控制见 getJson()
        this.webClient = WebClient.create();
    }

    /** 抓取一次；source 参数决定优先顺序，返回带来源标签的结果。 */
    public Mono<FetchResult> fetch(DeepSweSetting cfg) {
        String src = cfg.source().toLowerCase();
        if ("live".equals(src)) {
            return liveOrEmpty(cfg.version()).map(payload -> new FetchResult(payload, "live"));
        }
        if ("mirror".equals(src)) {
            return fetchFromMirror().map(payload -> new FetchResult(payload, "mirror"));
        }
        // auto：实时优先（国内可直连、上游已更新），失败回退 GitHub 每日镜像（国内需代理）
        return liveOrEmpty(cfg.version())
            .map(payload -> new FetchResult(payload, "live"))
            .switchIfEmpty(fetchFromMirror().map(payload -> new FetchResult(payload, "mirror")));
    }

    public Mono<LeaderboardPayload> fetchFromMirror() {
        return getJson(MIRROR_URL);
    }

    public Mono<LeaderboardPayload> fetchFromLive(String version) {
        return getJson(String.format(LIVE_URL, version));
    }

    private Mono<LeaderboardPayload> liveOrEmpty(String preferred) {
        List<String> versions = new ArrayList<>();
        if (preferred != null && !preferred.isBlank()) {
            versions.add(preferred.trim());
        }
        for (String v : VERSION_FALLBACKS) {
            if (!versions.contains(v)) {
                versions.add(v);
            }
        }
        return Flux.fromIterable(versions)
            .concatMap(this::fetchFromLive)
            .next();
    }

    private Mono<LeaderboardPayload> getJson(String url) {
        return webClient.get()
            .uri(url)
            .retrieve()
            .onStatus(status -> status.isError(),
                resp -> Mono.error(new IllegalStateException("HTTP " + resp.statusCode() + " " + url)))
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(25))
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
                log.error("DeepSWE 抓取失败 url={}", url, err);
                return Mono.empty();
            });
    }

    /** 抓取结果：payload + 实际来源。 */
    public record FetchResult(LeaderboardPayload payload, String source) {
    }
}
