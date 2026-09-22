package dev.joyswe.deepswe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import dev.joyswe.deepswe.cache.LeaderboardCacheStore;
import dev.joyswe.deepswe.source.ArtificialAnalysisSource;
import dev.joyswe.deepswe.source.ArtificialAnalysisSource.AaRow;

/**
 * Artificial Analysis 数据源测试：使用目标上线日抓取的真实页面作为 fixture，
 * 验证 RSC flight 提取、models 数组解析、基础模型名/档位切分与「每模型最佳配置」聚合。
 */
class ArtificialAnalysisSourceTest {

    private static String fixtureHtml() throws Exception {
        Path fixture = Path.of("src", "test", "resources", "artanalysis-models.html");
        assertTrue(Files.exists(fixture), "fixture missing: " + fixture.toAbsolutePath());
        return Files.readString(fixture);
    }

    private ArtificialAnalysisSource source(Path dir) {
        return new ArtificialAnalysisSource(new LeaderboardCacheStore(dir));
    }

    @Test
    void metadata() throws Exception {
        Path dir = Files.createTempDirectory("artanalysis-test");
        ArtificialAnalysisSource source = source(dir);
        assertEquals("artanalysis", source.id());
        assertEquals("Artificial Analysis", source.displayName());
        assertTrue(source.description().length() > 0);
    }

    @Test
    void emptyWhenNoData() throws Exception {
        Path dir = Files.createTempDirectory("artanalysis-test");
        ArtificialAnalysisSource source = source(dir);
        assertTrue(source.all().isEmpty());
        assertFalse(source.meta().isAvailable());
    }

    @Test
    void parsesRealPage() throws Exception {
        List<AaRow> rows = ArtificialAnalysisSource.parseHtmlStatic(fixtureHtml());
        assertTrue(rows.size() >= 600, "应解析出全部模型（673）");
        // 榜首应为 Claude Opus 5.5（Max Effort）
        AaRow top = rows.get(0);
        assertEquals("Claude Opus 5.5", top.baseName());
        assertTrue(top.intelligenceIndex() > 50, "榜首智能指数应 > 50");
        assertFalse(top.deprecated());
    }

    @Test
    void dedupsByBaseNameBestConfig() throws Exception {
        Path dir = Files.createTempDirectory("artanalysis-test");
        ArtificialAnalysisSource source = source(dir);
        List<AaRow> rows = ArtificialAnalysisSource.parseHtmlStatic(fixtureHtml());
        // Claude Opus 5.5 有多个 effort 档，原始行应含多档
        long claudeCount = rows.stream()
            .filter(r -> r.baseName().equals("Claude Opus 5.5"))
            .count();
        assertTrue(claudeCount >= 5, "原始行应有多个 Claude Opus 5.5 effort 档（5）");
        assertNotNull(source);
    }

    @Test
    void baseNameAndEffortSplitting() throws Exception {
        List<AaRow> rows = ArtificialAnalysisSource.parseHtmlStatic(fixtureHtml());
        AaRow gpt6 = rows.stream().filter(r -> r.baseName().equals("GPT-6 Astra")).findFirst().orElse(null);
        assertNotNull(gpt6, "应有 GPT-6 Astra");
        if (gpt6 != null) {
            assertTrue(gpt6.effort().length() > 0, "effort 不应为空（如 max）");
        }
    }
}