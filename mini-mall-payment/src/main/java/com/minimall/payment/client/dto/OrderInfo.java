package com.minimall.payment.client.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单信息副本 (Feign 调 order 的 GET /order/{orderId} 后接收 JSON 用)。
 * <p>
 * 微服务铁律: payment 引不到 order 的 OrderDetailVO 类, 所以在这里放一个字段名一致的副本,
 * Jackson 按字段名把 JSON 反序列化进来 (跟 search 服务的 ProductSource 一个套路)。
 * 只声明支付要用到的字段, 其余(receiver/items 等)不关心就不写。
 */
@Data
public class OrderInfo {
    private Long orderId;
    private String orderNo;
    /** 订单状态: 0待付款 1已付款 2已发货 3已完成 4已取消 */
    private Byte status;
    /** 应付金额 —— 这就是支付的权威金额来源 */
    private BigDecimal totalAmount;
}
