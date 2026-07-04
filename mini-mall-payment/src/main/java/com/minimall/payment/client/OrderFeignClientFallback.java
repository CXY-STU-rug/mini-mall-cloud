package com.minimall.payment.client;

import com.minimall.common.core.domain.Result;
import com.minimall.payment.client.dto.OrderInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OrderFeignClient 降级: order 服务不通时返 error。
 * 支付强依赖订单信息, 拿不到订单就不能建支付单 → 上层看到 code!=200 直接报错, 绝不"盲付"。
 */
@Component
@Slf4j
public class OrderFeignClientFallback implements OrderFeignClient {

    @Override
    public Result<OrderInfo> getOrder(Long orderId) {
        log.warn("[fallback] order 服务不可用, 查订单降级 orderId={}", orderId);
        return Result.error(503, "订单服务不可用, 请稍后再试");
    }

    @Override
    public Result<Boolean> markPaid(Long orderId) {
        // ⚠ 通知失败是"钱到账了但订单没改状态"的严重不一致, 必须 error 让上层记下来补偿, 不能吞
        log.error("[fallback] order 服务不可用, 标记已付款失败 orderId={} (需人工/对账补偿)", orderId);
        return Result.error(503, "订单服务不可用");
    }

    @Override
    public Result<Boolean> markRefunded(Long orderId) {
        log.error("[fallback] order 服务不可用, 标记退款失败 orderId={} (需人工/对账补偿)", orderId);
        return Result.error(503, "订单服务不可用");
    }
}
