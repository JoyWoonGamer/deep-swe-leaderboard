package run.plugin.deepswe.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.theme.TemplateNameResolver;
import run.halo.app.theme.router.ModelConst;
import run.plugin.deepswe.LeaderboardConfigService;
import run.plugin.deepswe.model.LeaderboardMetaVo;
import run.plugin.deepswe.source.LeaderboardRegistry;
import run.plugin.deepswe.source.LeaderboardSource;

/**
 * 前台页面路由：
 * <ul>
 *   <li>{@code /benchmarks} —— 聚合首页，展示全部榜单卡片</li>
 *   <li>{@code /benchmarks/{board}} —— 榜单详情分页（复用通用榜单模板）</li>
 *   <li>{@code /deepswe} —— 兼容旧短链接（映射到 deepswe 详情页）</li>
 * </ul>
 *
 * <p>采用 Halo 官方插件前台页面标准做法：自定义 RouterFunction 映射站点根路径，
 * 通过 {@link TemplateNameResolver} 解析插件模板并用 {@code ServerResponse.render(...)} 渲染，
 * 复用当前主题的页头/页脚/外壳。</p>
 */
@Component
public class DeepSwePageRouter {

    private static final Logger log = LoggerFactory.getLogger(DeepSwePageRouter.class);

    private static final String INDEX_TEMPLATE = "benchmarks";
    private static final String BOARD_TEMPLATE = "board";
    private static final String LEGACY_TEMPLATE = "deepswe";
    private static final String TEMPLATE_ID = "plugin:plugin-deepswe-leaderboard:board";

    private final LeaderboardRegistry registry;
    private final LeaderboardConfigService configService;
    private final TemplateNameResolver templateNameResolver;

    public DeepSwePageRouter(LeaderboardRegistry registry,
        LeaderboardConfigService configService,
        TemplateNameResolver templateNameResolver) {
        this.registry = registry;
        this.configService = configService;
        this.templateNameResolver = templateNameResolver;
    }

    @Bean
    public RouterFunction<ServerResponse> deepSwePageRoute() {
        return RouterFunctions.route()
            .GET("/benchmarks", this::renderIndex)
            .GET("/benchmarks/{board}", this::renderBoard)
            .GET("/deepswe", this::renderLegacy)
            .build();
    }

    private Mono<ServerResponse> renderIndex(ServerRequest request) {
        configService.refresh();
        return templateNameResolver
            .resolveTemplateNameOrDefault(request.exchange(), INDEX_TEMPLATE)
            .flatMap(templateName -> ServerResponse.ok().render(templateName, buildIndexModel()))
            .onErrorResume(e -> {
                log.error("排行榜聚合首页渲染失败", e);
                return ServerResponse.ok().render(INDEX_TEMPLATE, fallbackModel());
            });
    }

    private Mono<ServerResponse> renderBoard(ServerRequest request) {
        String board = request.pathVariable("board");
        return registry.get(board)
            .map(src -> {
                src.refreshIfNeededAsync(configService.refreshMinutes());
                return templateNameResolver
                    .resolveTemplateNameOrDefault(request.exchange(), BOARD_TEMPLATE)
                    .flatMap(templateName -> ServerResponse.ok()
                        .render(templateName, buildBoardModel(src)))
                    .onErrorResume(e -> {
                        log.error("榜单 {} 详情页渲染失败", board, e);
                        return ServerResponse.ok().render(BOARD_TEMPLATE, fallbackModel());
                    });
            })
            .orElseGet(() -> ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> renderLegacy(ServerRequest request) {
        return registry.get("deepswe")
            .map(src -> {
                src.refreshIfNeededAsync(configService.refreshMinutes());
                return templateNameResolver
                    .resolveTemplateNameOrDefault(request.exchange(), LEGACY_TEMPLATE)
                    .flatMap(templateName -> ServerResponse.ok()
                        .render(templateName, buildBoardModel(src)))
                    .onErrorResume(e -> {
                        log.error("DeepSWE 旧页面渲染失败", e);
                        return ServerResponse.ok().render(LEGACY_TEMPLATE, fallbackModel());
                    });
            })
            .orElseGet(() -> ServerResponse.notFound().build());
    }

    /** 渲染异常时的最小可用 model：至少带上页面标题，避免模板取值报错。 */
    private Map<String, Object> fallbackModel() {
        Map<String, Object> model = new HashMap<>();
        model.put("title", configService.pageTitle());
        model.put("pageTitle", configService.pageTitle());
        model.put("pageSubtitle", configService.pageSubtitle());
        model.put("description", configService.pageSubtitle());
        model.put("boards", List.of());
        model.put(ModelConst.TEMPLATE_ID, TEMPLATE_ID);
        return model;
    }

    private Map<String, Object> buildIndexModel() {
        List<Map<String, Object>> boards = new ArrayList<>();
        for (LeaderboardSource src : registry.all()) {
            src.refreshIfNeededAsync(configService.refreshMinutes());
            LeaderboardMetaVo meta = src.meta();
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", src.id());
            card.put("name", src.displayName());
            card.put("description", src.description());
            card.put("available", meta.isAvailable());
            card.put("source", meta.getSource());
            card.put("generatedAt", meta.getGeneratedAt());
            card.put("modelCount", meta.getModelCount());
            card.put("nTasks", meta.getNTasks());
            card.put("consecutiveFailures", meta.getConsecutiveFailures());
            card.put("top", src.top(5));
            boards.add(card);
        }
        Map<String, Object> model = new HashMap<>();
        model.put("title", configService.pageTitle());
        model.put("pageTitle", configService.pageTitle());
        model.put("pageSubtitle", configService.pageSubtitle());
        model.put("description", configService.pageSubtitle());
        model.put("boards", boards);
        model.put(ModelConst.TEMPLATE_ID, TEMPLATE_ID);
        return model;
    }

    private Map<String, Object> buildBoardModel(LeaderboardSource src) {
        LeaderboardMetaVo meta = src.meta();
        Map<String, Object> data = new HashMap<>();
        data.put("id", src.id());
        data.put("name", src.displayName());
        data.put("available", meta.isAvailable());
        data.put("source", meta.getSource());
        data.put("fetchedAt", meta.getFetchedAt());
        data.put("generatedAt", meta.getGeneratedAt());
        data.put("nTasks", meta.getNTasks());
        data.put("defaultView", configService.defaultView());
        data.put("rows", src.top(configService.topN()));

        Map<String, Object> model = new HashMap<>();
        model.put("title", src.displayName() + " 排行榜 · " + configService.pageTitle());
        model.put("pageTitle", configService.pageTitle());
        model.put("description", src.description());
        model.put("boardData", data);
        model.put("boardDefaultView", configService.defaultView());
        model.put("boardBase", "/apis/api.deep-swe-leaderboard.halo.run/v1alpha1/boards/"
            + src.id());
        model.put(ModelConst.TEMPLATE_ID, TEMPLATE_ID);
        return model;
    }
}
