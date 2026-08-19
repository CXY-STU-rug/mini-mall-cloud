package com.minimall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表 Entity (从单体 com.minimall.minimall.entity 搬过来)
 *
 * ════════════════════════════════════════════════════════════════
 * vs 单体差异:
 *   ① 包名换 com.minimall.order.entity
 *   ② ⭐ 加 @TableName("orders") 显式声明
 *      ─ "orders" 是 MySQL 保留字, MP 默认推断为 "orders" 也能跑,
 *        但加上更清晰 + 防止 MP 版本变化 / 某些方言下生成 SQL 没加引号
 *   ③ 保留 @TableLogic (cart_item bug 不适用这里:
 *      orders 表没有 (userId, productId) 唯一索引,
 *      逻辑删除不会跟唯一索引冲突)
 * ════════════════════════════════════════════════════════════════
 */
@Getter
@Setter
@TableName("orders")
public class Orders implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单号 (业务唯一, 格式: yyyyMMddHHmmss + userId + 4 位随机) */
    private String orderNo;

    /** 用户 ID (来自 X-User-Id header) */
    private Long userId;

    /** 订单总金额 (DECIMAL, 必须用 BigDecimal 不能用 Double) */
    private BigDecimal totalAmount;

    /** 状态: 0待付款 1已付款 2已发货 3已完成 4已取消 */
    private Byte status;

    /** 收货人 (快照, 下单瞬间冻结, 后续地址变更不影响) */
    private String receiver;

    /** 手机号 (快照) */
    private String phone;

    /** 完整地址 (快照, 拼成一个字符串) */
    private String address;

    /** 支付时间 (status=1 时填) */
    private LocalDateTime payTime;

    /** 发货时间 (status=2 时填) */
    private LocalDateTime shipTime;

    /** 完成时间 (status=3 时填) */
    private LocalDateTime finishTime;

    // ─── G6 物流字段 ─────────────────────────────────────────
    // 设计选择: 直接给 orders 表加 2 列 (而不是独立 logistics 表)
    // 理由: 当前 N=1 物流场景, 没必要拆表; 等真出现"多承运商/转运/跨境拆单"再独立
    // MP 映射: DB logistics_no → Java logisticsNo (yml 开了 map-underscore-to-camel-case)
    /** 物流单号 (status=2 发货时填) */
    private String logisticsNo;

    /** 物流公司 (status=2 发货时填, 例: 顺丰 / 中通 / 京东) */
    private String logisticsCompany;
    // ─────────────────────────────────────────────────────────

    // ─── G8 优惠券字段 ───────────────────────────────────────
    /** 用户使用的具体券 id (关联 user_coupon.id, 不是 coupon 模板) */
    private Long userCouponId;

    /** 实际抵扣金额 快照 (取消订单时按这个值回退给券) */
    private BigDecimal discountAmount;
    // ─────────────────────────────────────────────────────────

    /** 备注 (用户填的下单备注) */
    private String remark;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 逻辑删除: 0 未删 1 已删 */
    @TableLogic
    private Byte isDeleted;

    /** 幂等 key（对应 DB request_id，建唯一索引 uk_request_id 兜底防重复下单） */
    private String requestId;
}
