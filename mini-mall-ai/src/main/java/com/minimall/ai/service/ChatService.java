package com.minimall.ai.service;

import com.minimall.ai.agent.ShoppingAssistant;
import com.minimall.ai.memory.LongTermMemoryService;
import com.minimall.ai.memory.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 对话编排服务: 在 agent 之外套一层, 负责“长期记忆”的回忆与写入上下文的准备。
 * <p>
 * 一次对话的完整链路:
 *   ① 用当前问题回忆该用户的长期记忆 (recall)
 *   ② 把 userId 放进 ThreadLocal, 供 @Tool rememberAboutUser 在本次同步调用链里取用
 *   ③ 调 agent: 短期记忆(userId) + 长期记忆(memory 注入 system) + 政策 RAG + 商品/记忆工具 一起编排
 *   ④ finally 清理 ThreadLocal, 防止线程复用把 userId 串给下一个请求
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ShoppingAssistant shoppingAssistant;
    private final LongTermMemoryService longTermMemoryService;

    public String chat(String userId, String message) {
        // ① 回忆: 按当前问题语义检索该用户的长期记忆(按 userId 隔离)
        String memory = longTermMemoryService.recall(userId, message);
        // ② 让本次调用链里的记忆写入工具能拿到 userId
        UserContext.set(userId);
        try {
            // ③ 交给 agent, memory 会注入到 system 提示(见 ShoppingAssistant 的 @SystemMessage)
            return shoppingAssistant.chat(userId, memory, message);
        } finally {
            // ④ 无论成功失败都清理, 这是 ThreadLocal 的铁律
            UserContext.clear();
        }
    }
}
