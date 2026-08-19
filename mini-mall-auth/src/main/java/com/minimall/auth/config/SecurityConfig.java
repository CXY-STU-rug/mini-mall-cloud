package com.minimall.auth.config;

import com.minimall.auth.security.OAuth2LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 总配置（auth 服务）。
 *
 * 引入 spring-boot-starter-security 后，Spring Security 会【默认给所有接口加上认证】，
 * 不写这个配置的话，/auth/login 都会被自带的 HTTP Basic 拦住返 401。
 * 所以这个类是必需的：由它来定义"哪些放行、会话怎么管、OAuth2 怎么登、密码怎么加密"。
 *
 * 三个注解：
 *   @Configuration        —— 这是一个配置类，里面的 @Bean 会进容器
 *   @EnableWebSecurity    —— 启用 Web 安全（引 starter 后默认就开，显式写更清楚）
 *   @EnableMethodSecurity —— 启用方法级授权，之后就能在方法上写 @PreAuthorize("hasRole('ADMIN')")
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    public SecurityConfig(OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler) {
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
    }

    /**
     * 密码加密器。注册成 Bean 后：
     *   ① DaoAuthenticationProvider 自动用它比对登录密码；
     *   ② 注册 / 找回密码时也用它加密。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 把 AuthenticationManager 暴露成 Bean，让 AuthController.login 能注入它来触发认证。
     * 容器里已经有 UserDetailsService(FeignUserDetailsService) + PasswordEncoder，
     * Spring Boot 会据此自动装配一个 DaoAuthenticationProvider，这个 Manager 背后用的就是它。
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 安全过滤器链：一条链定义"请求进来怎么处理"。
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 关 CSRF：前后端分离 + JWT，不依赖 Cookie-Session，CSRF 防护不适用
                .csrf(csrf -> csrf.disable())

                // 授权规则：本服务的登录/注册/找回/OAuth/文档端点全部放行（真正的入口鉴权在网关做），
                //           其余请求要求已认证。因为 auth 服务对外只暴露这些端点，等于全放行也安全。
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/**",                        // 本地登录/注册/登出/找回/邮件验证码
                                "/auth/oauth2/authorization/**",   // 下面把 OAuth2 发起端点挪到 /auth 前缀
                                "/auth/login/oauth2/code/**",      // GitHub 回调端点（也在 /auth 前缀下）
                                "/doc.html", "/webjars/**", "/v3/api-docs/**"   // 接口文档
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                // ⭐ OAuth2 登录：授权码流程全交给框架。我们只做两件事：
                //    ① 把默认端点挪到 /auth 前缀下 —— 这样网关现有的 /auth 路由和白名单直接生效，不用改网关
                //       · 发起地址：/auth/oauth2/authorization/github
                //       · 回调地址：/auth/login/oauth2/code/github（要跟 GitHub App 里填的 callback 一致）
                //    ② 指定成功后的处理器：签 JWT + 跳前端（见 OAuth2LoginSuccessHandler）
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(a -> a.baseUri("/auth/oauth2/authorization"))
                        .redirectionEndpoint(r -> r.baseUri("/auth/login/oauth2/code/*"))
                        .successHandler(oAuth2LoginSuccessHandler)
                );

        // 注意：这里【没有】设 SessionCreationPolicy.STATELESS。
        // 因为 OAuth2 授权码流程需要在"跳去 GitHub"和"GitHub 回调"之间，用 Session 临时存一下 state 参数（防 CSRF）。
        // 我们的接口鉴权靠 JWT，不依赖 Session，这个 Session 只在 OAuth 跳转那一小段用一下，无副作用。
        return http.build();
    }
}
