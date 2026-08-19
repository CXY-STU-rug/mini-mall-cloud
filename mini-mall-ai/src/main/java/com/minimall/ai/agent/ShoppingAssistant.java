package com.minimall.ai.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 购物助手 agent 接口 —— LangChain4j 的 AiServices 精华。
 *
 * 你只【声明】这个接口, LangChain4j 在运行时【动态生成实现】,
 * 自动把 LLM + RAG检索器 + 短期记忆 + 工具 编排起来 —— 不用写实现类!
 *
 * 方法参数注解:
 *   @SystemMessage 定义系统人设; 模板里的 {{memory}} 由 @V("memory") 参数填充(注入该用户的长期记忆)
 *   @UserMessage   标记哪个参数是"用户说的话"(会连同RAG检索结果一起送给LLM)
 *   @MemoryId      标记按谁隔离【短期】对话记忆(同一 userId 的多轮对话能记住上下文)
 *   @V("memory")   长期记忆文本, 由 ChatService 先 recall 出来再传入; 与短期记忆是两条独立机制
 */
public interface ShoppingAssistant {

    @SystemMessage(
            "你是电商平台的购物助手, 热情、简洁地帮用户挑选商品、解答售后政策。\n" +
            "下面是关于当前用户的长期记忆(可能为“（暂无）”), 回答时自然地参考, 不要生硬复述:\n" +
            "{{memory}}"
    )
    String chat(@MemoryId String userId, @V("memory") String memory, @UserMessage String message);
}
