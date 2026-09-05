package run.halo.plugin.deepswe.scheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import run.halo.plugin.deepswe.service.LeaderboardService;

/**
 * 定时刷新排行榜。
 * 每 5 分钟检查一次；若到达配置的刷新间隔（默认 60 分钟）则抓取最新数据。
 * 由插件生命周期（start/stop）驱动，避免依赖 Spring @Scheduled。
 */
@Component
public class LeaderboardScheduler {

    private static final long CHECK_PERIOD_MS = 5 * 60 * 1000L;
    private static final long INITIAL_DELAY_MS = 10_000L;

    private final LeaderboardService service;
    private volatile ScheduledExecutorService executor;

    public LeaderboardScheduler(LeaderboardService service) {
        this.service = service;
    }

    /** 启动定时任务（幂等）。 */
    public synchronized void start() {
        if (executor != null) {
            return;
        }
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "deepswe-leaderboard-scheduler");
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
            service.refreshIfNeeded().block(java.time.Duration.ofMinutes(4));
        } catch (Exception ignored) {
            // 刷新失败不影响调度，下次重试
        }
    }
}
