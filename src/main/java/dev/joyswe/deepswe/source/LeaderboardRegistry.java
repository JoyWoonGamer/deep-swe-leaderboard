package dev.joyswe.deepswe.source;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 榜单注册中心：持有全部 {@link LeaderboardSource}，提供按 id 路由与枚举能力。
 * 新增榜单只需在构造时传入对应 source 实例。
 */
public class LeaderboardRegistry {

    private final Map<String, LeaderboardSource> sources = new LinkedHashMap<>();

    public LeaderboardRegistry(List<LeaderboardSource> sourceList) {
        for (LeaderboardSource s : sourceList) {
            sources.put(s.id(), s);
        }
    }

    public Optional<LeaderboardSource> get(String id) {
        return Optional.ofNullable(sources.get(id));
    }

    public List<LeaderboardSource> all() {
        return List.copyOf(sources.values());
    }

    public boolean contains(String id) {
        return sources.containsKey(id);
    }
}
