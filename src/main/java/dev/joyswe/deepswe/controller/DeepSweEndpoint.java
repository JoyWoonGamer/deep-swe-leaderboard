package dev.joyswe.deepswe.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import dev.joyswe.deepswe.LeaderboardConfigService;
import dev.joyswe.deepswe.model.LeaderboardEntryVo;
import dev.joyswe.deepswe.model.LeaderboardMetaVo;
import dev.joyswe.deepswe.model.LeaderboardViewDto;
import dev.joyswe.deepswe.source.LeaderboardRegistry;
import dev.joyswe.deepswe.source.LeaderboardSource;

/**
 * 公开 REST 接口（匿名可访问），供前台 JS 组件或外部消费。
 *
 * <pre>
 * GET /apis/api.deep-swe-leaderboard.joyswe.dev/v1alpha1/boards               # 列出所有榜单
 * GET /apis/api.deep-swe-leaderboard.joyswe.dev/v1alpha1/boards/{board}       # 指定榜单 top（?size=N 可选）
 * GET /apis/api.deep-swe-leaderboard.joyswe.dev/v1alpha1/leaderboard           # 兼容旧接口（DeepSWE）
 * GET /apis/api.deep-swe-leaderboard.joyswe.dev/v1alpha1/leaderboard/top?size=20
 * </pre>
 *
 * <p><b>注意</b>：Halo 安全层对 CustomEndpoint 的匿名放行仅覆盖到路径变量段
 * （{@code /boards/*}），变量后跟额外段（{@code /boards/*/top}）会被拦截并重定向到登录页。
 * 因此所有 size 参数通过 query string 传递，不使用 /top 子路径。</p>
 */
@Component
public class DeepSweEndpoint implements CustomEndpoint {

    private final LeaderboardRegistry registry;
    private final LeaderboardConfigService configService;

    public DeepSweEndpoint(LeaderboardRegistry registry, LeaderboardConfigService configService) {
        this.registry = registry;
        this.configService = configService;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("/boards", this::boards)
            .GET("/boards/{board}", this::board)           // ?size=N optional
            .GET("/leaderboard", this::legacyLeaderboard)
            .GET("/leaderboard/top", this::legacyTop)
            .build();
    }

    /** 列出所有榜单（含各自 meta 与 top 预览）。 */
    private Mono<ServerResponse> boards(ServerRequest request) {
        configService.refresh();
        List<Map<String, Object>> list = new ArrayList<>();
        for (LeaderboardSource source : registry.all()) {
            source.refreshIfNeededAsync(configService.refreshMinutes());
            LeaderboardMetaVo meta = source.meta();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", source.id());
            item.put("name", source.displayName());
            item.put("description", source.description());
            item.put("available", meta.isAvailable());
            item.put("source", meta.getSource());
            item.put("generatedAt", meta.getGeneratedAt());
            item.put("fetchedAt", meta.getFetchedAt());
            item.put("nTasks", meta.getNTasks());
            item.put("modelCount", meta.getModelCount());
            item.put("consecutiveFailures", meta.getConsecutiveFailures());
            item.put("error", meta.getError());
            item.put("top", source.top(5));
            list.add(item);
        }
        return ServerResponse.ok().contentType(APPLICATION_JSON).bodyValue(list);
    }

    /** 指定榜单 top 数据，支持 ?size=N（Halo 安全层限制：不能用 /top 子路径）。 */
    private Mono<ServerResponse> board(ServerRequest request) {
        return resolveSource(request)
            .flatMap(src -> {
                src.refreshIfNeededAsync(configService.refreshMinutes());
                int size = resolveSize(request, configService.topN());
                return Mono.fromSupplier(() -> buildView(src, src.top(size)))
                    .flatMap(view -> ServerResponse.ok().contentType(APPLICATION_JSON).bodyValue(view));
            });
    }

    /** 兼容旧接口：/leaderboard 映射到 deepswe。 */
    private Mono<ServerResponse> legacyLeaderboard(ServerRequest request) {
        return registry.get("deepswe")
            .map(src -> {
                src.refreshIfNeededAsync(configService.refreshMinutes());
                return Mono.fromSupplier(() -> buildView(src, src.top(configService.topN())))
                    .flatMap(view -> ServerResponse.ok().contentType(APPLICATION_JSON).bodyValue(view));
            })
            .orElseGet(() -> ServerResponse.ok().contentType(APPLICATION_JSON)
                .bodyValue(Map.of("available", false, "error", "deepswe source not found")));
    }

    private Mono<ServerResponse> legacyTop(ServerRequest request) {
        return registry.get("deepswe")
            .map(src -> {
                src.refreshIfNeededAsync(configService.refreshMinutes());
                int size = resolveSize(request, configService.topN());
                return Mono.fromSupplier(() -> buildView(src, src.top(size)))
                    .flatMap(view -> ServerResponse.ok().contentType(APPLICATION_JSON).bodyValue(view));
            })
            .orElseGet(() -> ServerResponse.ok().contentType(APPLICATION_JSON)
                .bodyValue(Map.of("available", false, "error", "deepswe source not found")));
    }

    private Mono<LeaderboardSource> resolveSource(ServerRequest request) {
        String board = request.pathVariable("board");
        return registry.get(board)
            .map(Mono::just)
            .orElseGet(() -> Mono.error(new IllegalArgumentException("unknown board: " + board)));
    }

    private int resolveSize(ServerRequest request, int defaultSize) {
        Integer requested = request.queryParam("size")
            .flatMap(v -> {
                try {
                    return java.util.Optional.of(Integer.parseInt(v));
                } catch (NumberFormatException e) {
                    return java.util.Optional.empty();
                }
            })
            .orElse(null);
        if (requested == null || requested <= 0) {
            return defaultSize;
        }
        return requested;
    }

    private LeaderboardViewDto buildView(LeaderboardSource src, List<LeaderboardEntryVo> rows) {
        LeaderboardMetaVo meta = src.meta();
        LeaderboardViewDto dto = new LeaderboardViewDto();
        dto.setAvailable(meta.isAvailable());
        dto.setSource(meta.getSource());
        dto.setFetchedAt(meta.getFetchedAt());
        dto.setGeneratedAt(meta.getGeneratedAt());
        dto.setNTasks(meta.getNTasks());
        dto.setError(meta.getError());
        dto.setRows(rows);
        return dto;
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion("api.deep-swe-leaderboard.joyswe.dev", "v1alpha1");
    }
}
