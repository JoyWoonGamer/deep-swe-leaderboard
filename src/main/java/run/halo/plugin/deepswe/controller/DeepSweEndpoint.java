package run.halo.plugin.deepswe.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.plugin.deepswe.model.LeaderboardEntryVo;
import run.halo.plugin.deepswe.model.LeaderboardMetaVo;
import run.halo.plugin.deepswe.model.LeaderboardViewDto;
import run.halo.plugin.deepswe.service.DeepSweSetting;
import run.halo.plugin.deepswe.service.LeaderboardService;

/**
 * 公开 REST 接口，供前台 JS 组件或外部消费（与站点同域，无跨域问题）。
 *
 * <pre>
 * GET /apis/api.deep-swe-leaderboard.halo.run/v1alpha1/leaderboard
 * GET /apis/api.deep-swe-leaderboard.halo.run/v1alpha1/leaderboard/top?size=20
 * </pre>
 */
@Component
public class DeepSweEndpoint implements CustomEndpoint {

    private final LeaderboardService service;
    private final ReactiveSettingFetcher settingFetcher;

    public DeepSweEndpoint(LeaderboardService service, ReactiveSettingFetcher settingFetcher) {
        this.service = service;
        this.settingFetcher = settingFetcher;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("/leaderboard", this::leaderboard)
            .GET("/leaderboard/top", this::top)
            .build();
    }

    private Mono<ServerResponse> leaderboard(ServerRequest request) {
        service.refreshIfNeededAsync();
        return settingFetcher.fetch(DeepSweSetting.GROUP, DeepSweSetting.class)
            .defaultIfEmpty(new DeepSweSetting())
            .flatMap(cfg -> Mono.fromSupplier(() -> buildView(service.top(cfg.topN()))))
            .flatMap(view -> ServerResponse.ok()
                .contentType(APPLICATION_JSON)
                .bodyValue(view));
    }

    private Mono<ServerResponse> top(ServerRequest request) {
        service.refreshIfNeededAsync();
        return settingFetcher.fetch(DeepSweSetting.GROUP, DeepSweSetting.class)
            .defaultIfEmpty(new DeepSweSetting())
            .map(cfg -> resolveTopSize(request, cfg.topN()))
            .flatMap(size -> Mono.fromSupplier(() -> buildView(service.top(size))))
            .flatMap(view -> ServerResponse.ok()
                .contentType(APPLICATION_JSON)
                .bodyValue(view));
    }

    private int resolveTopSize(ServerRequest request, int defaultSize) {
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

    private LeaderboardViewDto buildView(List<LeaderboardEntryVo> rows) {
        LeaderboardMetaVo meta = service.meta();
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
        return new GroupVersion("api.deep-swe-leaderboard.halo.run", "v1alpha1");
    }
}
