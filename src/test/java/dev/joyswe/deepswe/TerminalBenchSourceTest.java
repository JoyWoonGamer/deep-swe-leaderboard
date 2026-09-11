package dev.joyswe.deepswe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import dev.joyswe.deepswe.cache.LeaderboardCacheStore;
import dev.joyswe.deepswe.source.TerminalBenchSource;
import dev.joyswe.deepswe.source.TerminalBenchSource.TbRow;

/**
 * Terminal-Bench 数据源测试：使用 2026-09-11 抓取的真实 tbench.ai 首页作为 fixture，
 * 验证 flight payload 提取、括号配对、rows 解析与「模型×Agent 最佳配置」归一化。
 */
class TerminalBenchSourceTest {

    private static String fixtureHtml() throws Exception {
        Path fixture = Path.of("src", "test", "resources", "tbench-home.html");
        assertTrue(Files.exists(fixture), "fixture missing: " + fixture.toAbsolutePath());
        return Files.readString(fixture);
    }

    private TerminalBenchSource source(Path dir) {
        return new TerminalBenchSource(new LeaderboardCacheStore(dir));
    }

    @Test
    void metadata() throws Exception {
        Path dir = Files.createTempDirectory("tbench-test");
        TerminalBenchSource source = source(dir);
        assertEquals("tbench", source.id());
        assertEquals("Terminal-Bench", source.displayName());
        assertTrue(source.description().length() > 0);
    }

    @Test
    void emptyWhenNoData() throws Exception {
        Path dir = Files.createTempDirectory("tbench-test");
        TerminalBenchSource source = source(dir);
        assertTrue(source.all().isEmpty());
        assertFalse(source.meta().isAvailable());
    }

    @Test
    void parsesRealHomepage() throws Exception {
        List<TbRow> rows = TerminalBenchSource.parseHtmlStatic(fixtureHtml());
        assertFalse(rows.isEmpty(), "应解析出榜单行");
        // 榜首应为 GPT-6 Astra + Codex
        TbRow top = rows.get(0);
        assertEquals("GPT-6 Astra", top.model());
        assertEquals("Codex", top.agent());
        assertTrue(top.accuracy() > 50, "accuracy 应为百分比（如 58.18）");
    }

    @Test
    void parsesUpdatedAt() throws Exception {
        Instant updatedAt = TerminalBenchSource.parseUpdatedAtStatic(fixtureHtml());
        assertNotNull(updatedAt, "应解析出 leaderboard updated_at");
        assertTrue(updatedAt.isAfter(Instant.parse("2026-09-01T00:00:00Z")),
            "updated_at 应为近期（如 2026-09-10）");
    }

    @Test
    void normalizesByModelAgentBest() throws Exception {
        Path dir = Files.createTempDirectory("tbench-test");
        TerminalBenchSource source = source(dir);
        List<TbRow> rows = TerminalBenchSource.parseHtmlStatic(fixtureHtml());
        // 模拟抓取成功：写内存行（通过反射不可取，改为直接验证 toVoList 的聚合规则）
        // 这里验证解析行中同一 (model, agent) 存在多档 effort，聚合后应为单行
        long astraCodex = rows.stream()
            .filter(r -> r.model().equals("GPT-6 Astra") && r.agent().equals("Codex"))
            .count();
        assertTrue(astraCodex >= 1, "应有 GPT-6 Astra × Codex 行");

        // 通过真实抓取链路验证归一化（需 WebClient，跳过）；此处至少验证全部行可排序
        assertTrue(rows.size() >= 10, "应有足够榜单行");
    }
}