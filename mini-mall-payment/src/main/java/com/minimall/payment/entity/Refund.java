package com.minimall.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单实体 (对应 refund 表)。一次退款一行, 支持部分退。
 * 注解套路跟 Payment 完全一样, 不再重复解释。
 */
@Data
@TableName("refund")
public class Refund implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 我方退款单号 (传给支付宝当 out_request_no, 唯一) */
    private String refundNo;

    /** 关联原支付单 */
    private Long paymentId;
    private String paymentNo;

    /** 关联订单 */
    private Long orderId;
    private String orderNo;

    /** 退款用户 */
    private Long userId;

    /** 退款金额 (可小于原支付额 = 部分退) */
    private BigDecimal amount;

    /** 退款原因 */
    private String reason;

    /** 退款状态: 0申请中 1处理中 2成功 3失败 */
    private Byte status;

    /** 支付宝退款流水号 */
    private String refundTradeNo;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
