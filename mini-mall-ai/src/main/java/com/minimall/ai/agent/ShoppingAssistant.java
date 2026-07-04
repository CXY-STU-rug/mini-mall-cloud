package com.minimall.ai.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

/**
 * 购物助手 agent 接口 —— LangChain4j 的 AiServices 精华。
 *
 * 你只【声明】这个接口, LangChain4j 在运行时【动态生成实现】,
 * 自动把 LLM + RAG检索器 + 记忆 编排起来 —— 不用写实现类!
 *
 * 方法参数注解:
 *   @UserMessage 标记哪个参数是"用户说的话"(会连同RAG检索结果一起送给LLM)
 *   @MemoryId    标记按谁隔离对话记忆(同一 userId 的多轮对话能记住上下文)
 */
public interface ShoppingAssistant {

    String chat(@MemoryId String userId, @UserMessage String message);
}
