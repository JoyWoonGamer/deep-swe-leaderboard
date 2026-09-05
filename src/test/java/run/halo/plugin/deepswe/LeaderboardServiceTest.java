package run.halo.plugin.deepswe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.plugin.deepswe.client.DeepSweClient;
import run.halo.plugin.deepswe.model.LeaderboardEntryVo;
import run.halo.plugin.deepswe.model.LeaderboardPayload;
import run.halo.plugin.deepswe.service.DeepSweSetting;
import run.halo.plugin.deepswe.service.LeaderboardService;

class LeaderboardServiceTest {

    private static final String SAMPLE = """
        {
          "generated_at": "2026-09-03T22:24:37.984682+00:00",
          "n_tasks_in_set": 113,
          "rows": [
            {
              "model": "gpt-6-astra",
              "harness": "mini-swe-agent",
              "provider": "openai",
              "reasoning_effort": "xhigh",
              "pass_rate": 0.7411,
              "n_attempted": 452,
              "n_tasks_passed_any": 91,
              "ci_half": 0.028653,
              "mean_cost_usd": 6.5237,
              "mean_output_tokens": 29557.3,
              "mean_agent_steps": 28.75
            },
            {
              "model": "gpt-6-astra",
              "harness": "mini-swe-agent",
              "reasoning_effort": "medium",
              "pass_rate": 0.7278,
              "mean_cost_usd": 4.3795,
              "mean_output_tokens": 20361.8,
              "mean_agent_steps": 26.03
            },
            {
              "model": "claude-opus-5",
              "harness": "mini-swe-agent",
              "reasoning_effort": "max",
              "pass_rate": 0.7364,
              "mean_cost_usd": 11.8375,
              "mean_output_tokens": 117565.6,
              "mean_agent_steps": 99.04
            }
          ]
        }
        """;

    private LeaderboardService newService(LeaderboardPayload payload) throws Exception {
        DeepSweClient client = mock(DeepSweClient.class);
        when(client.fetch(any(DeepSweSetting.class)))
            .thenReturn(payload == null ? Mono.empty()
                : Mono.just(new DeepSweClient.FetchResult(payload, "mirror")));
        ReactiveSettingFetcher fetcher = mock(ReactiveSettingFetcher.class);
        when(fetcher.fetch(eq(DeepSweSetting.GROUP), eq(DeepSweSetting.class)))
            .thenReturn(Mono.just(new DeepSweSetting()));
        LeaderboardService service = new LeaderboardService(client, fetcher);
        service.refreshIfNeeded().block();
        return service;
    }

    @Test
    void bestPerModelAndTop() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LeaderboardPayload payload = mapper.readValue(SAMPLE, LeaderboardPayload.class);
        LeaderboardService service = newService(payload);

        List<LeaderboardEntryVo> top = service.top(10);
        assertEquals(2, top.size());
        // gpt-6-astra 最佳配置（xhigh 0.7411）排第一
        assertEquals("gpt-6-astra", top.get(0).getModel());
        assertEquals(74, top.get(0).getPassRatePct());
        assertEquals("xhigh", top.get(0).getEffort());
        assertEquals(3, service.all().size());
    }

    @Test
    void emptyWhenNoData() throws Exception {
        LeaderboardService service = newService(null);
        assertTrue(service.all().isEmpty());
        assertFalse(service.meta().isAvailable());
    }
}
