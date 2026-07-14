package com.minimall.order.controller;

import com.minimall.common.core.domain.Result;
import com.minimall.order.entity.Orders;
import com.minimall.order.service.IOrdersService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

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

    /** 支付对账补偿任务使用：内部查询订单状态，不走用户归属校验。 */
    @GetMapping("/{orderId}")
    public Result<Map<String, Object>> detail(@PathVariable("orderId") Long orderId) {
        Orders order = ordersService.getById(orderId);
        if (order == null) {
            return Result.error(404, "订单不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getId());
        data.put("orderNo", order.getOrderNo());
        data.put("status", order.getStatus());
        data.put("totalAmount", order.getTotalAmount());
        return Result.success(data);
    }

    /** 标记订单已付款 (CAS 0→1, 幂等)。返回是否本次真正改成了已付款。 */
    @PutMapping("/{orderId}/paid")
    public Result<Boolean> markPaid(@PathVariable("orderId") Long orderId) {
        boolean changed = ordersService.markPaidByNotify(orderId);
        return Result.success(changed);
    }

    /**
     * 用户申请退款: 订单打成"申请退款中(5)"。
     * 返回退款前的原状态(1/2), 供客服拒绝时回滚; data=null 表示订单状态不允许申请退款。
     */
    @PutMapping("/{orderId}/refund-applying")
    public Result<Integer> markRefundApplying(@PathVariable("orderId") Long orderId) {
        return Result.success(ordersService.markRefundApplying(orderId));
    }

    /** 客服批准+退款成功: 标记订单"已退款(6)" (CAS 5→6, 幂等)。 */
    @PutMapping("/{orderId}/refunded")
    public Result<Boolean> markRefunded(@PathVariable("orderId") Long orderId) {
        boolean changed = ordersService.markRefundedByNotify(orderId);
        return Result.success(changed);
    }

    /**
     * 客服拒绝退款: 订单从"申请退款中(5)"回滚到原状态。
     * preStatus 由 payment 从退款单带过来(退款前是 1 还是 2)。
     */
    @PutMapping("/{orderId}/refund-reject")
    public Result<Boolean> markRefundReject(@PathVariable("orderId") Long orderId,
                                            @RequestParam("preStatus") Integer preStatus) {
        return Result.success(ordersService.markRefundReject(orderId, preStatus));
    }
}
