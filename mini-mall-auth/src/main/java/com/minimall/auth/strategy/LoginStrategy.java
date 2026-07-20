package com.minimall.auth.strategy;

import com.minimall.auth.dto.LoginRequest;
import com.minimall.auth.enums.LoginType;
import com.minimall.auth.model.User;

/**
 * 登录策略接口 (策略模式核心)。
 *
 * 每种登录方式实现一个策略, 只负责"如何验证身份"这一件变化的事:
 *   验证凭证 → 返回一个已认证通过的 User。
 *
 * 不负责的部分 (各方式完全一致, 由 LoginService 统一处理):
 *   禁用账号拦截、签发 JWT、清除密码、组装 AuthResponse。
 */
public interface LoginStrategy {

    /** 本策略支持的登录方式, 供工厂路由 */
    LoginType getType();

    /**
     * 验证凭证并返回认证通过的用户。
     *
     * 用户不存在时按各方式自身规则处理: 密码登录直接报错; 邮箱验证码登录自动注册。
     * 验证失败应抛 BusinessException。
     */
    User authenticate(LoginRequest request);
}
