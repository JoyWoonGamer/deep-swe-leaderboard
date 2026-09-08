package run.plugin.deepswe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import run.plugin.deepswe.cache.LeaderboardCacheStore;
import run.plugin.deepswe.source.DeepSweSource;

class DeepSweSourceTest {

    private DeepSweSource source(Path dir) {
        return new DeepSweSource(new LeaderboardCacheStore(dir), "v1.1");
    }

    @Test
    void metadata() throws Exception {
        Path dir = Files.createTempDirectory("deepswe-test");
        DeepSweSource source = source(dir);
        assertEquals("deepswe", source.id());
        assertEquals("DeepSWE", source.displayName());
        assertTrue(source.description().length() > 0);
    }

    @Test
    void emptyWhenNoData() throws Exception {
        Path dir = Files.createTempDirectory("deepswe-test");
        DeepSweSource source = source(dir);
        assertTrue(source.all().isEmpty());
        assertFalse(source.meta().isAvailable());
    }

    @Test
    void loadsFromDiskOnStartup() throws Exception {
        Path dir = Files.createTempDirectory("deepswe-test");
        String json = """
            {"id":"deepswe","source":"mirror","fetchedAt":"2026-09-06T00:00:00Z",
             "rows":[{"model":"gpt-6-astra","displayName":"gpt-6 astra","passRatePct":74,"cost":6.52}]}
            """;
        Files.writeString(dir.resolve("deepswe.json"), json);

        DeepSweSource source = source(dir);
        assertTrue(source.meta().isAvailable());
        assertEquals(1, source.all().size());
        assertEquals("gpt-6-astra", source.all().get(0).getModel());
        assertEquals(74, source.all().get(0).getPassRatePct());
    }
}
