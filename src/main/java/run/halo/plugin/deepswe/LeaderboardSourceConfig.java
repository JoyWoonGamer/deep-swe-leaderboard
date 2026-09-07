package run.halo.plugin.deepswe;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import run.halo.app.plugin.PluginsRootGetter;
import run.halo.plugin.deepswe.cache.LeaderboardCacheStore;
import run.halo.plugin.deepswe.source.DeepSweSource;
import run.halo.plugin.deepswe.source.HleSource;
import run.halo.plugin.deepswe.source.LeaderboardRegistry;
import run.halo.plugin.deepswe.source.LeaderboardSource;
import run.halo.plugin.deepswe.source.SweBenchSource;

/**
 * 装配榜单数据源与注册中心。
 *
 * <p>接入新榜：新增一个 {@link LeaderboardSource} 实现类，在此 {@code @Bean} 注册即可，
 * 首页自动多卡片、自动多 {@code /benchmarks/{id}} 分页。</p>
 */
@Configuration
public class LeaderboardSourceConfig {

    @Bean
    public LeaderboardCacheStore leaderboardCacheStore(PluginsRootGetter pluginsRootGetter) {
        return new LeaderboardCacheStore(pluginsRootGetter.get());
    }

    @Bean
    public DeepSweSource deepSweSource(LeaderboardCacheStore cacheStore,
        LeaderboardConfigService configService) {
        return new DeepSweSource(cacheStore, configService.currentVersion());
    }

    @Bean
    public SweBenchSource sweBenchSource(LeaderboardCacheStore cacheStore) {
        return new SweBenchSource(cacheStore);
    }

    @Bean
    public HleSource hleSource(LeaderboardCacheStore cacheStore) {
        return new HleSource(cacheStore);
    }

    @Bean
    public LeaderboardRegistry leaderboardRegistry(List<LeaderboardSource> sources) {
        return new LeaderboardRegistry(sources);
    }
}
