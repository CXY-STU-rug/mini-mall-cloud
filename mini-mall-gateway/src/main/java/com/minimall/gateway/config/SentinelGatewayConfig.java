package com.minimall.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.domain.Result;
import com.minimall.gateway.util.GatewayResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

/**
 * Sentinel Gateway 自定义降级响应 (F2.7, G4 修正)
 * <p>
 * 重要认知(G4 实测修正):
 *   SCA 2023.0.1.2 的 SentinelSCGAutoConfiguration 号称自动注册了
 *   SentinelGatewayBlockExceptionHandler, 但在当前 Boot 3.3.5 组合下【接不住】
 *   限流异常 —— GatewayFlowRule 内部转成参数流控, 抛 ParamFlowException,
 *   一路飘到 HttpWebHandlerAdapter 变成 500/空响应 (G4 联调时实测抓到)。
 * <p>
 * 所以这里做两层防护:
 *   ① sentinelBlockJsonHandler(): 自己注册【最高优先级 WebExceptionHandler】,
 *      认整个 BlockException 家族(Flow/ParamFlow/Degrade...), 直接写 429 JSON —— 真正生效的是它
 *   ② initBlockHandlers(): 保留原 BlockRequestHandler 注册, 若未来版本修好了
 *      自动装配, 走那条路输出的也是同样的 JSON
 */
@Configuration
public class SentinelGatewayConfig {

    /** 序列化限流 JSON 用; ObjectMapper 线程安全, 全类共享一个 */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * G4: 兜住限流异常的真正防线。
     * <p>
     * WebExceptionHandler 是 WebFlux 的异常处理扩展点, 所有 filter 链里抛的异常都会流经它。
     * @Order(HIGHEST_PRECEDENCE) 保证排在框架默认处理器(那个只会回 500 的)之前。
     * 不是限流异常就 Mono.error(ex) 原样往后传, 不抢别人的活。
     */
    /**
     * G4 核心修复: 用带 order 的 SentinelGatewayFilter 顶掉自动装配的那个。
     * <p>
     * 自动装配的过滤器 order=HIGHEST_PRECEDENCE(链头), 会和 AuthGlobalFilter 的
     * 401 提交产生订阅竞态: 限流触发时 cancel 腰斩半截 doCommit → 客户端收裸200。
     * yml 的 spring.cloud.sentinel.filter.order 只对 MVC 版生效, 网关版不读(实测),
     * 只能靠 @ConditionalOnMissingBean 机制自己注册。
     * order=0: 鉴权(-100)之后、路由(10000+)之前 —— 下游没人在订阅期提交响应。
     */
    @Bean
    public com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter sentinelGatewayFilter() {
        return new com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter(0);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public WebExceptionHandler sentinelBlockJsonHandler() {
        return (exchange, ex) -> {
            // 只认 Sentinel 的 BlockException 家族(含 ParamFlowException), 其他异常放行给下游处理器
            if (!BlockException.isBlockException(ex)) {
                return Mono.error(ex);
            }
            // 复用网关统一响应工具: 限流 429 与 Security 的 401/403、路由失败 503 走同一处, 结构完全一致
            Result<Void> body = Result.error(429,
                    "网关限流: 请求太频繁, 请稍后再试 (" + ex.getClass().getSimpleName() + ")");
            // 注意不做 isCommitted 防御 —— G4 实测限流 cancel 会留下"假已提交"状态,
            // 检查它反而放弃了本可成功的写出; 真写失败时 onErrorResume 把原异常还给默认处理器
            return GatewayResponseWriter.writeJson(exchange.getResponse(), HttpStatus.TOO_MANY_REQUESTS, body, mapper)
                    .onErrorResume(writeEx -> Mono.error(ex));
        };
    }

    /**
     * @PostConstruct: Spring 把 Bean 创建好后, 自动调这个方法
     * <p>
     * G4 关键改动: 这里不再返回 429 响应体, 而是把 BlockException【原样往外抛】。
     * 原因(实测): adapter 拿到 ServerResponse 后自己调 writeTo() 写响应,
     * 该 API 在 Spring 6.1 下有兼容坑 —— 头先刷成默认 200、body 写失败、异常照飘,
     * 客户端就收到"裸 200 空响应"。
     * 改成 Mono.error 后写出动作完全不发生, 响应保持未提交,
     * 异常飘到上面注册的 sentinelBlockJsonHandler() 统一写 429 JSON。
     */
    @PostConstruct
    public void initBlockHandlers() {
        // 不写响应, 只把限流异常传递出去, 交给 WebExceptionHandler 兜底
        BlockRequestHandler blockRequestHandler = (exchange, throwable) -> Mono.error(throwable);

        // 注册到 Gateway 专用回调管理器
        // ⚠️ 注意是 GatewayCallbackManager 不是 WebFluxCallbackManager
        //    前者是 Spring Cloud Gateway 用的(基于 RouteId 限流)
        //    后者是普通 WebFlux 用的(基于 @SentinelResource)
        GatewayCallbackManager.setBlockHandler(blockRequestHandler);
    }
}
