package com.minimall.payment.strategy.dto;

import java.math.BigDecimal;

/**
 * 主动查询【归一化】结果。
 *
 * 前端轮询时若支付单还是待支付, 编排层会让策略主动去问渠道"这单到底付了没",
 * 策略把渠道返回翻译成这个统一结构。查询失败/未支付一律 paid=false, 编排层据此决定要不要落库。
 */
public class PayQueryResult {

    /** 渠道确认已支付成功 = true; 未支付/查询失败/异常一律 false */
    private final boolean paid;

    /** 渠道交易号, 仅 paid=true 有值 */
    private final String tradeNo;

    /** 渠道返回的支付金额, 编排层落库前跟建单金额核对; 仅 paid=true 有值 */
    private final BigDecimal amount;

    private PayQueryResult(boolean paid, String tradeNo, BigDecimal amount) {
        this.paid = paid;
        this.tradeNo = tradeNo;
        this.amount = amount;
    }

    /** 未支付 / 查询未成功 —— 编排层不做任何落库 */
    public static PayQueryResult notPaid() {
        return new PayQueryResult(false, null, null);
    }

    /** 已支付 —— 带交易号和金额供编排层落库/核对 */
    public static PayQueryResult paid(String tradeNo, BigDecimal amount) {
        return new PayQueryResult(true, tradeNo, amount);
    }

    public boolean isPaid() {
        return paid;
    }

    public String getTradeNo() {
        return tradeNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
