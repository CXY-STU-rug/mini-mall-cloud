package com.minimall.auth.client;

import com.minimall.auth.client.fallback.UserFeignClientFallback;
import com.minimall.auth.model.User;
import com.minimall.common.core.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * User 服务的 Feign 客户端 (auth → user 跨服务调用)
 *
 * 3 个 internal 端点 (对应 user 服务的 UserInternalController):
 *   ① getByUsername - 本地登录时按用户名查 (返 User 带 password 密文)
 *   ② getByOauth    - OAuth 回调时按 (provider, oauthId) 查
 *   ③ createUser    - 注册 / OAuth 第一次登录时建账号
 *
 * 设计约定:
 *   - 查不到时 user 服务返 Result.success(null) (不是 404)
 *   - 网络挂 / user 服务挂时, 走 fallback 返 Result.error(503)
 */
@FeignClient(
        name = "mini-mall-user",
        fallback = UserFeignClientFallback.class
)
public interface UserFeignClient {

    /**
     * 按用户名查 (本地登录用)
     *
     * @return Result.success(user) 找到; Result.success(null) 找不到
     */
    @GetMapping("/user/internal/by-username/{username}")
    Result<User> getByUsername(@PathVariable("username") String username);

    /**
     * 按邮箱查 (EMAIL-1 邮箱验证码登录用)
     *
     * @return 约定同 byUsername: 找到返 user, 找不到返 success(null)
     */
    @GetMapping("/user/internal/by-email/{email}")
    Result<User> getByEmail(@PathVariable("email") String email);

    /**
     * 按 OAuth (provider, oauthId) 查 (OAuth 回调用)
     */
    @GetMapping("/user/internal/by-oauth/{provider}/{oauthId}")
    Result<User> getByOauth(@PathVariable("provider") String provider,
                            @PathVariable("oauthId") String oauthId);

    /**
     * 创建用户 (本地注册 / OAuth 首次登录)
     *
     * @param user 完整字段, 含 BCrypt password (本地) 或 oauthProvider+oauthId (OAuth)
     * @return Result.success(user 含数据库回填的 id)
     */
    @PostMapping("/user/internal")
    Result<User> createUser(@RequestBody User user);
}
