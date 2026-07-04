package com.minimall.ai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 连通性测试: 用 Java 侧真跑一遍 embedding + 向量库, 验证 Task2 三个 Bean 能工作。
 * @SpringBootTest = 起完整 Spring 容器(会装配三个 Bean, 也会注册到 Nacos)。
 * 需要 Ollama(11434) + Redis Stack(6380) + Nacos(8848) 在线。
 */
@SpringBootTest
class InfraConnectivityTest {

    @Autowired
    EmbeddingModel embeddingModel;      // bge-m3 (Ollama)

    @Autowired
    EmbeddingStore<TextSegment> store;  // Redis Stack

    /**
     * 断言一: bge-m3 出的向量维度 = 1024。
     * 验证 Ollama embedding Bean 通, 且维度和我们配的 dimension 一致。
     */
    @Test
    void embed_returns_1024_dim() {
        Embedding e = embeddingModel.embed("怎么退货").content();
        assertThat(e.dimension()).isEqualTo(1024);
    }

    /**
     * 断言二: 存一句退货政策 → 用"如何申请退货"去检索 → 能召回那句。
     * 这验证 RAG 的命脉——【语义检索】: 问题和知识文字用词不同("怎么退货" vs 存的原句),
     *   但向量语义相近, 所以能召回。这就是为什么 RAG 比关键词搜索强。
     */
    @Test
    void store_add_and_search() {
        // 存: 一句退货政策 → 转向量 → 存进 Redis
        TextSegment seg = TextSegment.from("退货需在签收后7天内申请，商品需保持完好。");
        store.add(embeddingModel.embed(seg).content(), seg);

        // 查: 用另一种问法转向量, 去 Redis 找最相似的 1 条
        Embedding query = embeddingModel.embed("我想申请退货怎么办").content();
        List<EmbeddingMatch<TextSegment>> matches = store.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(query)
                        .maxResults(1)
                        .build()
        ).matches();

        // 应该召回、且召回的正是那句退货政策
        assertThat(matches).isNotEmpty();
        assertThat(matches.get(0).embedded().text()).contains("退货");
    }
}
