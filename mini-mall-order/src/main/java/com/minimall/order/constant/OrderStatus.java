package com.minimall.order.constant;

/**
 * 订单状态常量 (从单体 com.minimall.minimall.common.common.constant 搬过来)
 *
 * 为啥用 byte 不用 enum:
 *   ① 跟数据库 orders.status TINYINT 字段一一对应, 无需转换
 *   ② JSON 序列化简单 (0/1/2/3/4 而不是 "PAID")
 *   ③ enum 跨服务 RPC 序列化容易踩坑
 * 不过缺点是: 编译期不强类型, 但用常量名屏蔽了魔法数
 *
 * 状态机:
 *   UNPAID (0) ─ pay  ───────▶ PAID (1) ─ ship ─▶ SHIPPED (2) ─ done ─▶ COMPLETED (3)
 *      │                          │                    │
 *      ├ cancel ─▶ CANCELLED (4)  └── 用户申请退款 ─────┴─▶ REFUND_APPLYING (5)
 *      │           (用户手动取消)                              │
 *      └ 30 min TTL ─▶ CANCELLED (4)                          ├ 客服批准 ─▶ REFUNDED (6)
 *                     (MQ 自动关单)                            └ 客服拒绝 ─▶ 回到原状态(1/2)
 *
 * ⭐ 退款改造(需求): 退款不再一步改成"已取消(4)", 而是先进"申请退款中(5)"等客服审批,
 *   审批通过才真正退钱并进"已退款(6)"。CANCELLED(4) 从此只代表"未付款的取消/关单"。
 */
public class OrderStatus {
    public static final byte UNPAID          = 0;   // 待付款
    public static final byte PAID            = 1;   // 已付款(待发货)
    public static final byte SHIPPED         = 2;   // 已发货(待收货)
    public static final byte COMPLETED       = 3;   // 已完成
    public static final byte CANCELLED       = 4;   // 已取消(仅未付款取消/超时关单)
    public static final byte REFUND_APPLYING = 5;   // 申请退款中(待客服审批)
    public static final byte REFUNDED        = 6;   // 已退款
}
