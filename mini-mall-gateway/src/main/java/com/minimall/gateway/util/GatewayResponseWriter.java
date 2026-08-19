package com.minimall.gateway.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.domain.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关统一响应写出工具。
 *
 * 为什么需要它:
 *   网关的各类"框架出口"(Security 的 401/403、Sentinel 的 429、路由失败的 503)都【不是 Controller】,
 *   背后没有 HttpMessageConverter 自动把返回对象序列化成 JSON。只能自己把 Result 序列化成字节、
 *   再用最底层的 response.writeWith 写进响应体
 *   (这条链上用 ServerResponse.bodyValue 那套高层封装有坑, 会"头先刷成默认200、body 写空" → 裸200)。
 *   抽到一处, 保证所有出口【格式一致(都是 Result) + 写法一致(都写真实 body, 状态码才落得到网线)】。
 */
public final class GatewayResponseWriter {

    private GatewayResponseWriter() {}   // 纯工具类, 不允许实例化

    /**
     * 把 Result 以 JSON 写出, 同时把 HTTP 状态码设成 status。
     *
     * @param resp   响应对象
     * @param status HTTP 状态码(401/403/429/503...)
     * @param body   全站统一返回体 Result
     * @param mapper Jackson 序列化器(调用方共享一个即可, 线程安全)
     */
    public static Mono<Void> writeJson(ServerHttpResponse resp, HttpStatus status,
                                       Result<?> body, ObjectMapper mapper) {
        resp.setStatusCode(status);                                      // 设 HTTP 状态码
        resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);    // 声明返回 JSON
        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(body);                      // Result → JSON 字节
        } catch (Exception e) {
            // 序列化兜底: 极端情况下退回手拼, 但仍必须写出"真实 body", 否则状态码又会被吞成裸200
            bytes = ("{\"code\":" + status.value() + ",\"message\":\"error\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        // writeWith + bufferFactory().wrap: 最底层把字节怼进响应体, 绕开有坑的高层封装(这层最稳)
        return resp.writeWith(Mono.just(resp.bufferFactory().wrap(bytes)));
    }
}
