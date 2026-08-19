package com.minimall.gateway.security;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 响应式授权管理器 —— "已认证之后, 到底能不能访问这个接口"的判定。
 *
 * 挂在 .authorizeExchange().anyExchange().access(本类) 上, 对所有【非白名单】请求生效。
 * 判定三类拒绝(都返回 403):
 *   ① internal 内部接口: 只允许 Feign 服务间直连, 外部经网关一律拒绝
 *   ② 账号被管理员禁用: 即使拿着有效期内的旧 token 也进不来(禁用即时生效)
 *   ③ 越权写操作: 默认拒绝模型 —— 除 C 端本人白名单外的一切写操作都要求管理员
 *
 * ⭐ 状态码: 本类返回 deny 时,
 *      - 请求【已认证】→ Spring Security 交给 AccessDeniedHandler → 403
 *      - 请求【未认证】(没带 token 的受保护请求)→ 交给 EntryPoint → 401
 *    正好复刻旧代码"未登录 401 / 越权 403"的行为。
 */
@Component
public class RbacReactiveAuthorizationManager
        implements ReactiveAuthorizationManager<AuthorizationContext> {

    private final ReactiveStringRedisTemplate reactiveRedis;

    /** 用户禁用标记前缀(user 服务禁用时写, 网关这里读) */
    private static final String USER_DISABLED_PREFIX = "user:disabled:";

    public RbacReactiveAuthorizationManager(ReactiveStringRedisTemplate reactiveRedis) {
        this.reactiveRedis = reactiveRedis;
    }

    @Override
    public Mono<AuthorizationDecision> check(Mono<Authentication> authentication,
                                             AuthorizationContext context) {
        String path = context.getExchange().getRequest().getURI().getPath();
        HttpMethod method = context.getExchange().getRequest().getMethod();

        // ① internal 接口: 最高优先级, 直接拒绝(外部绝不能经网关调内部接口)
        if (isInternal(path)) {
            return Mono.just(new AuthorizationDecision(false));
        }

        return authentication
                // 只处理"已认证"的; 未认证的会被下面 defaultIfEmpty 兜成 deny → 401
                .filter(Authentication::isAuthenticated)
                .flatMap(auth -> {
                    Long userId = (Long) auth.getPrincipal();
                    boolean admin = hasAdmin(auth);
                    // ② 禁用检查(响应式查 Redis, 同样 fail-open)
                    return reactiveRedis.hasKey(USER_DISABLED_PREFIX + userId)
                            .onErrorReturn(false)
                            .defaultIfEmpty(false)
                            .map(disabled -> {
                                if (Boolean.TRUE.equals(disabled)) {
                                    return new AuthorizationDecision(false);   // 被禁用 → 403
                                }
                                // ③ 越权检查: 需要 admin 但当前不是 → 拒绝
                                if (needAdmin(path, method) && !admin) {
                                    return new AuthorizationDecision(false);   // 越权 → 403
                                }
                                return new AuthorizationDecision(true);        // 放行
                            });
                })
                // 没有认证信息(受保护接口却没带有效 token)→ 拒绝 → 401
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    /** 当前认证是否具备管理员角色 */
    private boolean hasAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /**
     * 是否内部接口(只允许 Feign 服务间调用, 外部经网关一律 403)。
     * 判定: 路径中出现 /internal 段。覆盖 /user/internal、/coupon/internal/use 等。
     */
    private boolean isInternal(String path) {
        return path.contains("/internal/") || path.endsWith("/internal");
    }

    /**
     * 该请求是否"仅管理员(role=1)可访问"。命中两类之一就要求管理员:
     *   ① /admin/** —— 后台专用接口
     *   ② 管理资源的写操作(POST/PUT/DELETE/PATCH) —— GET 已在白名单放行给 C 端浏览,
     *      但增删改只有管理员能做, 否则普通用户能改任意商品、扣任意库存。
     */
    private boolean needAdmin(String path, HttpMethod method) {
        // 后台接口: 一律要管理员
        if (path.startsWith("/admin/")) {
            return true;
        }
        // 只拦写操作; 读(GET/HEAD)不拦, 让 C 端能浏览商品/分类/评价
        boolean isWrite = method == HttpMethod.POST
                || method == HttpMethod.PUT
                || method == HttpMethod.DELETE
                || method == HttpMethod.PATCH;
        if (!isWrite) {
            return false;
        }
        // ⭐ 默认拒绝模型: 除 C 端本人白名单里的写操作外, 其余一切写操作都要求管理员。
        //    好处: 以后新增任何写接口忘了配 = 自动要 admin, 只会"功能报 403 被立刻发现",
        //          不会"悄悄留一个谁都能调的安全漏洞"。
        if (isCEndWrite(path)) {
            return false;   // 命中 C 端白名单 → 本人 token 就能写, 不需要 admin
        }
        return true;        // 其余所有写操作 → 必须 admin
    }

    /**
     * C 端(普通用户本人)允许的写操作白名单。
     * 命中 = 用本人 token 就能写(操作的是自己的资源); 没命中的写操作一律要 admin。
     * 注意: internal 接口已在最前面被拦死, 这里不用管。
     */
    private boolean isCEndWrite(String path) {
        // —— 用户自己的资源: 购物车 / 收藏 / 订单 / 评价 / 收货地址 / 个人资料 ——
        if (path.startsWith("/cart")) return true;
        if (path.startsWith("/favorite")) return true;
        if (path.startsWith("/order")) return true;
        if (path.startsWith("/review")) return true;
        if (path.startsWith("/user/address")) return true;
        if (path.equals("/user/me")) return true;
        if (path.equals("/file/upload")) return true;
        // —— 混合前缀: 前缀下既有 C 端又有 admin, 必须精确区分 ——
        if (path.startsWith("/coupon/") && path.endsWith("/receive")) return true;   // 领券是 C 端, 建券是 admin
        if (path.startsWith("/seckill/") && !path.startsWith("/seckill/activity")) return true;  // 抢购 C 端, 发布活动 admin
        if (path.startsWith("/pay")) return true;      // 支付创建等 C 端(/pay/notify 已在白名单)
        if (path.startsWith("/refund")) return true;   // 退款申请等 C 端(/refund/notify 已在白名单)
        if (path.startsWith("/ai")) return true;       // AI 客服对话是 C 端登录用户操作
        return false;
    }
}
