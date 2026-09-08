package run.plugin.deepswe.source;

import run.plugin.deepswe.cache.LeaderboardCacheStore;

/**
 * Humanity's Last Exam 排行榜（HuggingFace Dataset Leaderboard API）。
 * 3000 道专家校验的博士级难题，覆盖 100+ 学科，最难的通用知识/推理基准。
 */
public class HleSource extends HfLeaderboardSource {

    public HleSource(LeaderboardCacheStore cacheStore) {
        super(cacheStore);
    }

    @Override
    public String id() {
        return "hle";
    }

    @Override
    public String displayName() {
        return "Humanity's Last Exam";
    }

    @Override
    public String description() {
        return "3000 道博士级难题 · % 正确率（最难的通用知识 / 推理基准）";
    }

    @Override
    protected String datasetId() {
        return "cais/hle";
    }
}
