package com.minimall.gateway.security;

import com.minimall.common.security.util.JwtUtil;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 响应式认证管理器 —— 真正"验 token"的地方。
 *
 * 拿到 Converter 抽出的裸 token, 依次做:
 *   ① 验签 + 解析: 拿 userId / role; 签名错或过期 → BadCredentialsException(认证失败)
 *   ② 登出黑名单: token 虽合法但已被登出/改密拉黑 → BadCredentialsException
 * 全部通过 → 返回"已认证" Authentication: principal=userId, 权限= ROLE_ADMIN / ROLE_USER。
 *
 * ⭐ 认证失败(本类抛的异常)最终会走 EntryPoint → 【401】。
 *    这正好对应旧代码里"坏 token / 黑名单"返回 401 的语义。
 *    而"账号被禁用 / 越权"是【403】, 放到授权层(RbacReactiveAuthorizationManager)处理, 不在这里。
 */
@Component
public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate reactiveRedis;

    /** 登出黑名单 key 前缀(auth 服务登出时写入, 这里读, 必须一致) */
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    public JwtReactiveAuthenticationManager(JwtUtil jwtUtil,
                                            ReactiveStringRedisTemplate reactiveRedis) {
        this.jwtUtil = jwtUtil;
        this.reactiveRedis = reactiveRedis;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        // Converter 用两参构造器塞进来的裸 token(principal 与 credentials 都是它)
        String token = (String) authentication.getCredentials();

        // ① 验签 + 解析。放 fromCallable 里: 解析是同步 CPU 操作, 抛异常时用 onErrorMap 统一转成认证异常
        return Mono.fromCallable(() -> {
                    Long userId = jwtUtil.getUserIdFromToken(token);   // 验签失败/过期会在这抛异常
                    Byte role = jwtUtil.getRoleFromToken(token);       // 一并取角色, 用于后续 RBAC
                    return new long[]{userId, role == null ? 0 : role};
                })
                // 任何解析异常统一转成 BadCredentialsException → 最终 401
                .onErrorMap(e -> new BadCredentialsException("无效的 token", e))
                // ② 黑名单校验(响应式查 Redis)
                .flatMap(arr -> {
                    long userId = arr[0];
                    byte role = (byte) arr[1];
                    return reactiveRedis.hasKey(BLACKLIST_PREFIX + token)
                            // ⭐ fail-open: Redis 挂了当作"没命中", 不让黑名单故障拖垮整条鉴权主干道
                            .onErrorReturn(false)
                            .defaultIfEmpty(false)
                            .flatMap(inBlacklist -> {
                                if (Boolean.TRUE.equals(inBlacklist)) {
                                    // 已登出/失效的 token → 认证失败 → 401
                                    return Mono.error(new BadCredentialsException("token 已失效, 请重新登录"));
                                }
                                // 通过 → 组装"已认证" Authentication
                                return Mono.just(buildAuthentication(userId, role));
                            });
                });
    }

    /**
     * 组装已认证的 Authentication:
     *   principal   = userId(Long)   —— 供后面的请求头注入器写 X-User-Id
     *   authorities = ROLE_ADMIN / ROLE_USER —— 供授权层做 hasRole 判断, 也用来还原 X-User-Role
     * 用三参构造器(带 authorities)会把 authenticated 直接置 true。
     */
    private Authentication buildAuthentication(long userId, byte role) {
        // role==1 是管理员, 其余按普通用户。Spring Security 约定角色权限带 "ROLE_" 前缀
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(role == 1 ? "ROLE_ADMIN" : "ROLE_USER"));
        // credentials 传 null: 认证完就丢掉 token, 不再随对象传递
        return new UsernamePasswordAuthenticationToken(userId, null, authorities);
    }
}
