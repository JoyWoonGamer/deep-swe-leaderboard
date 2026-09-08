package dev.joyswe.deepswe.scheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import dev.joyswe.deepswe.LeaderboardConfigService;
import dev.joyswe.deepswe.source.LeaderboardRegistry;
import dev.joyswe.deepswe.source.LeaderboardSource;

/**
 * 定时刷新全部榜单。
 * 每 5 分钟检查一次；各榜单达到配置的刷新间隔（默认 60 分钟）则抓取最新数据。
 * 由插件生命周期（start/stop）驱动，避免依赖 Spring @Scheduled。
 */
@Component
public class LeaderboardScheduler {

    private static final long CHECK_PERIOD_MS = 5 * 60 * 1000L;
    private static final long INITIAL_DELAY_MS = 10_000L;

    private final LeaderboardRegistry registry;
    private final LeaderboardConfigService configService;
    private volatile ScheduledExecutorService executor;

    public LeaderboardScheduler(LeaderboardRegistry registry, LeaderboardConfigService configService) {
        this.registry = registry;
        this.configService = configService;
    }

    /** 启动定时任务（幂等）。 */
    public synchronized void start() {
        if (executor != null) {
            return;
        }
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ai-leaderboard-scheduler");
            t.setDaemon(true);
            return t;
        });
        exec.scheduleWithFixedDelay(this::tick, INITIAL_DELAY_MS, CHECK_PERIOD_MS, TimeUnit.MILLISECONDS);
        this.executor = exec;
    }

    /** 停止定时任务。 */
    public synchronized void stop() {
        ScheduledExecutorService exec = this.executor;
        if (exec == null) {
            return;
        }
        this.executor = null;
        exec.shutdownNow();
    }

    private void tick() {
        try {
            configService.refresh();
            if (!configService.enabled()) {
                return;
            }
            long minutes = configService.refreshMinutes();
            for (LeaderboardSource source : registry.all()) {
                try {
                    source.refreshIfNeeded(minutes).block(java.time.Duration.ofMinutes(4));
                } catch (Exception ignored) {
                    // 单个榜单刷新失败不影响其他榜单，下次重试
                }
            }
        } catch (Exception ignored) {
            // 调度异常不影响后续
        }
    }
}
