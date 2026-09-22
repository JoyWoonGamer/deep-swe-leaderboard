package dev.joyswe.deepswe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import dev.joyswe.deepswe.cache.LeaderboardCacheStore;
import dev.joyswe.deepswe.source.OpenCompassSource;

class OpenCompassSourceTest {

    private OpenCompassSource source(Path dir) {
        return new OpenCompassSource(new LeaderboardCacheStore(dir));
    }

    @Test
    void metadata() throws Exception {
        Path dir = Files.createTempDirectory("compass-test");
        OpenCompassSource source = source(dir);
        assertEquals("compass", source.id());
        assertEquals("司南 OpenCompass", source.displayName());
        assertTrue(source.description().contains("上海 AI Lab"));
        assertEquals("评测榜", source.category());
    }

    @Test
    void emptyWhenNoData() throws Exception {
        Path dir = Files.createTempDirectory("compass-test");
        OpenCompassSource source = source(dir);
        assertTrue(source.all().isEmpty());
        assertFalse(source.meta().isAvailable());
    }

    @Test
    void loadsFromDiskOnStartup() throws Exception {
        Path dir = Files.createTempDirectory("compass-test");
        String json = """
            {"id":"compass","source":"OpenCompass OSS","fetchedAt":"2026-09-06T00:00:00Z",
             "rows":[
               {"model":"DeepSeek-V4-Pro","displayName":"DeepSeek-V4-Pro","provider":"DeepSeek",
                "effort":"开源权重","passRatePct":78,"summary":"知识 93 · 推理 56 · 数学 71 · 代码 91"},
               {"model":"Claude Opus 5 (high)","displayName":"Claude Opus 5 (high)","provider":"Anthropic",
                "effort":"闭源 API","passRatePct":83}
             ]}
            """;
        Files.writeString(dir.resolve("compass.json"), json);

        OpenCompassSource source = source(dir);
        assertTrue(source.meta().isAvailable());
        assertEquals(2, source.all().size());
        assertEquals("DeepSeek-V4-Pro", source.all().get(0).getModel());
        assertEquals(78, source.all().get(0).getPassRatePct());
        assertEquals("知识 93 · 推理 56 · 数学 71 · 代码 91", source.all().get(0).getSummary());
        // 地区/厂商为懒计算字段：磁盘快照不带时按 provider 实时推导
        assertEquals("中国", source.all().get(0).getRegion());
        assertEquals("DeepSeek", source.all().get(0).getVendor());
        assertNotNull(source.all().get(1).getDisplayName());
    }
}