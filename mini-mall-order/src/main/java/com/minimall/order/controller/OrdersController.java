package com.minimall.order.controller;

import com.minimall.common.core.context.SecurityContextHolder;
import com.minimall.common.core.domain.Result;
import com.minimall.order.dto.CreateOrderDTO;
import com.minimall.order.service.IOrdersService;
import com.minimall.order.vo.OrderDetailVO;
import com.minimall.order.vo.OrderListVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单 Controller (G3.7 搬, SEC.10 重构 → SecurityContextHolder)
 *
 * 端点 (网关代理 /order/** → mini-mall-order):
 *   POST   /order                 → 创建订单 (发延迟 MQ)
 *   GET    /order/my              → 我的订单列表
 *   GET    /order/{orderId}       → 订单详情
 *   PUT    /order/{orderId}/cancel → 用户取消
 *   POST   /order/{orderId}/pay   → 标记付款 (本地模拟)
 *
 * 注: 路径 /order 而不是单体的 /api/order, 跟网关路由 /order/** 对齐.
 */
@RestController
@RequestMapping("/order")
public class OrdersController {

    @Autowired
    private IOrdersService ordersService;

    /** ① 创建订单 */
    @PostMapping
    public Result<Map<String, Object>> create(
            @RequestBody CreateOrderDTO dto

    ) {
        Long userId= SecurityContextHolder.getUserId();
        return Result.success(ordersService.createOrder(userId, dto));
    }

    /** ② 我的订单列表 */
    @GetMapping("/my")
    public Result<List<OrderListVO>> myOrders() {
        Long userId= SecurityContextHolder.getUserId();
        return Result.success(ordersService.listMyOrders(userId));
    }

    /** ③ 订单详情 */
    @GetMapping("/{orderId}")
    public Result<OrderDetailVO> detail(
            @PathVariable Long orderId

    ) {
        Long userId= SecurityContextHolder.getUserId();
        return Result.success(ordersService.getOrderDetail(userId, orderId));
    }

    /** ④ 取消订单 */
    @PutMapping("/{orderId}/cancel")
    public Result<Void> cancel(
            @PathVariable Long orderId
    ) {
        Long userId= SecurityContextHolder.getUserId();
        ordersService.cancelOrder(userId, orderId);
        return Result.success();
    }

    /** ⑤ 标记付款 (本地模拟) */
    @PostMapping("/{orderId}/pay")
    public Result<Void> pay(
            @PathVariable Long orderId

    ) {
        Long userId= SecurityContextHolder.getUserId();
        ordersService.payOrder(userId, orderId);
        return Result.success();
    }

    // ─────────────────────────────────────────────────────────────
    // G6 物流: 2 个新端点 TODO 用户写
    //
    // ⑥ 发货 (admin)
    //   - PUT /order/{orderId}/ship
    //   - @PathVariable Long orderId
    //   - @RequestBody @Valid ShipOrderDTO dto    ← 加 @Valid 才会触发 @NotBlank
    //   - ❌ 不要调 SecurityContextHolder.getUserId(), admin 操作不需要用户身份
    //   - 调 ordersService.shipOrder(orderId, dto)
    //   - 返 Result.success()
    //
    // ⑦ 签收 (用户)
    //   - PUT /order/{orderId}/sign
    //   - 完全照搬 cancel 端点改个方法名就行
    //   - 调   ordersService.signOrder(userId, orderId);
    //      return Result.success();
    // ─────────────────────────────────────────────────────────────

    // ⑥ 发货接口已删除 (安全修复):
    //    发货是管理员操作, 已由 AdminOrderController 的 PUT /admin/order/{id}/ship 提供,
    //    走 /admin/ 前缀受网关 role=1 保护。
    //    原来这里还挂了个 PUT /order/{orderId}/ship, 没有任何身份校验, 普通用户
    //    就能给别人的订单发货 —— 垂直越权漏洞。两者调的是同一个 shipOrder(),
    //    功能完全重复, 所以直接删掉这个挂错位置的入口, 消除攻击面。

    /** ⑦ 签收 (用户) - TODO 用户实现 G6.5 */


    @PutMapping("/{orderId}/sign")
    public Result<Void> sign( @PathVariable Long orderId)
    {  Long userId= SecurityContextHolder.getUserId();
        ordersService.signOrder(userId, orderId);
        return Result.success();

    }
}
