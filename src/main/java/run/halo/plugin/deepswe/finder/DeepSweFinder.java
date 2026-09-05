package run.halo.plugin.deepswe.finder;

import java.util.List;
import reactor.core.publisher.Mono;
import run.halo.app.theme.finders.Finder;
import run.halo.plugin.deepswe.model.LeaderboardEntryVo;
import run.halo.plugin.deepswe.model.LeaderboardMetaVo;
import run.halo.plugin.deepswe.service.LeaderboardService;

/**
 * 向主题模板暴露的 Finder。
 * 模板中可这样使用：
 * <pre>
 *   ${deepSwe.meta()}                     元信息
 *   ${deepSwe.top(20)}                    每个模型最佳配置，按分数取前 20
 *   ${deepSwe.all()}                      全部配置行
 * </pre>
 */
@Finder("deepSwe")
public class DeepSweFinder {

    private final LeaderboardService service;

    public DeepSweFinder(LeaderboardService service) {
        this.service = service;
    }

    public Mono<LeaderboardMetaVo> meta() {
        return Mono.fromSupplier(service::meta);
    }

    public Mono<List<LeaderboardEntryVo>> top(int n) {
        return Mono.fromSupplier(() -> service.top(n));
    }

    public Mono<List<LeaderboardEntryVo>> all() {
        return Mono.fromSupplier(service::all);
    }
}
