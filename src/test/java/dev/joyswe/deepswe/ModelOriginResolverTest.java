package dev.joyswe.deepswe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import dev.joyswe.deepswe.model.ModelOriginResolver;
import dev.joyswe.deepswe.model.ModelOriginResolver.OriginTag;

class ModelOriginResolverTest {

    private OriginTag resolve(String provider, String model) {
        return ModelOriginResolver.resolve(provider, model);
    }

    @Test
    void chineseVendorsByProvider() {
        assertEquals("中国", resolve("DeepSeek", "DeepSeek-V4-Pro").region());
        assertEquals("DeepSeek", resolve("DeepSeek", "DeepSeek-V4-Pro").vendor());
        assertEquals("中国", resolve("Moonshot", "Kimi-K3").region());
        assertEquals("中国", resolve("Alibaba", "Qwen3.8-Max").region());
        assertEquals("中国", resolve("Zhipu AI", "GLM-5.3").region());
        assertEquals("中国", resolve("ByteDance", "Doubao-Seed-2.1-Pro").region());
        assertEquals("中国", resolve("Tencent", "Hy3-preview").region());
        assertEquals("中国", resolve("MiniMax", "MiniMax-M3").region());
    }

    @Test
    void usVendorsByProvider() {
        assertEquals("美国", resolve("Anthropic", "Claude Opus 5 (high)").region());
        assertEquals("Anthropic", resolve("Anthropic", "Claude").vendor());
        assertEquals("美国", resolve("OpenAI", "GPT-5.5").region());
        assertEquals("美国", resolve("Google", "Gemini-3.7-Flash").region());
        assertEquals("美国", resolve("xAI", "Grok-4.6").region());
    }

    @Test
    void euAndKrVendors() {
        assertEquals("欧洲", resolve("Mistral", "Mistral-Large").region());
        assertEquals("韩国", resolve("Naver", "HCX-003").region());
        assertEquals("韩国", resolve("Upstage", "SOLAR-10.7B").region());
    }

    @Test
    void fallbackByModelPrefix() {
        assertEquals("中国", resolve("", "deepseek-ai/DeepSeek-V4-Pro").region());
        assertEquals("中国", resolve("", "Qwen/Qwen3.5-397B-A17B").region());
        assertEquals("美国", resolve(null, "openai/gpt-5.5").region());
        assertEquals("欧洲", resolve(null, "mistralai/Mistral-Large").region());
    }

    @Test
    void unknownFallsBackToInternational() {
        assertEquals("国际", resolve(null, null).region());
        assertEquals("国际", resolve("SomeUnknownOrg", "SomeModel").region());
        assertEquals("美国", ModelOriginResolver.resolve("some-author", "meta-llama/Meta-Llama-3.1-70B").region());
    }
}