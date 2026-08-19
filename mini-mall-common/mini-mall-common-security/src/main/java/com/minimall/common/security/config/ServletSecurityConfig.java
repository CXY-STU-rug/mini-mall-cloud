package com.minimall.common.security.config;

import com.minimall.common.security.filter.HeaderAuthenticationFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 业务服务通用 Servlet Spring Security 配置(阶段三 · 纵深防御)。
 *
 * 放在 common-security 里, 所有业务服务(user/product/order/payment/review/search/ai/file)
 * 只要依赖 common-security 就【零改动】自动获得:
 *   ① HeaderAuthenticationFilter: 把网关注入的 X-User-Id/X-User-Role 还原成 Authentication
 *   ② /admin/** 二次校验: 即使有人绕过网关直连业务服务, 管理接口仍要求 ROLE_ADMIN
 *   ③ @EnableMethodSecurity: 之后可在任意方法上加 @PreAuthorize("hasRole('ADMIN')") 做更细的授权
 *
 * ⭐ 三个条件注解保证"只在该生效的地方生效", 不误伤别的模块:
 *   @ConditionalOnWebApplication(SERVLET) —— 只在 Servlet 应用装配。
 *        gateway 是 WebFlux(响应式), 条件不满足 → 本类根本不加载, 不会和网关的响应式安全冲突。
 *   @ConditionalOnClass(name=...)          —— classpath 有 Servlet 版 SecurityFilterChain 才装配。
 *        用【字符串】形式让 Spring 走 ASM 字节码扫描, 缺类时直接跳过、不触发类加载(防 NoClassDefFoundError)。
 *   @ConditionalOnMissingBean(下面 @Bean 上) —— 应用若已自定义 SecurityFilterChain 就退让。
 *        auth 服务有自己的 SecurityConfig(OAuth2 登录那套), 这里会自动退让, 不覆盖它。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.security.web.SecurityFilterChain")
@EnableWebSecurity
@EnableMethodSecurity
public class ServletSecurityConfig {

    /**
     * 通用安全过滤器链。
     * @ConditionalOnMissingBean(SecurityFilterChain.class): 只有当应用【没有】自己的
     *   SecurityFilterChain 时才注册本条(auth 有自己的 → 本条自动不生效)。
     *   自动配置晚于用户配置加载, 所以判断时 auth 的链已存在, 退让判定准确。
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain commonSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                // 关 CSRF: 前后端分离 + 无状态, 不依赖 Cookie-Session
                .csrf(csrf -> csrf.disable())
                // 无状态: 业务服务不建 Session, 身份每次都从网关注入的头里现取
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 授权规则(第二道防线):
                //   /admin/** 必须是管理员; 其余一律放行 —— 因为粗粒度鉴权已在网关做完,
                //   业务服务这层默认信任网关, 只对最敏感的管理接口再兜一道底。
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                // 把"读头还原身份"的过滤器插在用户名密码认证过滤器之前, 保证授权判断前身份已就绪
                .addFilterBefore(new HeaderAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class)
                // 关掉框架自带登录方式: 我们只认网关注入的头, 不需要 Basic / 表单登录页
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }
}
