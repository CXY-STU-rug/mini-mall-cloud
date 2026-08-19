package com.minimall.ai.memory;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 长期记忆写入工具 (function-calling)。
 * <p>
 * 由大模型在对话中自行判断: 用户透露了值得长期记住的偏好时, 调用本工具把它存进长期记忆。
 * userId 不由模型提供(模型也拿不到), 而是从 {@link UserContext}(ThreadLocal) 取当前对话用户。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryTool {

    private final LongTermMemoryService longTermMemoryService;

    @Tool("当用户透露了值得长期记住的个人偏好或稳定信息时调用, 例如口味、尺码、颜色偏好、关注的商品类别、称呼等。"
            + "只记稳定、跨会话有用的偏好; 一次性的临时问题、闲聊不要记。参数 fact 用一句简洁的第三人称陈述, 如“用户不吃辣”。")
    public void rememberAboutUser(String fact) {
        String userId = UserContext.get();
        if (userId == null || userId.isBlank()) {
            // 理论上不会发生(Service 层已 set), 兜底防止把记忆错记到匿名用户
            log.warn("[ltm] rememberAboutUser 缺少当前用户上下文, 跳过 fact={}", fact);
            return;
        }
        longTermMemoryService.remember(userId, fact);
    }
}
