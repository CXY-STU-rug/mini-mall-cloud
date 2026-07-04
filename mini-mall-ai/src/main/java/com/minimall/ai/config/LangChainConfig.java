package com.minimall.ai.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把三个"外部 AI 能力"装配成 Spring Bean。
 *
 * 核心思想(面向接口): 方法返回类型是【接口】(ChatLanguageModel/EmbeddingModel/EmbeddingStore),
 *   具体实现是【某厂商的类】(OpenAi/Ollama/Redis)。业务代码只认接口, 换厂商只改这里。
 *
 * 方法参数 AiProperties 是 Spring 自动注入的(上一步 @ConfigurationProperties 那个 Bean),
 *   从里面取 yml 配的地址/key。
 */
@Configuration
public class LangChainConfig {

    /**
     * ① 生成模型 = DeepSeek。
     * DeepSeek 兼容 OpenAI 协议, 所以用 OpenAiChatModel, 只是把 baseUrl 指到 DeepSeek 网关。
     */
    @Bean
    public ChatLanguageModel chatModel(AiProperties p) {
        return OpenAiChatModel.builder()
                .baseUrl(p.getDeepseek().getBaseUrl())   // https://api.deepseek.com
                .apiKey(p.getDeepseek().getApiKey())
                .modelName(p.getDeepseek().getModel())   // deepseek-chat
                .build();
    }

    /**
     * ② 向量化模型 = 本地 bge-m3(经 Ollama)。
     * 负责把文本转成 1024 维向量, RAG 的"文本→向量"这一环。
     */
    @Bean
    public EmbeddingModel embeddingModel(AiProperties p) {
        return OllamaEmbeddingModel.builder()
                .baseUrl(p.getOllama().getBaseUrl())         // http://localhost:11434
                .modelName(p.getOllama().getEmbeddingModel()) // bge-m3
                .build();
    }

    /**
     * ③ 向量库 = Redis Stack。
     * 泛型 <TextSegment>: 存的是"文本片段+它的向量"。dimension 必须和 bge-m3 输出一致(1024),
     *   否则建索引维度对不上, 存取会报错。
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(AiProperties p) {
        return RedisEmbeddingStore.builder()
                .host(p.getRedis().getHost())        // localhost
                .port(p.getRedis().getPort())        // 6380
                .dimension(p.getRedis().getDimension()) // 1024
                .build();
    }
}
