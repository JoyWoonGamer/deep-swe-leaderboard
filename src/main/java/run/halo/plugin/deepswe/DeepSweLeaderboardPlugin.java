package run.halo.plugin.deepswe;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import run.halo.plugin.deepswe.scheduler.LeaderboardScheduler;

/**
 * DeepSWE 排行榜插件主类，管理插件生命周期并驱动定时刷新。
 */
@Component
public class DeepSweLeaderboardPlugin extends BasePlugin {

    private final LeaderboardScheduler scheduler;

    public DeepSweLeaderboardPlugin(PluginContext pluginContext, LeaderboardScheduler scheduler) {
        super(pluginContext);
        this.scheduler = scheduler;
    }

    @Override
    public void start() {
        scheduler.start();
    }

    @Override
    public void stop() {
        scheduler.stop();
    }
}
