package com.minimall.common.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 业务服务的"身份还原"过滤器(阶段三 · 纵深防御)。
 *
 * 背景: 网关(阶段二)已经验过 JWT, 并把身份写进了两个请求头:
 *   X-User-Id   —— 用户 id
 *   X-User-Role —— 角色码(网关约定: "1"=管理员, "0"=普通用户)
 * 业务服务【不再自己解 JWT】, 只需信任这两个头, 把它们【翻译成 Spring Security 的 Authentication】,
 * 之后 @PreAuthorize / hasRole('ADMIN') 这些标准鉴权注解才能工作。
 *
 * ⚠️ 注意区分两个同名类:
 *   - org.springframework.security.core.context.SecurityContextHolder  ← 本类用的, Spring Security 的
 *   - com.minimall.common.core.context.SecurityContextHolder          ← 项目自研的 ThreadLocal, HeaderInterceptor 用的
 * 两者并存不冲突: 老的继续给业务代码 getUserId() 用, 这个新的给框架的 @PreAuthorize 用。
 *
 * OncePerRequestFilter: Spring 提供的"保证一次请求只过一遍"的过滤器基类(避免 forward/include 时重复执行)。
 */
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    /** 跟网关注入时用的头名【完全一致】 */
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // ① 取网关注入的 userId; 没有说明请求没经过网关鉴权(或匿名接口)→ 不建立认证, 交给后续规则处理
        String userId = request.getHeader(HEADER_USER_ID);
        if (StringUtils.hasText(userId)) {
            // ② 取角色码, 翻译成 Spring Security 约定的角色权限(必须带 "ROLE_" 前缀, hasRole 会自动补这个前缀)
            String role = request.getHeader(HEADER_USER_ROLE);
            String authority = "1".equals(role) ? "ROLE_ADMIN" : "ROLE_USER";

            // ③ 组装"已认证"的 Authentication: principal=userId, 权限=上面的角色
            //    三参构造器会把 authenticated 直接置 true(因为身份已由网关验过, 这里只是还原)
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId, null, List.of(new SimpleGrantedAuthority(authority)));

            // ④ 塞进 Spring Security 当前线程上下文, 之后 @PreAuthorize / hasRole 才拿得到
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        // ⑤ 放行到下一个过滤器 / Controller。注意: 不在这里做拒绝, 拒绝交给授权规则(/admin/** 或 @PreAuthorize)
        //    SecurityContext 是 ThreadLocal, Spring Security 的框架过滤器会在请求结束时自动清理, 无需手动 remove
        chain.doFilter(request, response);
    }
}
