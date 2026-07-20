package com.minimall.payment.strategy.dto;

import java.math.BigDecimal;

/**
 * 支付回调【归一化】结果。
 *
 * 各渠道的回调报文字段五花八门 (支付宝叫 out_trade_no, 微信叫 out_trade_no 但结构不同...),
 * 策略负责把渠道报文验签 + 翻译成这个统一结构, 编排层 (PaymentServiceImpl) 只认这个,
 * 不必再认识任何一家支付的字段名。
 *
 * 用 Status 三态而不是布尔, 精确对应编排层三种收尾动作:
 *   INVALID  验签失败/应用不符/异常 → 回 "failure" (可疑或出错, 让渠道稍后重发)
 *   IGNORED  验签过但非"支付成功"终态 → 回 "success" (收到了但不处理, 别再重发)
 *   SUCCESS  确认支付成功 → 继续走落库 + 通知 order
 */
public class PayNotifyResult {

    public enum Status {
        /** 验签失败 / app 不符 / 解析异常 —— 回 failure */
        INVALID,
        /** 验签通过但不是成功终态 —— 回 success 但不处理 */
        IGNORED,
        /** 确认支付成功 —— 继续处理 */
        SUCCESS
    }

    private final Status status;

    /** 我方支付单号 (对应支付宝 out_trade_no); 仅 SUCCESS 有值 */
    private final String paymentNo;

    /** 渠道方交易号 (支付宝 trade_no), 退款要用; 仅 SUCCESS 有值 */
    private final String tradeNo;

    /** 回调携带的支付金额, 编排层用它跟建单金额核对防篡改; 仅 SUCCESS 有值 */
    private final BigDecimal amount;

    /** 渠道通知唯一 id (支付宝 notify_id), 编排层用它做幂等落库键; 仅 SUCCESS 有值 */
    private final String notifyId;

    /** 渠道原始交易状态字符串 (如 TRADE_SUCCESS), 仅供落 notify_log 审计; 仅 SUCCESS 有值 */
    private final String rawStatus;

    /** 私有构造, 只允许通过下面三个静态工厂创建, 保证各状态字段搭配正确 */
    private PayNotifyResult(Status status, String paymentNo, String tradeNo,
                           BigDecimal amount, String notifyId, String rawStatus) {
        this.status = status;
        this.paymentNo = paymentNo;
        this.tradeNo = tradeNo;
        this.amount = amount;
        this.notifyId = notifyId;
        this.rawStatus = rawStatus;
    }

    /** 验签失败/异常 —— 其余字段都为 null */
    public static PayNotifyResult invalid() {
        return new PayNotifyResult(Status.INVALID, null, null, null, null, null);
    }

    /** 非成功终态 —— 收到但不处理 */
    public static PayNotifyResult ignored() {
        return new PayNotifyResult(Status.IGNORED, null, null, null, null, null);
    }

    /** 支付成功 —— 带齐编排层落库/核对所需字段 */
    public static PayNotifyResult success(String paymentNo, String tradeNo, BigDecimal amount,
                                          String notifyId, String rawStatus) {
        return new PayNotifyResult(Status.SUCCESS, paymentNo, tradeNo, amount, notifyId, rawStatus);
    }

    public Status getStatus() {
        return status;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public String getTradeNo() {
        return tradeNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getNotifyId() {
        return notifyId;
    }

    public String getRawStatus() {
        return rawStatus;
    }
}
