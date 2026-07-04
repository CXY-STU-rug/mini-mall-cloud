package com.minimall.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.minimall.order.dto.CreateOrderDTO;
import com.minimall.order.dto.ShipOrderDTO;
import com.minimall.order.entity.Orders;
import com.minimall.order.vo.OrderDetailVO;
import com.minimall.order.vo.OrderListVO;

import java.util.List;
import java.util.Map;

/**
 * 订单服务接口 (从单体 IOrdersService 搬, 改: 所有方法第一参数加 Long userId)
 *
 * ════════════════════════════════════════════════════════════════
 * vs 单体差异:
 *   ① 单体的方法签名不带 userId, 用 UserContext.getUserId() 取
 *   ② 微服务里【显式】传 userId 参数, 因为:
 *      - controller 从 @RequestHeader("X-User-Id") 拿到
 *      - service 层不能依赖 ThreadLocal (后续可能跨线程: 异步, MQ 消费者)
 *   ③ closeOrderByMQ 不带 userId, 因为 MQ 消费者线程没登录用户
 *
 * 6 个方法 + 1 个 (单体的 closeTimeoutOrders 兜底定时任务暂不搬)
 * ════════════════════════════════════════════════════════════════
 */
public interface IOrdersService extends IService<Orders> {

    /** 创建订单 */
    Map<String, Object> createOrder(Long userId, CreateOrderDTO dto);

    /** 我的订单列表 */
    List<OrderListVO> listMyOrders(Long userId);

    /** 订单详情 */
    OrderDetailVO getOrderDetail(Long userId, Long orderId);

    /**
     * 订单详情 (ADMIN 用): 跳过越权校验, 因为管理员可以看任何订单
     * 仅供 /admin/order/{id} 端点使用; 网关已校验 role=1
     */
    OrderDetailVO getOrderDetailForAdmin(Long orderId);

    /** 用户手动取消订单 */
    void cancelOrder(Long userId, Long orderId);

    /** 标记已付款 (本地模拟支付, 不接真支付) */
    void payOrder(Long userId, Long orderId);

    /**
     * MQ 消费者关单 (注意: 没 userId 参数, 因为消费者没登录上下文)
     * 关键: 必须【幂等】, 消息可能重复投递
     */
    void closeOrderByMQ(Long orderId);

    // ─── G6 物流: 状态机推进 ─────────────────────────────────
    // 状态流转: 1 已付款 ──ship──> 2 已发货 ──sign──> 3 已完成
    // ─────────────────────────────────────────────────────────

    /**
     * 发货 (管理员/仓库系统调用, 无 userId)
     *
     * 状态机前置: status 必须 = 1 (已付款), 否则拒绝
     * 副作用: status 改 2, 填 shipTime + logisticsNo + logisticsCompany
     *
     * ⚠ TODO 安全: 当前无 admin 网关守护, 普通用户能调到这个接口.
     *    真生产环境必须挂在 admin 路径下 + RBAC 鉴权 (G10 admin 模块补)
     */
    void shipOrder(Long orderId, ShipOrderDTO dto);

    /**
     * 签收 (用户主动确认收货)
     *
     * 状态机前置: status 必须 = 2 (已发货), 否则拒绝
     * 副作用: status 改 3, 填 finishTime
     * 越权防护: orders.user_id 必须 = 入参 userId, 否则拒绝
     */
    void signOrder(Long userId, Long orderId);

    /**
     * 支付回调标记已付款 (payment 服务经 Feign internal 调, 无 userId)。
     * <p>
     * 跟 payOrder 的区别:
     *   - 没有 userId 越权校验 (调用方是 payment 服务, 不是终端用户)
     *   - 无 Redis 锁: 幂等只靠 CAS(WHERE status=0), 支付宝可能重复回调
     * 状态机: 0(待付款) → 1(已付款), CAS 命中才算真正首次付款成功。
     * @return true=本次把订单从待付款改成了已付款; false=订单已不是待付款(已付/已取消/重复回调)
     */
    boolean markPaidByNotify(Long orderId);

    /**
     * 退款成功后标记订单已退款/关闭 (payment 服务 Feign internal 调, 无 userId)。
     * 状态机: 已付款(1) 或 已发货(2) → 已取消/退款(4), CAS 幂等。
     * ⚠ V1 从简: 只改订单状态; 生产退款还应回补库存+退券(可复用 cancelOrder 的回补逻辑)。
     * @return true=本次改成了退款态; false=状态不允许退款或重复请求
     */
    boolean markRefundedByNotify(Long orderId);
}
