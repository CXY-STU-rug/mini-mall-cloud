package com.minimall.payment.dto;

import lombok.Data;

/**
 * 退款申请请求。
 * V1 只支持全额退款, 所以不收金额(金额以支付单为准, 防篡改), 只要订单号 + 原因。
 */
@Data
public class RefundApplyDTO {
    private Long orderId;
    private String reason;
}
