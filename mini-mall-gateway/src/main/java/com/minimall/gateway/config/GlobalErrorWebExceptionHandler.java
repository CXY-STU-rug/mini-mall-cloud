package com.minimall.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.domain.Result;
import com.minimall.gateway.util.GatewayResponseWriter;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关全局错误兜底 —— 把"框架级错误"也统一成 Result JSON。
 *
 * 背景:
 *   401/403 是 Security 出口、429 是 Sentinel 出口, 都能自己控制格式(已统一成 Result)。
 *   但后端【无实例/连不上/超时】时, 是 Spring Cloud Gateway 框架默认的 DefaultErrorWebExceptionHandler
 *   吐出 Spring Boot 风格的 error JSON(带 timestamp/path/status...), 形状和全站 Result 不一致。
 *
 * 做法:
 *   注册一个 @Order 更靠前的 ErrorWebExceptionHandler, 顶在默认处理器(order = -1)前面,
 *   把 503/404/其它未捕获异常统一改写成 {code,message,data}, 复用 GatewayResponseWriter 写出。
 *
 * @Order(-2): 比默认的 DefaultErrorWebExceptionHandler(-1)更靠前, 先由本类处理;
 *   Sentinel 的 BlockException 由更靠前的 sentinelBlockJsonHandler(HIGHEST_PRECEDENCE)先接走, 不会落到这。
 */
@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    /** 序列化 Result 用; ObjectMapper 线程安全, 共享一个 */
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 响应已被前面的处理器提交过, 就别抢了, 把异常继续抛给后面的处理器
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        // 从异常推断 HTTP 状态码: 网关无实例抛的 NotFoundException 是 ResponseStatusException 子类(带 503),
        // 能取到就用它带的状态码; 取不到(其它未捕获异常)按 500 处理。
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus resolved = HttpStatus.resolve(rse.getStatusCode().value());
            if (resolved != null) {
                status = resolved;
            }
        }
        // 统一写 Result JSON, 与 401/403/429 同款(同一个工具、同一个 {code,message,data} 结构)
        Result<Void> body = Result.error(status.value(), reason(status));
        return GatewayResponseWriter.writeJson(exchange.getResponse(), status, body, mapper);
    }

    /** 给几个常见状态码一句人话文案, 其余回退到状态码标准短语 */
    private String reason(HttpStatus status) {
        return switch (status) {
            case SERVICE_UNAVAILABLE -> "后端服务暂不可用, 请稍后再试";   // 503: 无实例/熔断
            case NOT_FOUND           -> "请求的资源不存在";              // 404
            case GATEWAY_TIMEOUT     -> "后端服务响应超时";              // 504
            default                  -> status.getReasonPhrase();       // 其余用英文标准短语
        };
    }
}
