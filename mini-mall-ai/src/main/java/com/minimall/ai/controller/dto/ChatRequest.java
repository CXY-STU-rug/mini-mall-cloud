package com.minimall.ai.controller.dto;

import lombok.Data;

/**
 * AI 对话请求体。
 * 前端 POST /ai/chat 时 body 里只带一句用户说的话;
 * userId 不放这里 —— 它由网关校验 JWT 后从 X-User-Id 请求头注入, 前端伪造不了。
 */
@Data
public class ChatRequest {
    private String message;
}
