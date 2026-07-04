package com.minimall.ai.controller;

import com.minimall.ai.agent.ShoppingAssistant;
import com.minimall.ai.controller.dto.ChatRequest;
import com.minimall.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 客服对话 Controller。
 * <p>
 * 端点:
 *   POST /ai/chat  用户发一句话, 返回 AI 回复 (自动走 RAG 知识库 + 商品查询工具 + 多轮记忆)
 * <p>
 * 网关侧(Task7 要配): /ai/chat 是 C 端用户操作 → 进 isCEndWrite 白名单(需登录),
 *   网关校验 JWT 后注入 X-User-Id, 这里直接读头, 拿它当对话记忆的隔离键。
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiChatController {

    // 注入 Task4 那个 AiServices 动态生成的购物助手 agent
    private final ShoppingAssistant shoppingAssistant;

    /**
     * 和 AI 客服对话。
     * @param userId 网关注入的用户 ID (兼做 memoryId); 本地未接网关时默认 guest 方便测试
     * @param req    请求体, 只含用户说的话
     */
    @PostMapping("/chat")
    public Result<String> chat(@RequestHeader(value = "X-User-Id", defaultValue = "guest") String userId,
                               @RequestBody ChatRequest req) {
        // 把 userId(记忆隔离) + 用户消息交给 agent, 它内部自动编排 LLM/RAG/工具/记忆
        String answer = shoppingAssistant.chat(userId, req.getMessage());
        return Result.success(answer);
    }
}
