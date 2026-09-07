package run.halo.plugin.deepswe.model;

import java.util.List;

/**
 * 榜单的持久化快照：归一化后的榜单行 + 元信息。
 * 所有榜单统一用此结构落盘，插件重启后回填内存缓存，避免「重启丢数据 + 首屏依赖重拉」。
 */
public class CachedSnapshot {

    private String id;
    private List<LeaderboardEntryVo> rows;
    private String source;
    private String fetchedAt;
    private String generatedAt;
    private int nTasks;

    public CachedSnapshot() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<LeaderboardEntryVo> getRows() {
        return rows == null ? List.of() : rows;
    }

    public void setRows(List<LeaderboardEntryVo> rows) {
        this.rows = rows;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(String fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public int getNTasks() {
        return nTasks;
    }

    public void setNTasks(int nTasks) {
        this.nTasks = nTasks;
    }
}
