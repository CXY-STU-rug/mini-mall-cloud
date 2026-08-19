package com.minimall.auth.service;

import com.minimall.auth.vo.AuthResponse;
import com.minimall.auth.dto.LoginRequest;
import com.minimall.auth.model.User;
import com.minimall.auth.strategy.LoginStrategy;
import com.minimall.auth.strategy.LoginStrategyFactory;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.security.util.JwtUtil;
import org.springframework.stereotype.Service;

/**
 * 登录统一收口。
 *
 * 职责划分:
 *   "怎么验证身份" (变化的部分) → 委托给具体 LoginStrategy
 *   "验证通过后干什么" (不变的部分) → 本类统一处理: 禁用拦截 → 签 JWT → 清密码 → 返回
 *
 * 这样三种 (及未来 N 种) 登录方式共用同一条尾巴, 不再各抄一遍。
 */
@Service
public class LoginService {

    private final LoginStrategyFactory strategyFactory;
    private final JwtUtil jwtUtil;

    public LoginService(LoginStrategyFactory strategyFactory, JwtUtil jwtUtil) {
        this.strategyFactory = strategyFactory;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse login(LoginRequest request) {
        // ① 按登录方式取策略, 执行各自的身份验证 (变化的部分)
        LoginStrategy strategy = strategyFactory.getStrategy(request.getLoginType());
        User user = strategy.authenticate(request);

        // ② 以下为所有登录方式共用的尾巴 (不变的部分) ──────────────

        // 禁用账号拦截: 放在验证之后, 不给探测"哪些账号被禁"的机会
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用, 请联系管理员");
        }

        // 签自家 JWT, 返回结构对所有登录方式一致, 前端不用区分登录来源
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        user.setPassword(null);   // 兜底: 密文绝不出网关
        return new AuthResponse(token, user);
    }
}
