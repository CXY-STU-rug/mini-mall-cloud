package com.minimall.auth.strategy;

import com.minimall.auth.client.UserFeignClient;
import com.minimall.auth.dto.LoginRequest;
import com.minimall.auth.enums.LoginType;
import com.minimall.auth.model.User;
import com.minimall.common.core.domain.Result;
import com.minimall.common.core.exception.BusinessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 账号密码登录策略。
 *
 * 逻辑来自原 AuthController.login: Feign 查用户 → BCrypt 比对密码。
 */
@Component
public class PasswordLoginStrategy implements LoginStrategy {

    /** BCrypt 加密器, 无状态线程安全, 全类共享一份 */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final UserFeignClient userFeignClient;

    public PasswordLoginStrategy(UserFeignClient userFeignClient) {
        this.userFeignClient = userFeignClient;
    }

    @Override
    public LoginType getType() {
        return LoginType.PASSWORD;
    }

    @Override
    public User authenticate(LoginRequest request) {
        // ① Feign 查用户 (含 BCrypt 密文)
        Result<User> resp = userFeignClient.getByUsername(request.getUsername());
        if (resp.getCode() != 200) {
            // user 服务挂了 (Fallback 返 503)
            throw new BusinessException(resp.getMessage());
        }
        User user = resp.getData();

        // ② 没查到 / 密码不对 - 防爆破不区分 "用户不存在" vs "密码错"
        if (user == null || !ENCODER.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        return user;
    }
}
