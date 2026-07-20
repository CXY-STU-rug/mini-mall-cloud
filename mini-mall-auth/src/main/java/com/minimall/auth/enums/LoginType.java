package com.minimall.auth.enums;

/**
 * 登录方式枚举。
 *
 * 新增一种登录方式时: 这里加一个枚举值 + 写一个对应的 LoginStrategy 实现类即可,
 * 无需改动已有登录逻辑 (开闭原则)。
 */
public enum LoginType {

    /** 账号 + 密码 */
    PASSWORD,

    /** 邮箱 + 验证码 */
    EMAIL_CODE;

    // 未来可扩展: SMS_CODE(短信验证码) / WECHAT(微信) / PHONE_PASSWORD(手机号密码) ...
}
