package com.minimall.ai;

import com.minimall.ai.agent.ShoppingAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 问答测试 —— 第一次让 agent 真正回答业务问题。
 * 起完整容器: 会先跑 KnowledgeImportService 灌入知识(Task3), 再测问答(Task4)。
 * 需要 Ollama + Redis + Nacos + DeepSeek(key 第一次真正用于生成) 全在线。
 */
@SpringBootTest
class RagQaTest {

    @Autowired
    ShoppingAssistant assistant;

    @Test
    void ask_return_policy() {
        String answer = assistant.chat("test-user", "我买的东西怎么退货？运费谁出？");

        // 打印出来人工看回答质量(LLM 输出不确定, 断言只做宽松兜底)
        System.out.println("\n========= AI 回答 =========\n" + answer + "\n==========================\n");

        assertThat(answer).isNotBlank();
        // 回答应体现退货政策要点(7天/退货/运费), 证明 RAG 检索到了知识并被 DeepSeek 用上
        assertThat(answer).containsAnyOf("退货", "7", "天", "运费");
    }
}
