package run.halo.plugin.deepswe.controller;

import java.util.HashMap;
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
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.theme.TemplateNameResolver;
import run.halo.app.theme.router.ModelConst;
import run.halo.plugin.deepswe.model.LeaderboardEntryVo;
import run.halo.plugin.deepswe.model.LeaderboardMetaVo;
import run.halo.plugin.deepswe.service.DeepSweSetting;
import run.halo.plugin.deepswe.service.LeaderboardService;

/**
 * DeepSWE 排行榜短链接页面（{@code /deepswe}）。
 *
 * <p>采用 Halo 官方插件前台页面标准做法（与「瞬间 / 项目集」插件一致）：
 * 自定义 RouterFunction 映射站点根短路径，通过 {@link TemplateNameResolver} 解析插件模板
 * {@code templates/deepswe.html} 并用 {@code ServerResponse.render(...)} 渲染。
 * 模板通过 {@code layout :: html(...)} 复用当前主题的页头 / 页脚 / 页面外壳，
 * 主题未提供布局时使用 Halo 内置 fallback 布局。</p>
 *
 * <p>排行榜数据由服务端注入 {{@code deepsweData}}/{{@code deepsweDefaultView}}，前端拿到立即渲染，
 * 首屏无需等待网络（解决加载慢）。</p>
 */
@Component
public class DeepSwePageRouter {

    private static final Logger log = LoggerFactory.getLogger(DeepSwePageRouter.class);

    private static final String TEMPLATE_NAME = "deepswe";
    private static final String TEMPLATE_ID = "plugin:plugin-deepswe-leaderboard:deepswe";

    private final LeaderboardService service;
    private final ReactiveSettingFetcher settingFetcher;
    private final TemplateNameResolver templateNameResolver;

    public DeepSwePageRouter(LeaderboardService service,
        ReactiveSettingFetcher settingFetcher,
        TemplateNameResolver templateNameResolver) {
        this.service = service;
        this.settingFetcher = settingFetcher;
        this.templateNameResolver = templateNameResolver;
    }

    @Bean
    public RouterFunction<ServerResponse> deepSwePageRoute() {
        return RouterFunctions.route()
            .GET("/deepswe", this::renderPage)
            .build();
    }

    private Mono<ServerResponse> renderPage(ServerRequest request) {
        service.refreshIfNeededAsync();
        return settingFetcher.fetch(DeepSweSetting.GROUP, DeepSweSetting.class)
            .defaultIfEmpty(new DeepSweSetting())
            .flatMap(cfg -> templateNameResolver
                .resolveTemplateNameOrDefault(request.exchange(), TEMPLATE_NAME)
                .flatMap(templateName -> ServerResponse.ok().render(templateName, buildModel(cfg))))
            .onErrorResume(e -> {
                log.error("DeepSWE /deepswe 渲染失败", e);
                return ServerResponse.ok()
                    .render(TEMPLATE_NAME, Map.of());
            });
    }

    private Map<String, Object> buildModel(DeepSweSetting cfg) {
        LeaderboardMetaVo meta = service.meta();
        Map<String, Object> data = new HashMap<>();
        data.put("available", meta.isAvailable());
        data.put("source", meta.getSource());
        data.put("fetchedAt", meta.getFetchedAt());
        data.put("generatedAt", meta.getGeneratedAt());
        data.put("nTasks", meta.getNTasks());
        data.put("defaultView", cfg.defaultView());
        data.put("rows", service.top(cfg.topN()));

        Map<String, Object> model = new HashMap<>();
        model.put("title", "DeepSWE 排行榜");
        model.put("description", "DeepSWE 编程智能体基准测试 · 排行榜实时展示");
        model.put("deepsweData", data);
        model.put("deepsweDefaultView", cfg.defaultView());
        model.put(ModelConst.TEMPLATE_ID, TEMPLATE_ID);
        return model;
    }
}