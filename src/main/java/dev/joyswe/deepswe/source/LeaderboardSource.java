package dev.joyswe.deepswe.source;

import java.util.List;
import reactor.core.publisher.Mono;
import dev.joyswe.deepswe.model.LeaderboardEntryVo;
import dev.joyswe.deepswe.model.LeaderboardMetaVo;

/**
 * 排行榜数据源抽象。每个榜单（DeepSWE / SWE-bench / HLE ...）实现一个 Source。
 *
 * <p>接入新榜只需：实现本接口 + 注册到 {@link LeaderboardRegistry}，
 * 首页自动多一个卡片、自动多一个 {@code /benchmarks/{id}} 分页，前端框架零改动。</p>
 */
public interface LeaderboardSource {

    /** 唯一标识，即路由 slug 与缓存 key，如 "deepswe" / "swebench"。 */
    String id();

    /** 榜单显示名，如 "DeepSWE" / "SWE-bench Verified"。 */
    String displayName();

    /** 榜单简介，用于首页卡片副标题。 */
    String description();

    /**
     * 榜单参赛池类别，用于聚合首页自解释「为什么榜单有的偏海外、有的偏国产」：
     * {@code "竞技场"} = 闭源 API 模型报名参赛（海外 API 生态密集）；
     * 默认 {@code "评测榜"} = 开放权重提交 / 官方评测（国内外混合）。
     */
    default String category() {
        return "评测榜";
    }

    /** 抓取一次，返回带来源标签的结果。失败应返回 empty，由上层统一记录错误。 */
    Mono<FetchResult> fetch();

    /** 达到刷新间隔才真正抓取（否则空操作）。 */
    Mono<Void> refreshIfNeeded(long refreshMinutes);

    /** 非阻塞触发刷新（SWR）：请求时后台异步重拉，不阻塞当前读取。 */
    void refreshIfNeededAsync(long refreshMinutes);

    /** 归一化后的榜单行（按分数降序）。 */
    List<LeaderboardEntryVo> top(int n);

    /** 全部行（按分数降序）。 */
    List<LeaderboardEntryVo> all();

    /** 元信息：可用性、来源、抓取时间、任务数、错误等。 */
    LeaderboardMetaVo meta();

    /** 抓取结果：原始 payload 抽象 + 实际来源标签。 */
    record FetchResult(Object payload, String source) {
    }
}
