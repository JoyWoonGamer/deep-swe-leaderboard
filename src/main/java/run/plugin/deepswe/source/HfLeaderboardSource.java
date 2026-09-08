package run.plugin.deepswe.source;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import reactor.core.publisher.Mono;
import run.plugin.deepswe.cache.LeaderboardCacheStore;
import run.plugin.deepswe.model.LeaderboardEntryVo;
import run.plugin.deepswe.model.LeaderboardMetaVo;
import run.plugin.deepswe.source.hf.HuggingFaceLeaderboardClient;
import run.plugin.deepswe.source.hf.HuggingFaceLeaderboardClient.HfEntry;

/**
 * 基于 HuggingFace Dataset Leaderboard API 的榜单基类。
 *
 * <p>HF 榜单字段：rank / modelId / value / verified / author。与 DeepSWE 字段不同，
 * 因此归一化为统一的 {@link LeaderboardEntryVo}：value → passRate（分数），无成本/步数信息。</p>
 */
public abstract class HfLeaderboardSource extends AbstractLeaderboardSource {

    private final HuggingFaceLeaderboardClient client = new HuggingFaceLeaderboardClient();

    private volatile List<HfEntry> entries = List.of();

    protected HfLeaderboardSource(LeaderboardCacheStore cacheStore) {
        super(cacheStore);
    }

    /** HF 数据集 id，如 "SWE-bench/SWE-bench_Verified"。 */
    protected abstract String datasetId();

    @Override
    protected Mono<FetchResult> doFetch() {
        return client.fetch(datasetId())
            .map(list -> {
                List<HfEntry> sorted = list.stream()
                    .sorted(Comparator.comparingDouble((HfEntry e) -> e.value).reversed())
                    .toList();
                this.entries = sorted;
                return new FetchResult(sorted, "huggingface");
            });
    }

    @Override
    protected LeaderboardMetaVo buildMeta() {
        return new LeaderboardMetaVo();
    }

    @Override
    protected Instant parseGeneratedAt(Object payload) {
        return null; // HF 接口不含生成时间
    }

    @Override
    protected String buildError() {
        return "HuggingFace 数据源抓取失败（" + datasetId() + "）";
    }

    /** 归一化为 VO 列表（已按 value 降序）。 */
    @Override
    protected List<LeaderboardEntryVo> toVoList() {
        List<LeaderboardEntryVo> out = new ArrayList<>();
        for (HfEntry e : entries) {
            LeaderboardEntryVo vo = new LeaderboardEntryVo();
            vo.setModel(e.modelId);
            vo.setDisplayName(humanize(e.modelId));
            vo.setEffort("");
            vo.setProvider(e.authorName);
            vo.setPassRate(e.value / 100.0);
            vo.setPassRatePct((int) Math.round(e.value));
            vo.setCiHalfPct(0);
            vo.setCost(0);
            vo.setOutTok(0);
            vo.setSteps(0);
            vo.setDurationSeconds(0);
            vo.setNAttempted(0);
            vo.setNTasksPassedAny(0);
            out.add(vo);
        }
        return out;
    }

    private static String humanize(String modelId) {
        if (modelId == null) {
            return "";
        }
        int slash = modelId.indexOf('/');
        return slash >= 0 ? modelId.substring(slash + 1) : modelId;
    }
}
