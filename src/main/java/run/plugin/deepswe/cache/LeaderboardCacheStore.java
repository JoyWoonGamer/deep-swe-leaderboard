package run.plugin.deepswe.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import run.plugin.deepswe.model.CachedSnapshot;

/**
 * 榜单缓存持久化存储：把归一化快照写入插件根目录下的 JSON 文件，重启后回填。
 *
 * <p>落盘位置：{@code {pluginsRoot}/{id}.json}。原子写入（临时文件 + 移动），避免写一半损坏。</p>
 */
public class LeaderboardCacheStore {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardCacheStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path root;

    public LeaderboardCacheStore(Path root) {
        this.root = root;
    }

    /** 读取指定榜单的缓存快照；不存在或损坏返回 null。 */
    public CachedSnapshot load(String id) {
        Path file = fileFor(id);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String body = Files.readString(file, StandardCharsets.UTF_8);
            return MAPPER.readValue(body, CachedSnapshot.class);
        } catch (IOException e) {
            log.warn("读取缓存失败 id={}", id, e);
            return null;
        }
    }

    /** 写入指定榜单的缓存快照（原子）。 */
    public void save(CachedSnapshot snapshot) {
        if (snapshot == null || snapshot.getId() == null) {
            return;
        }
        try {
            Path dir = root.toAbsolutePath();
            Files.createDirectories(dir);
            Path file = fileFor(snapshot.getId());
            Path tmp = dir.resolve(snapshot.getId() + ".json.tmp");
            String body = MAPPER.writeValueAsString(snapshot);
            Files.writeString(tmp, body, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("写入缓存失败 id={}", snapshot.getId(), e);
        }
    }

    private Path fileFor(String id) {
        return root.toAbsolutePath().resolve(id + ".json");
    }
}
