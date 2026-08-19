package com.minimall.ai.memory;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 长期记忆服务: 跨会话记住/回忆某个用户的稳定偏好。
 * <p>
 * 本质 = 对“用户事实”做 RAG, 复用 bge-m3 向量化 + 专用的 Redis 向量库(longTermMemoryStore)。
 * 与政策知识库物理隔离(不同 index), 且每条记忆带 userId 元数据, 检索时按 userId 过滤, 用户之间互不串。
 * <p>
 * 与短期记忆的分工:
 *   - 短期记忆(ChatMemory): 本次对话的最近若干轮消息, 在内存, 会话/重启即失 (单实例够用)。
 *   - 长期记忆(本类): 跨会话的稳定偏好, 落 Redis 向量库, 长期留存。
 */
@Slf4j
@Service
public class LongTermMemoryService {

    /** 一次回忆最多召回几条用户事实 (太多会挤占上下文) */
    private static final int MAX_RECALL = 3;
    /** 相似度阈值: 低于此分数视为不相关, 丢弃, 避免召回无关记忆干扰 LLM */
    private static final double MIN_SCORE = 0.6;
    /** 元数据里标识“这条记忆属于谁”的 key */
    private static final String KEY_USER = "userId";
    /** 没有任何记忆时给出的占位文本 (注入 prompt, 让模型知道“暂无”) */
    private static final String EMPTY = "（暂无）";

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> memoryStore;

    /**
     * 注意 @Qualifier: AI 模块有两个 EmbeddingStore(政策库 + 记忆库), 这里显式指定用记忆库,
     * 否则按类型注入会歧义。政策库已标 @Primary, 只有本处需要显式点名记忆库。
     */
    public LongTermMemoryService(EmbeddingModel embeddingModel,
                                 @Qualifier("longTermMemoryStore") EmbeddingStore<TextSegment> memoryStore) {
        this.embeddingModel = embeddingModel;
        this.memoryStore = memoryStore;
    }

    /**
     * 记住一条用户事实。
     * @param userId 记给谁
     * @param fact   一句稳定偏好, 如“用户不吃辣”“用户偏好白色、M 码”
     */
    public void remember(String userId, String fact) {
        if (isBlank(userId) || isBlank(fact)) {
            return;
        }
        // 事实文本 + userId 元数据 打包成一个 TextSegment
        TextSegment segment = TextSegment.from(fact, Metadata.from(KEY_USER, userId));
        // 向量化(bge-m3) 后连同原文一起存进记忆向量库
        Embedding embedding = embeddingModel.embed(segment).content();
        memoryStore.add(embedding, segment);
        log.info("[ltm] 记住用户事实 userId={}, fact={}", userId, fact);
    }

    /**
     * 回忆: 用当前问题去该用户的记忆里做语义检索, 返回拼好的记忆文本(供注入 prompt)。
     * @param userId 回忆谁的
     * @param query  当前用户问题 (作为检索的“查询向量”)
     * @return 用“；”拼接的相关记忆; 无则返回“（暂无）”
     */
    public String recall(String userId, String query) {
        if (isBlank(userId) || isBlank(query)) {
            return EMPTY;
        }
        // 1. 问题向量化
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        // 2. 只在“这个 userId 的记忆”里检索 (元数据过滤, 保证用户间隔离)
        Filter userFilter = metadataKey(KEY_USER).isEqualTo(userId);
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(MAX_RECALL)
                .minScore(MIN_SCORE)
                .filter(userFilter)
                .build();
        // 3. 取出命中的原文事实
        EmbeddingSearchResult<TextSegment> result = memoryStore.search(request);
        List<String> facts = result.matches().stream()
                .map(EmbeddingMatch::embedded)
                .map(TextSegment::text)
                .collect(Collectors.toList());
        return facts.isEmpty() ? EMPTY : String.join("；", facts);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
