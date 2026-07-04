package com.minimall.order.controller;

import com.minimall.common.core.domain.Result;
import com.minimall.order.service.IOrdersService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单 Internal Controller (SEC-2 Step2 新增, 给 payment 服务用)。
 *
 * 端点:
 *   PUT /order/internal/{orderId}/paid   支付回调标记已付款
 *
 * ⚠ 安全:
 *   - 网关 AuthGlobalFilter 黑名单已禁止外部访问任何 /internal (含 /order/internal)
 *   - payment 服务走 Feign 直连(Nacos :9003), 不过网关, 所以能调
 *   - 支付宝回调链路: 支付宝 → payment /pay/notify(验签) → Feign 调这里 → 改订单状态
 */
@RestController
@RequestMapping("/order/internal")
@RequiredArgsConstructor
public class OrderInternalController {

    private final IOrdersService ordersService;

    /** 标记订单已付款 (CAS 0→1, 幂等)。返回是否本次真正改成了已付款。 */
    @PutMapping("/{orderId}/paid")
    public Result<Boolean> markPaid(@PathVariable("orderId") Long orderId) {
        boolean changed = ordersService.markPaidByNotify(orderId);
        return Result.success(changed);
    }

    /** 退款成功标记订单退款/关闭 (CAS 1或2→4, 幂等)。 */
    @PutMapping("/{orderId}/refunded")
    public Result<Boolean> markRefunded(@PathVariable("orderId") Long orderId) {
        boolean changed = ordersService.markRefundedByNotify(orderId);
        return Result.success(changed);
    }
}
