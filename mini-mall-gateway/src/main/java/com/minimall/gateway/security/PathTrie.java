package com.minimall.gateway.security;

import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 前缀树(Trie) —— 用来做网关白名单的"按路径段前缀匹配"。
 *
 * ⭐ 为什么不用原来的 List + path.startsWith():
 *   原来 whitelist.stream().anyMatch(path -> path.startsWith("/auth")) 有【越界匹配 bug】:
 *   "/authxxx"、"/auth-admin" 这种也会被 startsWith("/auth") 判成 true, 等于把不该放的路径也放行了。
 *   前缀树按【路径段(以 "/" 切分)】逐段走, "/auth" 只能匹配 "/auth" 和 "/auth/**",
 *   匹配不到 "/authxxx"(因为段名是 "authxxx" ≠ "auth"), 从根上消除了越界 bug。
 *
 * 匹配复杂度: O(路径段数), 与白名单条数无关; 白名单越多, 相比线性扫描优势越大。
 */
public class PathTrie {

    /**
     * 树节点。
     *   children  —— 子节点, key = 路径段名(如 "auth"、"product")
     *   terminal  —— 该节点是否是某条白名单规则的"终点"(即到这里就构成一条完整前缀)
     *   methods   —— 该前缀允许的 HTTP 方法集合; null 表示不限方法(任意方法都放行)
     */
    private static class Node {
        Map<String, Node> children = new HashMap<>();
        boolean terminal = false;
        Set<HttpMethod> methods;
    }

    /** 树根(不代表任何段, 只是入口) */
    private final Node root = new Node();

    /**
     * 注册一条白名单前缀。
     * @param prefix  形如 "/auth"、"/search/product"
     * @param methods 允许的方法集合; 传 null = 不限方法
     */
    public void insert(String prefix, Set<HttpMethod> methods) {
        Node cur = root;
        // 把 "/search/product" 切成 ["search","product"], 过滤掉切分产生的空串
        for (String seg : prefix.split("/")) {
            if (seg.isEmpty()) {
                continue;   // 开头的 "/" 会切出一个空串, 跳过
            }
            // 没有这个子节点就新建一个, 有就复用 —— 逐段往下建树
            cur = cur.children.computeIfAbsent(seg, k -> new Node());
        }
        // 走到前缀最后一段, 打上"终点"标记, 并记住允许的方法
        cur.terminal = true;
        cur.methods = methods;
    }

    /**
     * 判断某个请求路径 + 方法是否命中白名单。
     *
     * 算法(逐段下行, 一旦踩到某个"终点"节点就算前缀命中):
     *   for 每个路径段:
     *      ① 若当前节点已经是终点 → 说明有更短的前缀已经覆盖了本路径 → 命中(再校方法)
     *      ② 否则往下走一段; 走不动(没有对应子节点)→ 不命中
     *   段走完后, 若停在的节点是终点 → 精确命中(再校方法)
     */
    public boolean matches(String path, HttpMethod method) {
        Node cur = root;
        for (String seg : path.split("/")) {
            if (seg.isEmpty()) {
                continue;   // 同样跳过开头 "/" 切出的空串
            }
            // ① 当前节点已是某前缀终点 → 前缀命中(如注册了 "/auth", 现在路径是 "/auth/login")
            if (cur.terminal) {
                return methodAllowed(cur, method);
            }
            // ② 继续下行一段; 没有这个子段 → 白名单里没有能覆盖它的前缀 → 不命中
            Node next = cur.children.get(seg);
            if (next == null) {
                return false;
            }
            cur = next;
        }
        // 段全部走完, 恰好停在一个终点上 = 精确命中(如注册 "/auth", 路径就是 "/auth")
        return cur.terminal && methodAllowed(cur, method);
    }

    /** 方法校验: 规则没限定方法(null)就任意放行, 否则要求方法在允许集合内 */
    private boolean methodAllowed(Node node, HttpMethod method) {
        return node.methods == null || node.methods.contains(method);
    }
}
