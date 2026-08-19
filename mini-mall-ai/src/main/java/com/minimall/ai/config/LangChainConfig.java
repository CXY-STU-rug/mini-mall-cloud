package com.minimall.ai.config;

import com.minimall.ai.agent.ShoppingAssistant;
import com.minimall.ai.memory.MemoryTool;
import com.minimall.ai.tool.ProductQueryTool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * 把 AI 能力装配成 Spring Bean。
 * 前三个是"基础设施"(Task2), 后两个是"agent 编排"(Task4)。
 */
@Configuration
public class LangChainConfig {

    // ═══════ Task2: 三个基础设施 ═══════

    /** ① 生成模型 = DeepSeek (兼容 OpenAI 协议) */
    @Bean
    public ChatLanguageModel chatModel(AiProperties p) {
        return OpenAiChatModel.builder()
                .baseUrl(p.getDeepseek().getBaseUrl())
                .apiKey(p.getDeepseek().getApiKey())
                .modelName(p.getDeepseek().getModel())
                .build();
    }

    /** ② 向量化模型 = 本地 bge-m3 (Ollama) */
    @Bean
    public EmbeddingModel embeddingModel(AiProperties p) {
        return OllamaEmbeddingModel.builder()
                .baseUrl(p.getOllama().getBaseUrl())
                .modelName(p.getOllama().getEmbeddingModel())
                .build();
    }

    /**
     * ③ 政策知识向量库 = Redis Stack (RAG 用)。
     *    @Primary: 本模块现在有两个 EmbeddingStore(政策库 + 长期记忆库), 按类型注入时默认用这个,
     *    只有长期记忆服务显式 @Qualifier 点名记忆库。
     */
    @Bean
    @Primary
    public EmbeddingStore<TextSegment> embeddingStore(AiProperties p) {
        return RedisEmbeddingStore.builder()
                .host(p.getRedis().getHost())
                .port(p.getRedis().getPort())
                .dimension(p.getRedis().getDimension())
                .build();
    }

    /**
     * ③b 长期记忆专用向量库 = Redis Stack。
     *    与政策库【物理隔离】: 不同 indexName + 不同 prefix, 检索时不会互相串。
     *    metadataKeys(userId): 让 Redis 为 userId 建元数据索引, 从而支持按 userId 过滤(用户间隔离)。
     */
    @Bean("longTermMemoryStore")
    public EmbeddingStore<TextSegment> longTermMemoryStore(AiProperties p) {
        return RedisEmbeddingStore.builder()
                .host(p.getRedis().getHost())
                .port(p.getRedis().getPort())
                .dimension(p.getRedis().getDimension())
                .indexName("ltm-user-memory")
                .prefix("ltm:")
                .metadataKeys(List.of("userId"))
                .build();
    }

    // ═══════ Task4: agent 编排 ═══════

    /**
     * ④ RAG 检索器: 封装"问题→向量→去向量库检索相关片段"。
     *    maxResults(3) = 最多召回3个最相似片段;
     *    minScore(0.6) = 相似度低于0.6的丢弃(避免召回不相关内容干扰LLM)。
     */
    @Bean
    public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> store,
                                             EmbeddingModel embeddingModel) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.6)
                .build();
    }

    /**
     * ⑤ ⭐ 购物助手 agent 本体 (AiServices 动态生成 ShoppingAssistant 接口的实现)。
     *    .chatLanguageModel  绑定生成模型(DeepSeek)
     *    .contentRetriever   绑定RAG检索器(问答前自动检索知识, 塞进prompt) —— 这就是RAG接进来的地方
     *    .chatMemoryProvider 每个 memoryId(userId) 一份记忆, 存最近10条消息(多轮对话)
     *    .tools              挂上 function-calling 工具, DeepSeek 自动决定何时调用查商品
     */
    @Bean
    public ShoppingAssistant shoppingAssistant(ChatLanguageModel chatModel,
                                               ContentRetriever contentRetriever,
                                               ProductQueryTool productQueryTool,
                                               MemoryTool memoryTool) {
        return AiServices.builder(ShoppingAssistant.class)
                .chatLanguageModel(chatModel)
                .contentRetriever(contentRetriever)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                // 两个工具: 查商品 + 写长期记忆; 模型按需自行决定调哪个
                .tools(productQueryTool, memoryTool)
                .build();
    }
}
