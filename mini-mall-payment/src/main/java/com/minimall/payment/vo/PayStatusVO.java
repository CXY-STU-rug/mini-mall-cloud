package com.minimall.payment.vo;

import lombok.Data;

/**
 * 支付状态查询结果 (前端下单跳支付宝后, 轮询这个接口判断是否付款成功)。
 * 前端只要看 paid==true 就跳成功页。
 */
@Data
public class PayStatusVO {
    private Long orderId;
    private String paymentNo;
    /** 支付单状态: 0待支付 1已支付 2已关闭 3退款中 4已退款; null=还没建过支付单 */
    private Byte status;
    private String statusDesc;
    /** 是否已支付 (status==1), 前端轮询主要看它 */
    private Boolean paid;
}
