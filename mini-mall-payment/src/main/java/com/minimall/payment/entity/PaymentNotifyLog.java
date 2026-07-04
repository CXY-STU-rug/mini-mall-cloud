package com.minimall.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 回调流水/幂等黑匣子 (对应 payment_notify_log 表)。
 * 所有支付宝回调无论成败都落一条: 既做幂等(notify_id 唯一索引挡重复), 又做审计(存原始报文)。
 */
@Data
@TableName("payment_notify_log")
public class PaymentNotifyLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 支付宝通知 ID, 幂等唯一键 (同一条通知只处理一次) */
    private String notifyId;

    /** 回调类型: 1支付回调 2退款回调 */
    private Byte notifyType;

    /** 我方单号 (out_trade_no) */
    private String outTradeNo;

    /** 支付宝交易号 */
    private String tradeNo;

    /** 交易状态 (TRADE_SUCCESS / TRADE_FINISHED 等) */
    private String tradeStatus;

    /** 回调原始报文全量 (出问题能复盘对账) */
    private String rawBody;

    /** 验签结果: 1成功 0失败 */
    private Byte verifyResult;

    private LocalDateTime createTime;
}
