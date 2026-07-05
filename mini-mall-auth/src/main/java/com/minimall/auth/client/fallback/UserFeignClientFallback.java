package com.minimall.auth.client.fallback;

import com.minimall.auth.client.UserFeignClient;
import com.minimall.auth.model.User;
import com.minimall.common.core.domain.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * UserFeignClient 降级
 *
 * 触发场景: user 服务挂 / 网络超时 / 触发 Sentinel 熔断.
 * 降级策略: 返 503, 让 auth 调用方知道是用户服务不可用 (区别于"用户不存在").
 */
@Component
public class UserFeignClientFallback implements UserFeignClient {

    private static final Logger log = LoggerFactory.getLogger(UserFeignClientFallback.class);

    @Override
    public Result<User> getByUsername(String username) {
        log.warn("[Feign-Fallback] (auth→user).getByUsername 降级 username={}", username);
        return Result.error(503, "用户服务暂不可用");
    }

    @Override
    public Result<User> getByEmail(String email) {
        log.warn("[Feign-Fallback] (auth→user).getByEmail 降级 email={}", email);
        return Result.error(503, "用户服务暂不可用");
    }

    @Override
    public Result<User> getByOauth(String provider, String oauthId) {
        log.warn("[Feign-Fallback] (auth→user).getByOauth 降级 provider={} oauthId={}", provider, oauthId);
        return Result.error(503, "用户服务暂不可用");
    }

    @Override
    public Result<User> createUser(User user) {
        log.warn("[Feign-Fallback] (auth→user).createUser 降级 username={}", user.getUsername());
        return Result.error(503, "用户服务暂不可用");
    }
}
