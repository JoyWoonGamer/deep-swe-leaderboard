package run.plugin.deepswe.service;

/**
 * 插件设置（对应 extensions/settings.yaml 中 name 为 source/version/refreshMinutes/enabled/topN 的表单字段）。
 * 标准 JavaBean（无参构造 + getter + setter），便于 Jackson 反序列化；
 * 读取时通过 xxx() 方法对缺失/非法值做安全回退。
 */
public class DeepSweSetting {

    public static final String GROUP = "deepSwe";

    /** 页面标题回退值（与 settings.yaml 的默认值保持一致）。 */
    public static final String DEFAULT_PAGE_TITLE = "AI大模型排行榜";

    /** 页面副标题回退值。 */
    public static final String DEFAULT_PAGE_SUBTITLE = "聚合展示多个 AI 模型基准排行榜，实时更新";

    private String source = "auto";
    private String version = "v1.1";
    private Integer refreshMinutes = 60;
    private Boolean enabled = true;
    private Integer topN = 20;
    private String defaultView = "table";
    private String pageTitle = DEFAULT_PAGE_TITLE;
    private String pageSubtitle = DEFAULT_PAGE_SUBTITLE;

    public DeepSweSetting() {
    }

    // ---- 安全读取（带回退）----

    /** 前台页面大标题 / 浏览器标签标题，空值时回退为 {@link #DEFAULT_PAGE_TITLE}。 */
    public String pageTitle() {
        if (pageTitle == null || pageTitle.isBlank()) {
            return DEFAULT_PAGE_TITLE;
        }
        String t = pageTitle.trim();
        return t.length() > 60 ? t.substring(0, 60) : t;
    }

    /** 前台页面副标题，空值时回退为 {@link #DEFAULT_PAGE_SUBTITLE}。 */
    public String pageSubtitle() {
        if (pageSubtitle == null || pageSubtitle.isBlank()) {
            return DEFAULT_PAGE_SUBTITLE;
        }
        String t = pageSubtitle.trim();
        return t.length() > 120 ? t.substring(0, 120) : t;
    }

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

    public String getPageTitle() {
        return pageTitle;
    }

    public void setPageTitle(String pageTitle) {
        this.pageTitle = pageTitle;
    }

    public String getPageSubtitle() {
        return pageSubtitle;
    }

    public void setPageSubtitle(String pageSubtitle) {
        this.pageSubtitle = pageSubtitle;
    }
}
