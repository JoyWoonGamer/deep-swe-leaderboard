package run.halo.plugin.deepswe.model;

/**
 * 排行榜元信息：可用性、来源、抓取时间、任务数等。
 */
public class LeaderboardMetaVo {

    private boolean available;
    private String source;
    private String fetchedAt;
    private String generatedAt;
    private int nTasks;
    private int rowCount;
    private int modelCount;
    private String error;

    public boolean isAvailable() {
        return available;
    }

    public String getSource() {
        return source;
    }

    public String getFetchedAt() {
        return fetchedAt;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public int getNTasks() {
        return nTasks;
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getModelCount() {
        return modelCount;
    }

    public String getError() {
        return error;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setFetchedAt(String fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public void setNTasks(int nTasks) {
        this.nTasks = nTasks;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public void setModelCount(int modelCount) {
        this.modelCount = modelCount;
    }

    public void setError(String error) {
        this.error = error;
    }
}
