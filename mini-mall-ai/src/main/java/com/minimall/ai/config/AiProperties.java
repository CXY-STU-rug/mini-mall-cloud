package com.minimall.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 把 application.yml 里 ai.* 配置读进 Java 对象。
 * @Data (lombok) 自动生成 getter/setter —— 绑定必须要 setter, 别漏。
 */
@Component
@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private DeepSeek deepseek = new DeepSeek();
    private Ollama ollama = new Ollama();
    private Redis redis = new Redis();

    // ── 示范: DeepSeek 段 (对应 yml ai.deepseek.*) ──
    @Data
    public static class DeepSeek {
        private String baseUrl;   // ai.deepseek.base-url
        private String apiKey;    // ai.deepseek.api-key
        private String model;     // ai.deepseek.model
    }

    // ── Ollama 段 (对应 yml ai.ollama.*) ──
    @Data
    public static class Ollama {
        private String baseUrl;         // ai.ollama.base-url
        private String embeddingModel;  // ai.ollama.embedding-model
    }

    // ── Redis 段 (对应 yml ai.redis.*) ──
    @Data
    public static class Redis {
        private String host;        // ai.redis.host
        private Integer port;       // ai.redis.port
        private Integer dimension;  // ai.redis.dimension
    }
}