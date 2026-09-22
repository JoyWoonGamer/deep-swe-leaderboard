package dev.joyswe.deepswe.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
import dev.joyswe.deepswe.LeaderboardConfigService;
import dev.joyswe.deepswe.model.LeaderboardMetaVo;
import dev.joyswe.deepswe.source.LeaderboardRegistry;
import dev.joyswe.deepswe.source.LeaderboardSource;

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
    private static final String HEALTH_TEMPLATE = "health";
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
            .GET("/benchmarks/health", this::renderHealth)
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

    /** 数据源健康状态面板：展示每个榜单的可用性/来源/抓取时间/连续失败次数/错误等。 */
    private Mono<ServerResponse> renderHealth(ServerRequest request) {
        configService.refresh();
        return templateNameResolver
            .resolveTemplateNameOrDefault(request.exchange(), HEALTH_TEMPLATE)
            .flatMap(templateName -> ServerResponse.ok().render(templateName, buildHealthModel()))
            .onErrorResume(e -> {
                log.error("健康面板渲染失败", e);
                return ServerResponse.ok().render(HEALTH_TEMPLATE, fallbackModel());
            });
    }

    /** 健康面板 model：全部数据源的健康状态列表 + 页面标题。 */
    private Map<String, Object> buildHealthModel() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (LeaderboardSource src : registry.all()) {
            src.refreshIfNeededAsync(configService.refreshMinutes());
            LeaderboardMetaVo meta = src.meta();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", src.id());
            item.put("name", src.displayName());
            item.put("description", src.description());
            item.put("available", meta.isAvailable());
            item.put("source", meta.getSource());
            item.put("fetchedAt", meta.getFetchedAt());
            item.put("generatedAt", meta.getGeneratedAt());
            item.put("nTasks", meta.getNTasks());
            item.put("modelCount", meta.getModelCount());
            item.put("rowCount", meta.getRowCount());
            item.put("consecutiveFailures", meta.getConsecutiveFailures());
            item.put("error", meta.getError());
            item.put("updatedLabel", buildUpdatedLabel(meta));
            // 健康状态：可用=ok；不可用且连续失败>0=err；不可用但未失败（如等待首拉）=warn
            boolean available = meta.isAvailable();
            int failures = meta.getConsecutiveFailures();
            item.put("status", available ? "ok" : (failures > 0 ? "err" : "warn"));
            item.put("statusText", available ? "正常" : (failures > 0 ? "异常" : "待就绪"));
            items.add(item);
        }
        Map<String, Object> model = new HashMap<>();
        model.put("title", "数据源健康状态 · " + configService.pageTitle());
        model.put("pageTitle", "数据源健康状态");
        model.put("pageSubtitle", "各数据源抓取状态与健康指标一览（用于排查「数据更新不到」问题）");
        model.put("description", configService.pageSubtitle());
        model.put("healthItems", items);
        model.put(ModelConst.TEMPLATE_ID, TEMPLATE_ID);
        return model;
    }

    private Mono<ServerResponse> renderBoard(ServerRequest request) {
        configService.refresh();
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
        configService.refresh();
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
        model.put("healthItems", List.of());
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
            card.put("fetchedAt", meta.getFetchedAt());
            card.put("modelCount", meta.getModelCount());
            card.put("nTasks", meta.getNTasks());
            card.put("consecutiveFailures", meta.getConsecutiveFailures());
            card.put("updatedLabel", buildUpdatedLabel(meta));
            card.put("top", src.top(10)); // 首页卡片至少列出前十
            boards.add(card);
        }
        // 数据最新的榜单排最前：generatedAt（上游生成时间）优先，
        // 无则用 fetchedAt（本地最近成功抓取）兜底，均无（未就绪）排最后。
        boards.sort(Comparator
            .comparing((Map<String, Object> card) -> freshInstant(card),
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(card -> (String) card.get("id")));
        Map<String, Object> model = new HashMap<>();
        model.put("title", configService.pageTitle());
        model.put("pageTitle", configService.pageTitle());
        model.put("pageSubtitle", configService.pageSubtitle());
        model.put("description", configService.pageSubtitle());
        model.put("boards", boards);
        model.put(ModelConst.TEMPLATE_ID, TEMPLATE_ID);
        return model;
    }

    /** 榜单新鲜度时间：generatedAt（上游生成时间）优先，缺失（HF 榜等）时用 fetchedAt 兜底。 */
    private static Instant freshInstant(Map<String, Object> card) {
        String ts = (String) card.get("generatedAt");
        if (ts == null || ts.isBlank()) {
            ts = (String) card.get("fetchedAt");
        }
        if (ts == null || ts.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(ts);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 前台卡片「数据更新于 …」文案：优先显示上游生成时间，缺省显示抓取时间。 */
    private static String buildUpdatedLabel(LeaderboardMetaVo meta) {
        String generated = meta.getGeneratedAt();
        String fetched = meta.getFetchedAt();
        String ts = (generated != null && !generated.isBlank()) ? generated : fetched;
        if (ts == null || ts.isBlank()) {
            return "暂无更新记录";
        }
        try {
            Instant instant = Instant.parse(ts);
            java.time.ZoneId zone = java.time.ZoneId.systemDefault();
            return java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
                .withZone(zone)
                .format(instant);
        } catch (RuntimeException e) {
            return ts;
        }
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
        model.put("boardBase", "/apis/api.deep-swe-leaderboard.joyswe.dev/v1alpha1/boards/"
            + src.id());
        model.put(ModelConst.TEMPLATE_ID, TEMPLATE_ID);
        return model;
    }
}
