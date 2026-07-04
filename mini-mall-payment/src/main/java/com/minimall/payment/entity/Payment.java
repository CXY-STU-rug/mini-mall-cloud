package com.minimall.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单实体 (对应 payment 表)。一次支付尝试一行。
 *
 * MP 注解说明:
 *   @TableName("payment")        类 ↔ 表名映射 (类名 Payment 跟表名 payment 不完全一致, 显式指定最稳)
 *   @TableId(type=IdType.AUTO)   主键用数据库自增 (跟建表 AUTO_INCREMENT 对应)
 *   其余字段靠 application.yml 的 map-underscore-to-camel-case 自动"下划线↔驼峰":
 *     数据库 payment_no ↔ Java paymentNo, 不用逐个 @TableField
 *
 * createTime/updateTime 不用手动设: MP 默认只 INSERT 非 null 字段,
 *   这俩为 null 就不出现在 SQL 里, 数据库的 DEFAULT CURRENT_TIMESTAMP 自动填。
 */
@Data
@TableName("payment")
public class Payment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 我方支付单号 (传给支付宝当 out_trade_no, 全局唯一) */
    private String paymentNo;

    /** 关联订单 id / 订单号 */
    private Long orderId;
    private String orderNo;

    /** 下单用户 (冗余, 便于查"我的支付") */
    private Long userId;

    /** 应付金额 (下单时从 order 冻结, 服务端为准) */
    private BigDecimal amount;

    /** 支付渠道: 1=支付宝 (预留 2=微信) */
    private Byte channel;

    /** 支付宝交易号 (回调时才有, 退款要用它) */
    private String tradeNo;

    /** 支付单状态: 0待支付 1已支付 2已关闭 3退款中 4已退款 */
    private Byte status;

    /** 回调到账时间 */
    private LocalDateTime notifyTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
