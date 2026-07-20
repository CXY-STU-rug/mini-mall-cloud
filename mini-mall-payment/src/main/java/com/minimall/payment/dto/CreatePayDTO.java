package com.minimall.payment.dto;

import lombok.Data;

/**
 * 创建支付单请求 (前端下单后带订单 id 来发起支付)。
 * 只传 orderId, 金额绝不让前端传 —— 服务端 Feign 查 order 拿真实金额, 防篡改。
 */
@Data
public class CreatePayDTO {
    private Long orderId;

    /**
     * 支付渠道 (取值见 PayChannel 枚举名, 如 "ALIPAY")。
     * 不传/传空默认支付宝, 兼容不带该字段的老前端; 服务端用 PayChannel.ofName 解析。
     */
    private String channel;
}
