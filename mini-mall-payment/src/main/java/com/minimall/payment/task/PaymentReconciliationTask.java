package com.minimall.payment.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimall.common.core.domain.Result;
import com.minimall.payment.client.OrderFeignClient;
import com.minimall.payment.client.dto.OrderInfo;
import com.minimall.payment.entity.Payment;
import com.minimall.payment.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 支付对账补偿任务。
 *
 * 核心目标：
 * payment.status=1 已经说明钱到了；如果 order 仍是待付款，就补一次 markPaid。
 * 这样可以兜住“支付宝回调处理成功，但 Feign 通知订单失败”的不一致窗口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationTask {

    private static final int BATCH_SIZE = 100;
    private static final byte PAYMENT_STATUS_PAID = 1;
    private static final byte ORDER_STATUS_UNPAID = 0;

    private final PaymentMapper paymentMapper;
    private final OrderFeignClient orderFeignClient;

    @Scheduled(
            initialDelayString = "${minimall.payment.reconcile.initial-delay:30000}",
            fixedDelayString = "${minimall.payment.reconcile.fixed-delay:60000}"
    )
    public void reconcilePaidPayments() {
        QueryWrapper<Payment> wrapper = new QueryWrapper<>();
        wrapper.eq("status", PAYMENT_STATUS_PAID)
                .orderByDesc("update_time")
                .last("limit " + BATCH_SIZE);

        List<Payment> payments = paymentMapper.selectList(wrapper);
        if (payments == null || payments.isEmpty()) {
            return;
        }

        for (Payment payment : payments) {
            reconcileOne(payment);
        }
    }

    private void reconcileOne(Payment payment) {
        if (payment == null || payment.getOrderId() == null) {
            return;
        }

        Long orderId = payment.getOrderId();
        try {
            Result<OrderInfo> orderResp = orderFeignClient.getInternalOrder(orderId);
            if (orderResp == null || orderResp.getCode() == null || orderResp.getCode() != 200 || orderResp.getData() == null) {
                log.warn("[pay-reconcile] query order failed paymentNo={} orderId={} message={}",
                        payment.getPaymentNo(), orderId, orderResp == null ? null : orderResp.getMessage());
                return;
            }

            OrderInfo order = orderResp.getData();
            if (order.getStatus() == null || order.getStatus() != ORDER_STATUS_UNPAID) {
                return;
            }

            Result<Boolean> markResp = orderFeignClient.markPaid(orderId);
            if (markResp == null || markResp.getCode() == null || markResp.getCode() != 200) {
                log.warn("[pay-reconcile] mark paid failed paymentNo={} orderId={} message={}",
                        payment.getPaymentNo(), orderId, markResp == null ? null : markResp.getMessage());
                return;
            }

            log.info("[pay-reconcile] order markPaid compensated paymentNo={} orderId={} changed={}",
                    payment.getPaymentNo(), orderId, markResp.getData());
        } catch (Exception e) {
            log.warn("[pay-reconcile] reconcile exception paymentNo={} orderId={}",
                    payment.getPaymentNo(), orderId, e);
        }
    }
}
