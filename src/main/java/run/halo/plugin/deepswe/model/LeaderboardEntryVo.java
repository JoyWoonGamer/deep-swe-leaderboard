package run.halo.plugin.deepswe.model;

/**
 * 面向主题模板与 REST 接口的展示对象（扁平化、可直接渲染）。
 * 提供 getter 以便 Thymeleaf 通过 ${row.xxx} 访问。
 */
public class LeaderboardEntryVo {

    private String model;
    private String displayName;
    private String effort;
    private String provider;
    private double passRate;
    private int passRatePct;
    private double ciHalfPct;
    private double cost;
    private long outTok;
    private long steps;
    private long durationSeconds;
    private int nAttempted;
    private int nTasksPassedAny;

    public LeaderboardEntryVo() {
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setEffort(String effort) {
        this.effort = effort;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public void setPassRate(double passRate) {
        this.passRate = passRate;
    }

    public void setPassRatePct(int passRatePct) {
        this.passRatePct = passRatePct;
    }

    public void setCiHalfPct(double ciHalfPct) {
        this.ciHalfPct = ciHalfPct;
    }

    public void setCost(double cost) {
        this.cost = cost;
    }

    public void setOutTok(long outTok) {
        this.outTok = outTok;
    }

    public void setSteps(long steps) {
        this.steps = steps;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public void setNAttempted(int nAttempted) {
        this.nAttempted = nAttempted;
    }

    public void setNTasksPassedAny(int nTasksPassedAny) {
        this.nTasksPassedAny = nTasksPassedAny;
    }

    public String getModel() {
        return model;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEffort() {
        return effort;
    }

    public String getProvider() {
        return provider;
    }

    public double getPassRate() {
        return passRate;
    }

    public int getPassRatePct() {
        return passRatePct;
    }

    public double getCiHalfPct() {
        return ciHalfPct;
    }

    public double getCost() {
        return cost;
    }

    public long getOutTok() {
        return outTok;
    }

    public long getSteps() {
        return steps;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public int getNAttempted() {
        return nAttempted;
    }

    public int getNTasksPassedAny() {
        return nTasksPassedAny;
    }

    @Override
    public String toString() {
        return model + " [" + effort + "] " + passRatePct + "%";
    }
}
