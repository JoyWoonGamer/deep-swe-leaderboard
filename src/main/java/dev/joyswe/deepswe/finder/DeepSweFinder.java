package dev.joyswe.deepswe.finder;

import java.util.List;
import reactor.core.publisher.Mono;
import run.halo.app.theme.finders.Finder;
import dev.joyswe.deepswe.LeaderboardConfigService;
import dev.joyswe.deepswe.model.LeaderboardEntryVo;
import dev.joyswe.deepswe.model.LeaderboardMetaVo;
import dev.joyswe.deepswe.source.LeaderboardRegistry;
import dev.joyswe.deepswe.source.LeaderboardSource;

/**
 * 向主题模板暴露的多榜 Finder。
 * <pre>
 *   ${aiLeaderboards.sources()}                所有榜单 source（含 id/name/meta/top 预览）
 *   ${aiLeaderboards.meta("deepswe")}          指定榜单元信息
 *   ${aiLeaderboards.top("deepswe", 20)}       指定榜单 top N
 *   ${aiLeaderboards.all("deepswe")}           指定榜单全部行
 *   ${aiLeaderboards.pageTitle()}              后台配置的页面标题（默认「AI大模型排行榜」）
 *   ${aiLeaderboards.pageSubtitle()}           后台配置的页面副标题
 * </pre>
 */
@Finder("aiLeaderboards")
public class DeepSweFinder {

    private final LeaderboardRegistry registry;
    private final LeaderboardConfigService configService;

    public DeepSweFinder(LeaderboardRegistry registry, LeaderboardConfigService configService) {
        this.registry = registry;
        this.configService = configService;
    }

    public Mono<List<LeaderboardSource>> sources() {
        return Mono.fromSupplier(registry::all);
    }

    /** 后台配置的页面标题，供主题模板复用。 */
    public Mono<String> pageTitle() {
        return Mono.fromSupplier(configService::pageTitle);
    }

    /** 后台配置的页面副标题，供主题模板复用。 */
    public Mono<String> pageSubtitle() {
        return Mono.fromSupplier(configService::pageSubtitle);
    }

    public Mono<LeaderboardMetaVo> meta(String id) {
        return Mono.fromSupplier(() -> registry.get(id)
            .map(LeaderboardSource::meta)
            .orElseGet(LeaderboardMetaVo::new));
    }

    public Mono<List<LeaderboardEntryVo>> top(String id, int n) {
        return Mono.fromSupplier(() -> registry.get(id)
            .map(src -> src.top(n))
            .orElseGet(List::of));
    }

    public Mono<List<LeaderboardEntryVo>> all(String id) {
        return Mono.fromSupplier(() -> registry.get(id)
            .map(LeaderboardSource::all)
            .orElseGet(List::of));
    }
}
