package dev.joyswe.deepswe;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.ReactiveSettingFetcher;
import dev.joyswe.deepswe.service.DeepSweSetting;

/**
 * 读取插件设置，并以同步快照形式暴露给数据源构造。
 * 设置变更通过 {@link #refresh()} 在调度器中定期拉取。
 */
@Component
public class LeaderboardConfigService {

    private final ReactiveSettingFetcher settingFetcher;
    private final AtomicReference<DeepSweSetting> snapshot = new AtomicReference<>(new DeepSweSetting());

    public LeaderboardConfigService(ReactiveSettingFetcher settingFetcher) {
        this.settingFetcher = settingFetcher;
    }

    /** 当前版本号（用于 DeepSWE live 源）。 */
    public String currentVersion() {
        return snapshot.get().version();
    }

    /** 刷新间隔（分钟）。 */
    public int refreshMinutes() {
        return snapshot.get().refreshMinutes();
    }

    /** 是否启用定时刷新。 */
    public boolean enabled() {
        return snapshot.get().enabled();
    }

    /** 默认展示行数。 */
    public int topN() {
        return snapshot.get().topN();
    }

    /** 默认视图。 */
    public String defaultView() {
        return snapshot.get().defaultView();
    }

    /** 前台页面标题（后台可配，空值回退为默认）。 */
    public String pageTitle() {
        return snapshot.get().pageTitle();
    }

    /** 前台页面副标题（后台可配，空值回退为默认）。 */
    public String pageSubtitle() {
        return snapshot.get().pageSubtitle();
    }

    /** 从 Halo 设置拉取最新配置（阻塞，用于调度线程）。 */
    public void refresh() {
        try {
            DeepSweSetting cfg = settingFetcher.fetch(DeepSweSetting.GROUP, DeepSweSetting.class)
                .defaultIfEmpty(new DeepSweSetting())
                .block(java.time.Duration.ofSeconds(10));
            if (cfg != null) {
                snapshot.set(cfg);
            }
        } catch (Exception ignored) {
            // 读取失败保留旧值
        }
    }
}
