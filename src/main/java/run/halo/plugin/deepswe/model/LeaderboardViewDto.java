package run.halo.plugin.deepswe.model;

import java.util.List;

/**
 * REST 接口返回的整体视图（元信息 + 行数据）。
 */
public class LeaderboardViewDto {

    private boolean available;
    private String source;
    private String fetchedAt;
    private String generatedAt;
    private int nTasks;
    private String error;
    private List<LeaderboardEntryVo> rows;

    public LeaderboardViewDto() {
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
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

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public List<LeaderboardEntryVo> getRows() {
        return rows;
    }

    public void setRows(List<LeaderboardEntryVo> rows) {
        this.rows = rows;
    }
}
