package com.minimall.gateway.security;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 身份透传过滤器 —— 把网关认证出来的身份, 以 HTTP 头注入给下游业务服务。
 *
 * 为什么需要它:
 *   下游 user/order/product 等服务【不再自己解 JWT】(那是网关的活)。它们只信任网关注入的
 *   X-User-Id / X-User-Role 头来识别"当前是谁、什么角色"。这就是"身份在网关一次解析、往下透传"。
 *
 * 执行时机(顺序很关键):
 *   本过滤器 order = -50, 晚于 Spring Security 的过滤器链(order=-100)。
 *   所以运行到这里时, Spring Security 已经把认证结果写进了 Reactor 上下文,
 *   这里用 ReactiveSecurityContextHolder 就能取到, 再 mutate 请求塞头, 继续转发给下游。
 *
 * 白名单请求(没有认证信息)则原样放行, 不注入任何头。
 */
@Component
public class HeaderInjectionWebFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                // 只给"已认证"的请求注入头
                .filter(Authentication::isAuthenticated)
                .flatMap(auth -> {
                    Long userId = (Long) auth.getPrincipal();          // 认证管理器里放的就是 userId
                    String role = hasAdmin(auth) ? "1" : "0";          // 从权限还原回旧的 0/1 角色码
                    // 注入 X-User-Id / X-User-Role 头, 替换掉原请求继续往下走。
                    //   坑同 RequestLogFilter: 请求已穿过 Security 的 WebFilter 链, header 是"双层只读",
                    //   exchange.getRequest().mutate().header() 会抛 UnsupportedOperationException。
                    //   所以用 ServerHttpRequestDecorator 覆写 getHeaders() 返回全新可写副本, 绕开只读解包。
                    ServerHttpRequest mutated = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public HttpHeaders getHeaders() {
                            HttpHeaders headers = new HttpHeaders();   // 全新可写 HttpHeaders
                            headers.addAll(super.getHeaders());        // 拷入原有 header
                            headers.set("X-User-Id", String.valueOf(userId));   // 注入用户 id
                            headers.set("X-User-Role", role);                   // 注入角色码
                            return headers;
                        }
                    };
                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                // 没有认证信息(白名单/匿名请求)→ 不注入, 原样放行
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange)));
    }

    /** 当前认证是否具备管理员角色 */
    private boolean hasAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /**
     * 顺序 -50: 必须 > Spring Security 的 -100(即晚于安全链执行),
     * 才能读到安全链写好的认证上下文; 同时又早于网关的路由转发, 保证头能带到下游。
     */
    @Override
    public int getOrder() {
        return -50;
    }
}
