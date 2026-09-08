package run.plugin.deepswe.source;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import run.plugin.deepswe.cache.LeaderboardCacheStore;
import run.plugin.deepswe.model.CachedSnapshot;
import run.plugin.deepswe.model.LeaderboardEntryVo;
import run.plugin.deepswe.model.LeaderboardMetaVo;

/**
 * 榜单数据源的通用缓存骨架：内存缓存 + 磁盘持久化 + 刷新节流 + 失败计数。
 *
 * <p>持久化：抓取成功后把归一化快照写入磁盘（{@code {pluginsRoot}/{id}.json}），
 * 插件重启时 {@link #loadFromDisk()} 回填，避免「重启丢数据 + 首屏依赖重拉成功」。
 * 各榜单只需实现 {@link #doFetch()}、{@link #buildMeta()}、{@link #toVoList()} 等。</p>
 */
public abstract class AbstractLeaderboardSource implements LeaderboardSource {

    private static final Logger log = LoggerFactory.getLogger(AbstractLeaderboardSource.class);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final LeaderboardCacheStore cacheStore;

    private volatile Object payload;
    private volatile String source;
    private volatile Instant fetchedAt;
    private volatile Instant generatedAt;
    private volatile String error;
    private volatile int consecutiveFailures;

    /** 磁盘快照（payload 为空时的兜底），保证重启后仍有数据可展示。 */
    private volatile CachedSnapshot snapshot;

    public AbstractLeaderboardSource(LeaderboardCacheStore cacheStore) {
        this.cacheStore = cacheStore;
        loadFromDisk();
    }

    /** 启动时从磁盘回填缓存快照。 */
    private void loadFromDisk() {
        try {
            CachedSnapshot snap = cacheStore.load(id());
            if (snap != null && !snap.getRows().isEmpty()) {
                this.snapshot = snap;
                this.source = snap.getSource();
                this.fetchedAt = parseInstant(snap.getFetchedAt());
                this.generatedAt = parseInstant(snap.getGeneratedAt());
            }
        } catch (Exception e) {
            log.warn("[{}] 回填磁盘缓存失败", id(), e);
        }
    }

    /** 抓取成功后持久化到磁盘。 */
    private void persist() {
        try {
            CachedSnapshot snap = new CachedSnapshot();
            snap.setId(id());
            snap.setRows(toVoList());
            snap.setSource(source);
            snap.setFetchedAt(fetchedAt == null ? null : fetchedAt.toString());
            snap.setGeneratedAt(generatedAt == null ? null : generatedAt.toString());
            snap.setNTasks(buildMeta().getNTasks());
            cacheStore.save(snap);
            this.snapshot = snap;
        } catch (Exception e) {
            log.warn("[{}] 持久化缓存失败", id(), e);
        }
    }

    @Override
    public final Mono<LeaderboardSource.FetchResult> fetch() {
        if (!running.compareAndSet(false, true)) {
            return Mono.empty();
        }
        return doFetch()
            .doOnNext(result -> {
                this.payload = result.payload();
                this.source = result.source();
                this.fetchedAt = Instant.now();
                this.generatedAt = parseGeneratedAt(result.payload());
                this.error = null;
                this.consecutiveFailures = 0;
                persist();
            })
            .switchIfEmpty(Mono.defer(() -> {
                consecutiveFailures++;
                this.error = buildError();
                return Mono.empty();
            }))
            .doFinally(signal -> running.set(false));
    }

    /** 供定时/请求触发：达到刷新间隔才真正抓取。 */
    @Override
    public final Mono<Void> refreshIfNeeded(long refreshMinutes) {
        long intervalMs = refreshMinutes * 60_000L;
        Instant now = Instant.now();
        boolean due = fetchedAt == null || now.isAfter(fetchedAt.plusMillis(intervalMs));
        return due ? fetch().then() : Mono.empty();
    }

    /** 非阻塞触发（SWR）：请求时后台异步重拉，不阻塞当前读取。 */
    @Override
    public final void refreshIfNeededAsync(long refreshMinutes) {
        try {
            refreshIfNeeded(refreshMinutes).subscribe();
        } catch (RuntimeException ignored) {
            // 触发失败不影响当前请求
        }
    }

    @Override
    public final LeaderboardMetaVo meta() {
        LeaderboardMetaVo meta = buildMeta();
        meta.setAvailable(payload != null || snapshot != null);
        meta.setSource(source);
        meta.setFetchedAt(fetchedAt == null ? null : fetchedAt.toString());
        meta.setGeneratedAt(generatedAt == null ? null : generatedAt.toString());
        if (payload != null) {
            meta.setRowCount(rowCount());
            meta.setModelCount(modelCount());
        } else if (snapshot != null) {
            meta.setRowCount(snapshot.getRows().size());
            meta.setModelCount((int) snapshot.getRows().stream()
                .map(LeaderboardEntryVo::getModel)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count());
            meta.setNTasks(snapshot.getNTasks());
        }
        meta.setError(error);
        meta.setConsecutiveFailures(consecutiveFailures);
        return meta;
    }

    @Override
    public List<LeaderboardEntryVo> all() {
        return currentRows();
    }

    @Override
    public List<LeaderboardEntryVo> top(int n) {
        if (n <= 0) {
            n = 20;
        }
        List<LeaderboardEntryVo> rows = currentRows();
        return rows.size() <= n ? rows : rows.subList(0, n);
    }

    /** 返回当前有效行（优先内存 payload，无则磁盘快照）。 */
    protected final List<LeaderboardEntryVo> currentRows() {
        if (payload != null) {
            return toVoList();
        }
        if (snapshot != null) {
            return snapshot.getRows();
        }
        return List.of();
    }

    /** 子类实现：实际抓取逻辑，失败返回 empty。 */
    protected abstract Mono<FetchResult> doFetch();

    /** 子类实现：填充榜单静态元信息（任务数等）。 */
    protected abstract LeaderboardMetaVo buildMeta();

    /** 子类实现：把 payload 归一化为 VO 列表（按分数降序）。 */
    protected abstract List<LeaderboardEntryVo> toVoList();

    /** 子类实现：从 payload 解析生成时间，无则 null。 */
    protected Instant parseGeneratedAt(Object payload) {
        return null;
    }

    /** 子类实现：行数（payload 非空时）。 */
    protected int rowCount() {
        return toVoList().size();
    }

    /** 子类实现：模型数（payload 非空时）。 */
    protected int modelCount() {
        return (int) toVoList().stream()
            .map(LeaderboardEntryVo::getModel)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .count();
    }

    /** 子类实现：失败时错误文案。 */
    protected String buildError() {
        return "数据源抓取失败";
    }

    protected Object currentPayload() {
        return payload;
    }

    protected String currentSource() {
        return source;
    }

    protected Instant currentFetchedAt() {
        return fetchedAt;
    }

    protected Instant currentGeneratedAt() {
        return generatedAt;
    }

    protected void logError(String msg, Throwable e) {
        log.error("[{}] {}", id(), msg, e);
    }

    protected Duration timeout() {
        return Duration.ofSeconds(25);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
