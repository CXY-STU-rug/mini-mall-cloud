package com.minimall.payment.strategy;

import com.minimall.payment.enums.PayChannel;
import com.minimall.payment.strategy.dto.PayNotifyResult;
import com.minimall.payment.strategy.dto.PayQueryResult;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付渠道策略。
 *
 * 一个渠道 (支付宝/微信/...) 只跟外部支付平台打交道的那三件事收在这里, 其余通用编排
 * (建单、幂等落库、金额核对、CAS 改状态、通知 order) 留在 PaymentServiceImpl。
 *
 * 加一个新渠道 = 新写一个实现类 (加 @Component 即被工厂自动收集), 编排层零改动。
 */
public interface PayChannelStrategy {

    /** 本策略对应的渠道, 工厂据此建立 渠道→策略 的映射 */
    PayChannel getChannel();

    /**
     * 生成支付页 / 支付参数。
     * @param paymentNo 我方支付单号 (传给渠道当商户订单号)
     * @param amount    金额 (服务端权威金额, 非前端所传)
     * @param orderNo   订单号 (拼支付标题用)
     * @return 前端可直接用的支付载体 (支付宝: 自动提交表单 HTML; 微信: 可返二维码链接)
     */
    String createPayForm(String paymentNo, BigDecimal amount, String orderNo);

    /**
     * 验签 + 解析异步回调, 归一化成 PayNotifyResult。
     * 只做"是不是渠道发来的、付没付成、解析出关键字段", 不碰数据库。
     * @param params 渠道 POST 过来的原始回调参数
     */
    PayNotifyResult verifyAndParse(Map<String, String> params);

    /**
     * 主动向渠道查询这笔单的真实状态 (回调靠不住时的兜底)。
     * 任何失败都返回 notPaid(), 不抛异常 —— 兜底查询不该影响前端拿当前状态。
     * @param paymentNo 我方支付单号
     */
    PayQueryResult query(String paymentNo);
}
