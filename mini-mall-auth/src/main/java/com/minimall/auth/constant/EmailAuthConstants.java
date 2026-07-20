package com.minimall.auth.constant;

/**
 * 邮箱验证码登录相关常量。
 *
 * 从 EmailAuthController 抽出, 供"发码"(EmailAuthController)与"验码"(EmailCodeLoginStrategy)
 * 共用, 避免两处各写一份 Redis key 前缀导致拼错。
 */
public final class EmailAuthConstants {

    private EmailAuthConstants() {
    }

    /** 验证码本体, TTL 5 分钟 */
    public static final String CODE_PREFIX = "auth:email:code:";

    /** 60 秒重发限制 (存在=刚发过) */
    public static final String LIMIT_PREFIX = "auth:email:limit:";

    /** 验错计数, 连错 MAX_ERR_COUNT 次作废验证码 */
    public static final String ERR_PREFIX = "auth:email:err:";

    /** 验证码有效期 (分钟) */
    public static final long CODE_TTL_MINUTES = 5;

    /** 重发间隔 (秒) */
    public static final long RESEND_LIMIT_SECONDS = 60;

    /** 最多验错次数 */
    public static final int MAX_ERR_COUNT = 5;
}
