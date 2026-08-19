package com.minimall.gateway.config;

import com.minimall.gateway.security.JwtReactiveAuthenticationManager;
import com.minimall.gateway.security.JwtServerAuthenticationConverter;
import com.minimall.gateway.security.RbacReactiveAuthorizationManager;
import com.minimall.gateway.security.WhitelistMatcher;
import com.minimall.gateway.util.GatewayResponseWriter;         // 网关统一响应写出工具
import com.minimall.common.core.domain.Result;                 // 全站统一返回体
import com.fasterxml.jackson.databind.ObjectMapper;            // 传给工具做序列化
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

/**
 * 网关的【响应式 Spring Security】总配置。
 *
 * 这是阶段二的核心: 把原来手写在 AuthGlobalFilter 里的"取 token→验签→黑名单→授权→注入头"整套,
 * 改成由 Spring Security 的标准组件接管:
 *   · 认证(是谁)      —— AuthenticationWebFilter + Converter + AuthenticationManager
 *   · 授权(能不能访问) —— authorizeExchange + WhitelistMatcher(前缀树) + RBAC AuthorizationManager
 *   · 身份透传         —— HeaderInjectionWebFilter(另一个类, 读安全上下文注入头)
 *
 * @EnableWebFluxSecurity: 启用 WebFlux(响应式)版安全。网关是 Netty/WebFlux, 用的是
 *   ServerHttpSecurity / SecurityWebFilterChain, 而不是业务服务那套 Servlet 的 HttpSecurity。
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtServerAuthenticationConverter jwtConverter;
    private final JwtReactiveAuthenticationManager jwtAuthManager;
    private final RbacReactiveAuthorizationManager rbacManager;
    private final WhitelistMatcher whitelistMatcher;

    /** 序列化 Result 用; ObjectMapper 线程安全, 全类共享一个即可 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecurityConfig(JwtServerAuthenticationConverter jwtConverter,
                          JwtReactiveAuthenticationManager jwtAuthManager,
                          RbacReactiveAuthorizationManager rbacManager,
                          WhitelistMatcher whitelistMatcher) {
        this.jwtConverter = jwtConverter;
        this.jwtAuthManager = jwtAuthManager;
        this.rbacManager = rbacManager;
        this.whitelistMatcher = whitelistMatcher;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

        // —— 组装 JWT 认证过滤器: 用我们的 Manager 验签, 用我们的 Converter 抽 token ——
        AuthenticationWebFilter jwtAuthFilter = new AuthenticationWebFilter(jwtAuthManager);
        jwtAuthFilter.setServerAuthenticationConverter(jwtConverter);
        // 认证失败(坏 token / 黑名单)→ 直接回 401, 不继续
        jwtAuthFilter.setAuthenticationFailureHandler((webFilterExchange, ex) ->
                writeError(webFilterExchange.getExchange().getResponse(),
                        HttpStatus.UNAUTHORIZED, "未认证或登录已失效"));

        http
                // 关 CSRF: 前后端分离 + JWT, 不用 Cookie-Session, CSRF 防护不适用
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // 关掉框架自带的 HTTP Basic / 表单登录 / 登出: 我们只认 JWT, 不要这些默认登录页
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                // 无状态: 不建 Session, 不存安全上下文, 每个请求都靠 token 现验(纯 JWT 网关)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())

                // —— 授权规则 ——
                .authorizeExchange(ex -> ex
                        // 白名单(前缀树匹配)→ 免鉴权放行
                        .matchers(whitelistMatcher).permitAll()
                        // 其余一切请求 → 交给 RBAC 管理器判定(internal/禁用/越权)
                        .anyExchange().access(rbacManager))

                // 把 JWT 认证过滤器插在标准"认证"位置
                .addFilterAt(jwtAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)

                // —— 异常出口: 决定 401 还是 403 ——
                .exceptionHandling(e -> e
                        // 未认证(没带/带了空 token 却访问受保护接口)→ 401
                        .authenticationEntryPoint((exchange, ex) ->
                                writeError(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "未认证或登录已失效"))
                        // 已认证但没权限(internal / 被禁用 / 越权写)→ 403
                        .accessDeniedHandler((exchange, ex) ->
                                writeError(exchange.getResponse(), HttpStatus.FORBIDDEN, "无权限访问该资源")));

        return http.build();
    }

    /**
     * 统一写出 401/403 错误响应(带 JSON body)。
     *
     * ⭐ 为什么必须写一段真实 body, 而不是只 setStatusCode + 空 setComplete():
     *   实测(与 G4 限流"裸200空响应"同款坑): 在 Spring 6.1 + Spring Cloud Gateway + Reactor Netty
     *   这套组合下, 走 accessDeniedHandler 的 403 出口如果只 setStatusCode(403)+setComplete()(空 body),
     *   会出现"响应头先按默认 200 刷出、空 body 写出被吞" → 客户端网线上收到裸 200 空响应
     *   (网关内部日志却显示 Completed 403, 极具迷惑性)。
     *   改用 response.writeWith(...) 真正写出一段字节, 状态码才能稳定落到网线上。
     *   顺带把 401/403 也做成结构化 JSON, 前端能拿到 code/message, 比空 body 体验好。
     */
    private Mono<Void> writeError(ServerHttpResponse response, HttpStatus status, String message) {
        // 复用网关统一响应工具: 401/403 与限流 429、路由失败 503 走同一处, 保证格式和写法完全一致
        return GatewayResponseWriter.writeJson(response, status, Result.error(status.value(), message), objectMapper);
    }
}
