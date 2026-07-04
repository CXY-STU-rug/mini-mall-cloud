package com.minimall.gateway.filter;

import com.minimall.common.security.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * 全局鉴权过滤器
 * <p>
 * 工作原理：
 *   ① 每个进入网关的请求，都会经过 filter() 方法
 *   ② 白名单内的（登录/注册）直接放行
 *   ③ 其他请求：从 Authorization header 拿 token，解析校验
 *   ④ 成功 → 把 userId 塞进 X-User-Id header 透传给下游
 *   ⑤ 失败 → 返 401 直接拦下
 *
 * 实现接口：
 *   GlobalFilter —— Spring Cloud Gateway 的全局过滤器接口
 *   Ordered      —— 控制过滤器执行顺序
 *
 * 跟单体 JwtInterceptor 的 90% 一样，10% 关键不同：
 *   - 返 Mono<Void>（响应式），不是 boolean
 *   - 用 ServerWebExchange 取/改请求，不是 HttpServletRequest
 *   - 透传方案：HTTP header（X-User-Id），不是 ThreadLocal
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtUtil jwtUtil;

    /** 登出黑名单查询 (reactive, 网关是 WebFlux); key 前缀跟 auth 写入时一致 */
    @Autowired
    private org.springframework.data.redis.core.ReactiveStringRedisTemplate reactiveRedis;

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    /** 用户禁用标记前缀 (user 服务禁用时写, 网关这里读) */
    private static final String USER_DISABLED_PREFIX = "user:disabled:";

    /**
     * 白名单：这些 path 不校验 token，直接放行
     * <p>
     * ⭐ TODO ①：你来填白名单数组
     * 提示：根据 D3 路线图，至少要放行登录和注册
     * 写法：List.of("/user/login", "/user/register")
     */
    private record WhitelistRule(String prefix, Set<HttpMethod> methods) {
    }

    private static final List<WhitelistRule> WHITE_LIST = List.of(
            new WhitelistRule("/auth", null),                              // 登录注册, 不限 method
            new WhitelistRule("/category", Set.of(HttpMethod.GET)),
            new WhitelistRule("/product", Set.of(HttpMethod.GET)),         // ← 新增
            new WhitelistRule("/search/product", Set.of(HttpMethod.GET)),  // ← 新增
            new WhitelistRule("/review/product", Set.of(HttpMethod.GET)),
            new WhitelistRule("/coupon/available", Set.of(HttpMethod.GET)),
            // ⭐ SEC-2 Step2: 支付宝异步回调由支付宝服务器发起, 没有 JWT, 必须完全免鉴权。
            //    method 传 null = 不限方法(支付宝回调是 POST)。安全不靠网关, 靠回调里的 RSA2 验签。
            new WhitelistRule("/pay/notify", null),
            new WhitelistRule("/refund/notify", null)

    );


    // 黑名单说明(SEC 阶段升级):
    //   原来只黑 /user/internal, 但 /coupon/internal/use、/product/**/internal/** 等
    //   其它服务的内部接口没被挡住, 任何登录用户都能经网关调到 → 内部接口外泄。
    //   现改为统一规则: 任何路径段含 /internal 的都算内部接口, 一律 403。见 isInternal()。
    //   为什么安全: 服务间真正的内部调用走 Feign + Nacos 直连(如 :9001), 根本不过网关, 不受影响。

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        // 拿到当前请求对象
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // ⭐ 黑名单先判(最高优先级): 任何 internal 接口绝不让外部经网关访问
        //    放最前面, 保证即使某 internal 接口也命中了下面的白名单/写规则, 也先被这里拦死。
        if (isInternal(path)) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        // ⭐ TODO ②：判断当前 path 是否在白名单
        //   如果在 → 直接放行（return chain.filter(exchange);）
        //   提示：用 WHITE_LIST.stream().anyMatch(path::startsWith)
        //         或者用一个 for 循环判断
        // 在白名单 → 不校验，直接走下游
        // [你的代码写这里]
        HttpMethod method = request.getMethod();
        boolean inWhiteList = WHITE_LIST.stream().anyMatch(r ->
                path.startsWith(r.prefix()) && (r.methods() == null || r.methods().contains(method))
        );
        if (inWhiteList) {
            return chain.filter(exchange);
        }

        // ⭐ TODO ③：从 header 取 Authorization
        //   提示：request.getHeaders().getFirst("Authorization")
        //   token 一般以 "Bearer " 开头，要去掉这 7 个字符
        //   如果 token 为 null 或不以 Bearer 开头 → 调 unauthorized(exchange) 返 401  String token = request.getHeaders().getFirst("Authorization");
        // 去掉 "Bearer " 前缀，注意有个空格 = 7 字符

        // [你的代码：null / Bearer 校验]

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        final String jwt = authHeader.substring(7);   // 去掉 "Bearer " 前缀(含空格=7字符)

        // 解析 token, 拿 userId + role; 验签/过期失败会抛异常 → 401
        Long userId;
        Byte role;
        try {
            userId = jwtUtil.getUserIdFromToken(jwt);
            role = jwtUtil.getRoleFromToken(jwt);   // ADMIN 阶段: 一并拿 role
        } catch (Exception e) {
            return unauthorized(exchange);
        }
        // lambda 里要用, 复制成 effectively final
        final Long uid = userId;
        final Byte r = role;

        // ⭐ 登出黑名单校验 (reactive 查 Redis):
        //   token 签名有效 ≠ 仍然有效。用户登出/改密/被禁后, token 会被 auth 写进黑名单,
        //   这里命中就 401, 实现"已发出的 token 也能被服务端强制失效"。
        //   放在验签之后: 先确认是合法 token 再查 Redis, 避免为垃圾请求白查一次 Redis。
        //   ⭐ onErrorReturn(false): Redis 挂了 hasKey 会抛异常, 若不兜住会一路冒成 500 → 整站瘫痪。
        //   这里选择 fail-open(降级放行): Redis 不可用时当作"没命中", 宁可暂时失去黑名单/禁用校验,
        //   也不让登录鉴权这条主干道被 Redis 拖垮。(禁用用户重新登录仍被 login 查库拦, 有兜底)
        return reactiveRedis.hasKey(BLACKLIST_PREFIX + jwt)
                .onErrorReturn(false)
                .defaultIfEmpty(false)
                .flatMap(inBlacklist -> {
                    if (Boolean.TRUE.equals(inBlacklist)) {
                        return unauthorized(exchange);   // 已登出/失效的 token
                    }
                    // ⭐ 禁用即时生效: 再查该用户是否被管理员禁用 (user 服务禁用时写的 key)。
                    //   命中就 403 → 被禁用户即使拿着有效期内的旧 token 也进不来, 无需等 7 天过期。
                    return reactiveRedis.hasKey(USER_DISABLED_PREFIX + uid)
                            .onErrorReturn(false)
                            .defaultIfEmpty(false)
                            .flatMap(disabled -> {
                                if (Boolean.TRUE.equals(disabled)) {
                                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                                    return exchange.getResponse().setComplete();
                                }
                                // ⭐ ADMIN: /admin/** 或管理资源写操作必须 role=1 (详见 needAdmin)
                                if (needAdmin(path, method)) {
                                    if (r == null || r.intValue() != 1) {
                                        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                                        return exchange.getResponse().setComplete();
                                    }
                                }
                                // 把 userId + role 塞 header 透传给下游
                                ServerHttpRequest mutated = request.mutate()
                                        .header("X-User-Id", String.valueOf(uid))
                                        .header("X-User-Role", r == null ? "0" : String.valueOf(r))
                                        .build();
                                return chain.filter(exchange.mutate().request(mutated).build());
                            });
                });
    }

    /**
     * 判断该请求是否"仅管理员(role=1)可访问"。
     * 命中两类之一就要求管理员:
     *   ① /admin/** —— 后台专用接口
     *   ② 管理资源的写操作(POST/PUT/DELETE) —— GET 已在白名单放行给 C 端浏览,
     *      但增/删/改只有管理员能做, 否则普通用户能改任意商品、扣任意库存。
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
        // ⭐ SEC 阶段核心改造: 从"枚举管理写接口"反转为"默认拒绝"。
        //    含义: 除了 C 端白名单里的写操作, 其余一切写操作都要求管理员。
        //    好处: 以后新增任何写接口(券/分类/搜索维护/未来的支付退款…), 忘了配 = 自动要 admin,
        //          只会"功能报 403 被立刻发现", 不会"悄悄留一个谁都能调的安全漏洞"。
        if (isCEndWrite(path)) {
            return false;   // 命中 C 端白名单 → 本人 token 就能写, 不需要 admin
        }
        return true;        // 其余所有写操作 → 必须 admin(默认拒绝)
    }

    /**
     * C 端(普通用户本人)允许的写操作白名单。
     * 命中 = 用本人 token 就能写(操作的是自己的资源); 没命中的写操作一律要 admin。
     * 分类依据见笔记「写接口分类台账」。注意 internal 接口已在最前面被黑名单拦死, 这里不用管。
     */
    private boolean isCEndWrite(String path) {
        // —— 用户自己的资源: 购物车 / 收藏 / 订单 / 评价 / 收货地址 / 个人资料 ——
        if (path.startsWith("/cart")) return true;             // 购物车增删改
        if (path.startsWith("/favorite")) return true;         // 收藏 / 取消收藏
        if (path.startsWith("/order")) return true;            // 下单/取消/支付/确认收货(注意 /admin/order 是另一前缀, 已在上面被 /admin/ 拦成 admin)
        if (path.startsWith("/review")) return true;           // 发表评价
        if (path.startsWith("/user/address")) return true;     // 收货地址增删改
        if (path.equals("/user/me")) return true;              // 改个人资料
        if (path.equals("/file/upload")) return true;          // 上传(文件类型/大小加固在 file 服务里做, 见 SEC 上传加固)
        // —— 混合前缀: 前缀下既有 C 端又有 admin, 必须精确区分 ——
        // 领券 /coupon/{id}/receive 是 C 端; 但 POST /coupon(建券)是 admin, 所以不能整体放行 /coupon
        if (path.startsWith("/coupon/") && path.endsWith("/receive")) return true;
        // 秒杀抢购 /seckill/{id}、支付 /seckill/pay/** 是 C 端; 但 /seckill/activity(发布活动)是 admin
        if (path.startsWith("/seckill/") && !path.startsWith("/seckill/activity")) return true;
        // ⭐ SEC-2 Step2: 支付/退款的用户写操作(create/apply 等) 是 C 端, 本人 token 即可。
        //    注意: /pay/notify、/refund/notify 已在 WHITE_LIST 提前放行(免鉴权), 走不到这里, 不冲突。
        if (path.startsWith("/pay")) return true;
        if (path.startsWith("/refund")) return true;
        // ⭐ AI 阶段: AI 客服对话是 C 端登录用户操作, 本人 token 即可(不需 admin)。
        //    没放进 WHITE_LIST 是故意的: 要让网关校验 JWT 并注入 X-User-Id, 否则对话记忆没法按用户隔离,
        //    且能挡住匿名请求白烧 DeepSeek 额度。
        if (path.startsWith("/ai")) return true;
        return false;
    }

    /**
     * 是否内部接口(只允许 Feign 服务间调用, 外部经网关一律 403)。
     * 判定: 路径中出现 /internal 段。覆盖 /user/internal、/coupon/internal/use、product 的 internal/refresh-rating 等。
     */
    private boolean isInternal(String path) {
        return path.contains("/internal/") || path.endsWith("/internal");
    }

    /**
     * 返回 401 未授权（公共方法，TODO 里多处复用）
     *
     * WebFlux 风格的"中断响应"：
     *   ① 设置 401 状态码
     *   ② 调 setComplete() 直接结束响应链
     *   ③ 不调 chain.filter() 就意味着请求到此为止，不会转发到下游
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    /**
     * Filter 执行顺序：数字越小越先执行
     *
     * 为啥要 -100？
     *   Gateway 内部有很多默认 Filter（路由匹配、负载均衡等）
     *   我们的鉴权要在所有路由处理【之前】跑
     *   -100 比绝大部分默认 Filter 都小，能确保最先执行
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
