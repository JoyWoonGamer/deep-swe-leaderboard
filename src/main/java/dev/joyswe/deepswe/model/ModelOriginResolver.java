package dev.joyswe.deepswe.model;

import java.util.List;
import java.util.Locale;

/**
 * 模型「地区 × 厂商」归属解析器：把榜单行的 provider/org 与模型名映射为简明标签，
 * 用于前端每行展示「地区 · 厂商」，让读者一眼看出榜单参赛池的构成。
 *
 * <p>匹配顺序：先按 provider（厂商/机构，如 "DeepSeek"、"Moonshot"）精确匹配，
 * 再按模型名前缀（HF 仓库 org 前缀，如 "deepseek-ai/"、"Qwen/"）兜底，
 * 都未命中则归为「国际」。</p>
 */
public final class ModelOriginResolver {

    /** 归属信息：region 为地区（中国/美国/欧洲/韩国/国际），vendor 为厂商显示名。 */
    public record OriginTag(String region, String vendor) {
    }

    private static final List<Rule> CHINA_RULES = List.of(
        rule("deepseek", "DeepSeek"),
        rule("zhipu", "智谱AI"), rule("z.ai", "智谱AI"), rule("glm", "智谱AI"), rule("zai", "智谱AI"),
        rule("moonshot", "月之暗面"), rule("kimi", "月之暗面"),
        rule("qwen", "阿里"), rule("alibaba", "阿里"), rule("aliyun", "阿里"), rule("tongyi", "阿里"),
        rule("minimax", "MiniMax"),
        rule("tencent", "腾讯"), rule("hunyuan", "腾讯"), rule("hy3", "腾讯"),
        rule("bytedance", "字节跳动"), rule("doubao", "字节跳动"), rule("seed", "字节跳动"), rule("volcengine", "字节跳动"),
        rule("xiaomi", "小米"), rule("mimo", "小米"),
        rule("stepfun", "阶跃星辰"), rule("step-", "阶跃星辰"),
        rule("01.ai", "零一万物"), rule("zero-one", "零一万物"), rule("ling", "零一万物"),
        rule("baichuan", "百川智能"),
        rule("intern", "上海AI实验室"), rule("internlm", "上海AI实验室"), rule("openbmb", "OpenBMB"),
        rule("netease", "网易"), rule("youdao", "有道"),
        rule("iflytek", "科大讯飞"), rule("i-flytek", "科大讯飞"), rule("xfyun", "科大讯飞"),
        rule("baidu", "百度"), rule("ernie", "百度"), rule("wenxin", "百度"),
        rule("sensetime", "商汤"), rule("sensechat", "商汤"), rule("sense", "商汤"),
        rule("360", "三六零"), rule("qihang", "三六零"),
        rule("opencompass", "上海AI实验室"), rule("openss", "上海AI实验室")
    );

    private static final List<Rule> US_RULES = List.of(
        rule("openai", "OpenAI"), rule("gpt", "OpenAI"),
        rule("anthropic", "Anthropic"), rule("claude", "Anthropic"),
        rule("google", "Google"), rule("gemini", "Google"),
        rule("meta", "Meta"), rule("llama", "Meta"),
        rule("xai", "xAI"), rule("x-ai", "xAI"), rule("grok", "xAI"),
        rule("nvidia", "NVIDIA"),
        rule("microsoft", "微软"), rule("azure", "微软"), rule("phi", "微软"),
        rule("amazon", "Amazon"), rule("aws", "Amazon"),
        rule("cohere", "Cohere"), rule("together", "Together AI"),
        rule("cerebras", "Cerebras"), rule("liquid", "Liquid AI"),
        rule("ai21", "AI21"), rule("databricks", "Databricks"),
        rule("apple", "Apple"), rule("perplexity", "Perplexity"),
        rule("replicate", "Replicate"), rule("groq", "Groq"), rule("mistral", "Mistral AI")
    );

    /** 美国规则里的 Mistral 实际是法国公司，单独归入欧洲组。 */
    private static final List<Rule> EU_RULES = List.of(
        rule("mistral", "Mistral AI"),
        rule("hugging", "Hugging Face"), rule("huggingface", "Hugging Face"),
        rule("stability", "Stability AI"), rule("deepmind", "Google DeepMind"),
        rule("aleph", "Aleph Alpha"), rule("fugaku", "Fugaku-LLM")
    );

    private static final List<Rule> KR_RULES = List.of(
        rule("naver", "Naver"), rule("upstage", "Upstage"), rule("lg", "LG AI Research"),
        rule("kt-", "KT AI"), rule("hyperclova", "HyperCLOVA")
    );

    private ModelOriginResolver() {
    }

    /** 解析一行：provider 优先，模型名（整串或首段）兜底。 */
    public static OriginTag resolve(String provider, String model) {
        String providerKey = norm(provider);
        if (!providerKey.isBlank()) {
            OriginTag t = matchRules(providerKey);
            if (t != null) {
                return t;
            }
        }
        String modelKey = norm(model);
        if (!modelKey.isBlank()) {
            OriginTag t = matchRules(modelKey);
            if (t != null) {
                return t;
            }
            // 兜底：模型名第一段（HF 仓库 org 前缀，如 "deepseek-ai/…"、"meta-llama/…"）
            String first = modelKey.split("[/_\\s-]")[0];
            if (!first.isBlank() && !first.equals(modelKey)) {
                OriginTag p = matchRules(first);
                if (p != null) {
                    return p;
                }
            }
        }
        return new OriginTag("国际", provider == null ? null : provider);
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static OriginTag matchRules(String key) {
        for (Rule rule : CHINA_RULES) {
            if (key.contains(rule.marker)) {
                return new OriginTag("中国", rule.vendor);
            }
        }
        for (Rule rule : KR_RULES) {
            if (key.contains(rule.marker)) {
                return new OriginTag("韩国", rule.vendor);
            }
        }
        for (Rule rule : EU_RULES) {
            if (key.contains(rule.marker)) {
                return new OriginTag("欧洲", rule.vendor);
            }
        }
        for (Rule rule : US_RULES) {
            if (key.contains(rule.marker)) {
                return new OriginTag("美国", rule.vendor);
            }
        }
        return null;
    }

    private static Rule rule(String marker, String vendor) {
        return new Rule(marker, vendor);
    }

    private record Rule(String marker, String vendor) {
    }
}