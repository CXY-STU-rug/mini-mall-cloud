package com.minimall.payment.client;

import com.minimall.common.core.domain.Result;
import com.minimall.payment.client.dto.OrderInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * Order 服务 Feign 客户端 (payment 侧)。
 * <p>
 * 调 order 的 GET /order/{orderId} 拿订单金额+状态。
 * ⭐ 关键: 走 Feign 直连(Nacos), 不过网关, 所以不受 /internal 黑名单影响。
 *   FeignAuthInterceptor 会把当前用户的 X-User-Id 自动透传给 order,
 *   order 的 detail 接口会校验"这单是不是你的" —— 归属安全由 order 保证, payment 白嫖。
 */
@FeignClient(name = "mini-mall-order", fallback = OrderFeignClientFallback.class)
public interface OrderFeignClient {

    @GetMapping("/order/{orderId}")
    Result<OrderInfo> getOrder(@PathVariable("orderId") Long orderId);

    /**
     * 支付成功后, 通知 order 把订单标记为已付款 (走 order 的 internal 接口)。
     * @return data=true 表示本次真的把订单改成了已付款; false=订单已不是待付款(重复回调等)
     */
    @PutMapping("/order/internal/{orderId}/paid")
    Result<Boolean> markPaid(@PathVariable("orderId") Long orderId);

    /** 退款成功后, 通知 order 标记订单退款/关闭。 */
    @PutMapping("/order/internal/{orderId}/refunded")
    Result<Boolean> markRefunded(@PathVariable("orderId") Long orderId);
}
