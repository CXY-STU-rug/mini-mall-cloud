package com.minimall.gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * 白名单匹配器 —— 把"前缀树 PathTrie"接进 Spring Security 的授权 DSL。
 *
 * Spring Security 里 .authorizeExchange().matchers(白名单).permitAll() 需要一个
 * ServerWebExchangeMatcher: 给它一个请求, 它回答"匹不匹配"。
 * 我们让它内部用 PathTrie 来判断, 匹配到就 permitAll(免鉴权放行)。
 *
 * ⭐ 这些前缀原样搬自旧 AuthGlobalFilter 的 WHITE_LIST, 语义不变, 只是匹配算法从
 *    "线性 startsWith" 升级成了"前缀树逐段匹配"(顺带修掉 /authxxx 越界 bug)。
 */
@Component
public class WhitelistMatcher implements ServerWebExchangeMatcher {

    /** 网关级白名单前缀树: 应用启动时建一次, 之后只读, 线程安全 */
    private final PathTrie trie = new PathTrie();

    public WhitelistMatcher() {
        // —— 登录/注册/登出/找回/OAuth 全在 /auth 前缀下, 不限方法 ——
        trie.insert("/auth", null);
        // —— C 端可匿名浏览的只读接口: 只放行 GET ——
        trie.insert("/category", Set.of(HttpMethod.GET));
        trie.insert("/product", Set.of(HttpMethod.GET));
        trie.insert("/search/product", Set.of(HttpMethod.GET));
        trie.insert("/review/product", Set.of(HttpMethod.GET));
        trie.insert("/coupon/available", Set.of(HttpMethod.GET));
        // —— 支付宝异步回调: 由支付宝服务器发起, 没有 JWT, 必须免鉴权(安全靠回调里 RSA2 验签) ——
        trie.insert("/pay/notify", null);
        trie.insert("/refund/notify", null);
        // —— Knife4j 聚合文档静态资源: 没有 JWT, 不放行则 doc.html 打不开 ——
        trie.insert("/doc.html", null);
        trie.insert("/webjars", null);
        trie.insert("/v3/api-docs", null);
        trie.insert("/swagger-resources", null);
        trie.insert("/favicon.ico", null);
    }

    /**
     * Spring Security 每来一个请求都会调它。命中前缀树 → match()(→ permitAll),
     * 不命中 → notMatch()(→ 落到 anyExchange().access(RBAC) 走鉴权+授权)。
     */
    @Override
    public Mono<MatchResult> matches(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();
        return trie.matches(path, method) ? MatchResult.match() : MatchResult.notMatch();
    }
}
