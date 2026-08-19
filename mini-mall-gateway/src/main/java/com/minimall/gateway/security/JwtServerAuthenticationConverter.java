package com.minimall.gateway.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 认证信息提取器 —— 从请求里"抽出凭证", 交给认证管理器去验。
 *
 * Spring Security 的 AuthenticationWebFilter 分两步:
 *   ① ServerAuthenticationConverter: 把请求里的凭证(这里是 Bearer token)抽成一个"未认证的 Authentication"
 *   ② ReactiveAuthenticationManager: 拿这个未认证对象去验签, 验过返回"已认证的 Authentication"
 * 本类负责第 ① 步。
 *
 * ⭐ 关键: 没带 Bearer token 时返回 Mono.empty()。
 *    这样 AuthenticationWebFilter 就【跳过认证、直接放行到授权层】——
 *    白名单请求(本就不带 token)因此能顺利走到 permitAll; 受保护请求则在授权层因"未认证"被判 401。
 */
@Component
public class JwtServerAuthenticationConverter implements ServerAuthenticationConverter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        // 从 Authorization 头取值
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        // 没有头 / 不是 Bearer 开头 → 返回空: 不在这里报错, 交给后面的授权层决定 401 还是放行
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Mono.empty();
        }
        // 去掉 "Bearer "(7 字符)拿到裸 token
        String token = header.substring(BEARER_PREFIX.length());
        // 用两参构造器包成"未认证"的 token 载体(principal=credentials=token, authenticated=false)
        // 仅仅是把 token 传给下一步的 AuthenticationManager, 真正验签在那里做
        return Mono.just(new UsernamePasswordAuthenticationToken(token, token));
    }
}
