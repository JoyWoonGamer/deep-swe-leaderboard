package run.halo.plugin.deepswe.service;

/**
 * 插件设置（对应 extensions/settings.yaml 中 name 为 source/version/refreshMinutes/enabled/topN 的表单字段）。
 * 标准 JavaBean（无参构造 + getter + setter），便于 Jackson 反序列化；
 * 读取时通过 xxx() 方法对缺失/非法值做安全回退。
 */
public class DeepSweSetting {

    public static final String GROUP = "deepSwe";

    private String source = "auto";
    private String version = "v1.1";
    private Integer refreshMinutes = 60;
    private Boolean enabled = true;
    private Integer topN = 20;
    private String defaultView = "table";

    public DeepSweSetting() {
    }

    // ---- 安全读取（带回退）----

    public String source() {
        return source == null || source.isBlank() ? "auto" : source.trim().toLowerCase();
    }

    public String version() {
        return version == null || version.isBlank() ? "v1.1" : version.trim();
    }

    public int refreshMinutes() {
        return refreshMinutes == null || refreshMinutes <= 0 ? 60 : refreshMinutes;
    }

    public boolean enabled() {
        return enabled == null || enabled;
    }

    public int topN() {
        return topN == null || topN <= 0 ? 20 : topN;
    }

    public String defaultView() {
        if (defaultView == null || defaultView.isBlank()) {
            return "table";
        }
        String v = defaultView.trim().toLowerCase();
        return switch (v) {
            case "bars", "podium", "grid", "neon", "table" -> v;
            default -> "table";
        };
    }

    // ---- Jackson 反序列化所需的 getter / setter ----

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Integer getRefreshMinutes() {
        return refreshMinutes;
    }

    public void setRefreshMinutes(Integer refreshMinutes) {
        this.refreshMinutes = refreshMinutes;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getTopN() {
        return topN;
    }

    public void setTopN(Integer topN) {
        this.topN = topN;
    }

    public String getDefaultView() {
        return defaultView;
    }

    public void setDefaultView(String defaultView) {
        this.defaultView = defaultView;
    }
}
