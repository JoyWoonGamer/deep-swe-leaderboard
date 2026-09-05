package run.halo.plugin.deepswe.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DeepSWE leaderboard-live.json 的顶层结构。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeaderboardPayload {

    @JsonProperty("generated_at")
    private String generatedAt;

    @JsonProperty("n_tasks_in_set")
    private Integer nTasksInSet;

    @JsonProperty("rows")
    private List<LeaderboardRow> rows;

    public String generatedAt() {
        return generatedAt;
    }

    public Integer nTasksInSet() {
        return nTasksInSet;
    }

    public List<LeaderboardRow> rows() {
        return rows == null ? List.of() : rows;
    }
}
