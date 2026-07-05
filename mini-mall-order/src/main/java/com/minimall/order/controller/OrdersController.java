package com.minimall.order.controller;

import com.minimall.common.core.context.SecurityContextHolder;
import com.minimall.common.core.domain.Result;
import com.minimall.order.dto.CreateOrderDTO;
import com.minimall.order.service.IOrdersService;
import com.minimall.order.vo.OrderDetailVO;
import com.minimall.order.vo.OrderListVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 普通订单控制器。
 *
 * 外部 C 端用户入口只保留普通订单能力；支付服务回调订单状态的内部入口
 * 由 {@link OrderInternalController} 单独承载，避免 /order/internal/** 重复映射。
 */
@RestController
@RequestMapping("/order")
public class OrdersController {

    @Autowired
    private IOrdersService ordersService;

    /** 创建普通订单。 */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody CreateOrderDTO dto) {
        Long userId = SecurityContextHolder.getUserId();
        return Result.success(ordersService.createOrder(userId, dto));
    }

    /** 查询我的订单列表。 */
    @GetMapping("/my")
    public Result<List<OrderListVO>> myOrders() {
        Long userId = SecurityContextHolder.getUserId();
        return Result.success(ordersService.listMyOrders(userId));
    }

    /** 查询订单详情。 */
    @GetMapping("/{orderId}")
    public Result<OrderDetailVO> detail(@PathVariable Long orderId) {
        Long userId = SecurityContextHolder.getUserId();
        return Result.success(ordersService.getOrderDetail(userId, orderId));
    }

    /** 用户取消待付款订单。 */
    @PutMapping("/{orderId}/cancel")
    public Result<Void> cancel(@PathVariable Long orderId) {
        Long userId = SecurityContextHolder.getUserId();
        ordersService.cancelOrder(userId, orderId);
        return Result.success();
    }

    /**
     * 本地模拟付款入口。
     *
     * 真实支付宝链路已经由 mini-mall-payment 的 /pay/create + /pay/notify 承载；
     * 这里先保留旧接口，避免旧页面或测试脚本突然失效。
     */
    @PostMapping("/{orderId}/pay")
    public Result<Void> pay(@PathVariable Long orderId) {
        Long userId = SecurityContextHolder.getUserId();
        ordersService.payOrder(userId, orderId);
        return Result.success();
    }

    /** 用户确认收货。 */
    @PutMapping("/{orderId}/sign")
    public Result<Void> sign(@PathVariable Long orderId) {
        Long userId = SecurityContextHolder.getUserId();
        ordersService.signOrder(userId, orderId);
        return Result.success();
    }
}
