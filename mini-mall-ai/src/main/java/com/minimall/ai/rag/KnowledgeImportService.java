package com.minimall.ai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动时把 knowledge/*.md 政策文档灌入向量库。
 * implements ApplicationRunner: Spring 启动完成后自动执行一次 run()。
 * 注: 第一期每次启动都灌一遍(重复数据暂不去重), 后续可优化。
 */
@Slf4j
@Component
public class KnowledgeImportService implements ApplicationRunner {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public KnowledgeImportService(EmbeddingModel embeddingModel,
                                  EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 读 classpath:knowledge/ 下所有 .md (Spring 读资源样板, 我给你)
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:knowledge/*.md");

        List<TextSegment> segments = new ArrayList<>();
        for (Resource r : resources) {
            String text = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Document doc = Document.from(text);

            // 切片: recursive(每片≤300字, 相邻重叠30字)造切片器 → .split(doc)切这篇 → addAll累积
            segments.addAll(DocumentSplitters.recursive(300, 30).split(doc));
        }

        // 批量转向量: embedAll 返回 Response<List<Embedding>>, .content() 取出真列表
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        // 存: 向量列表 + 片段列表 一一对应(第i个向量↔第i个片段), 一起存进 Redis
        embeddingStore.addAll(embeddings, segments);

        log.info("知识库灌入完成: {} 个片段", segments.size());
    }
}