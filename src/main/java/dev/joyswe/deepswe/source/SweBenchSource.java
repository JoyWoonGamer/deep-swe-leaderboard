package dev.joyswe.deepswe.source;

import dev.joyswe.deepswe.cache.LeaderboardCacheStore;

/**
 * SWE-bench Verified 排行榜（HuggingFace Dataset Leaderboard API）。
 * 500 个人工校验的 GitHub issue，测代码修复能力，行业最权威的 agentic-coding 基准之一。
 */
public class SweBenchSource extends HfLeaderboardSource {

    public SweBenchSource(LeaderboardCacheStore cacheStore) {
        super(cacheStore);
    }

    @Override
    public String id() {
        return "swebench";
    }

    @Override
    public String displayName() {
        return "SWE-bench Verified";
    }

    @Override
    public String description() {
        return "500 个人工校验的 GitHub issue · % 已解决（行业最权威的代码修复基准）";
    }

    @Override
    protected String datasetId() {
        return "SWE-bench/SWE-bench_Verified";
    }
}
