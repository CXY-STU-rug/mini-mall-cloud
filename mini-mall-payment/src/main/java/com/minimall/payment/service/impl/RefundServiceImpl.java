package com.minimall.payment.service.impl;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.minimall.common.core.context.SecurityContextHolder;
import com.minimall.common.core.domain.Result;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.payment.client.OrderFeignClient;
import com.minimall.payment.dto.RefundApplyDTO;
import com.minimall.payment.entity.Payment;
import com.minimall.payment.entity.Refund;
import com.minimall.payment.mapper.PaymentMapper;
import com.minimall.payment.mapper.RefundMapper;
import com.minimall.payment.service.IRefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 退款服务实现 (Phase 5-6, V1 全额退, 同步)。
 *
 * apply 流程:
 *   ① 拿当前用户
 *   ② 查该订单"已支付(status=1)"的支付单 (归属校验 + 状态校验)
 *   ③ 幂等: 该支付单是否已有进行中/成功的退款单
 *   ④ 建退款单 refund (status=0 申请中)
 *   ⑤ 同步调支付宝 alipay.trade.refund
 *   ⑥ 按同步结果落地: 成功→refund=2 + payment CAS 1→4 + 通知 order; 失败→refund=3
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RefundServiceImpl implements IRefundService {

    private final RefundMapper refundMapper;
    private final PaymentMapper paymentMapper;
    private final OrderFeignClient orderFeignClient;
    private final AlipayClient alipayClient;

    @Override
    public boolean apply(RefundApplyDTO dto) {
        // ① 当前用户
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) throw new BusinessException(401, "未登录");
        if (dto == null || dto.getOrderId() == null) throw new BusinessException(400, "缺少订单号");

        // ② 查该订单"已支付"的支付单 (user_id 做归属, status=1 做状态校验)
        QueryWrapper<Payment> pq = new QueryWrapper<>();
        pq.eq("order_id", dto.getOrderId()).eq("user_id", userId).eq("status", 1)
          .orderByDesc("id").last("limit 1");
        Payment payment = paymentMapper.selectOne(pq);
        if (payment == null) {
            throw new BusinessException(400, "没有可退款的已支付订单");
        }

        // ③ 幂等: 该支付单已有"申请中(0)/处理中(1)/成功(2)"的退款单就不再发起
        QueryWrapper<Refund> rq = new QueryWrapper<>();
        rq.eq("payment_id", payment.getId()).in("status", 0, 1, 2).last("limit 1");
        if (refundMapper.selectOne(rq) != null) {
            throw new BusinessException(400, "该订单已在退款中或已退款");
        }

        // ④ 建退款单 (金额以支付单为准, V1 全额退)
        BigDecimal amount = payment.getAmount();
        String refundNo = genRefundNo(userId);
        Refund refund = new Refund();
        refund.setRefundNo(refundNo);
        refund.setPaymentId(payment.getId());
        refund.setPaymentNo(payment.getPaymentNo());
        refund.setOrderId(payment.getOrderId());
        refund.setOrderNo(payment.getOrderNo());
        refund.setUserId(userId);
        refund.setAmount(amount);
        refund.setReason(dto.getReason());
        refund.setStatus((byte) 0);   // 申请中
        refundMapper.insert(refund);
        log.info("[refund] 建退款单 refundNo={} paymentNo={} amount={}", refundNo, payment.getPaymentNo(), amount);

        // ⑤ 同步调支付宝退款
        AlipayTradeRefundResponse resp;
        try {
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            // out_request_no: 部分退款时区分每一笔; 全额退也要传, 用退款单号
            String bizContent = String.format(
                    "{\"out_trade_no\":\"%s\",\"refund_amount\":\"%s\",\"out_request_no\":\"%s\",\"refund_reason\":\"%s\"}",
                    payment.getPaymentNo(), amount.toPlainString(), refundNo,
                    dto.getReason() == null ? "用户申请退款" : dto.getReason());
            request.setBizContent(bizContent);
            resp = alipayClient.execute(request);   // ⭐ execute(同步), 不是 pageExecute
        } catch (Exception e) {
            markRefundFailed(refund, "调支付宝异常: " + e.getMessage());
            throw new BusinessException(500, "退款失败: " + e.getMessage());
        }

        // ⑥ 按同步结果落地
        // isSuccess() = 网关+业务都成功(code=10000); fund_change=Y 表示确实退了钱
        if (resp.isSuccess() && "Y".equals(resp.getFundChange())) {
            // 退款单 → 成功
            refund.setStatus((byte) 2);
            refund.setRefundTradeNo(resp.getTradeNo());
            refundMapper.updateById(refund);

            // 支付单 CAS 1(已支付)→4(已退款), 幂等
            UpdateWrapper<Payment> uw = new UpdateWrapper<>();
            uw.eq("id", payment.getId()).eq("status", 1).set("status", 4);
            paymentMapper.update(null, uw);

            // 通知 order 标记退款/关闭 (失败只 log, 靠对账补偿, 跟支付回调同理)
            try {
                Result<Boolean> r = orderFeignClient.markRefunded(payment.getOrderId());
                if (r == null || r.getCode() == null || r.getCode() != 200) {
                    log.error("[refund] 通知 order 退款失败, 待对账补偿 orderId={}", payment.getOrderId());
                }
            } catch (Exception e) {
                log.error("[refund] 通知 order 退款异常, 待对账补偿 orderId={}", payment.getOrderId(), e);
            }
            log.info("[refund] 退款成功 refundNo={} 支付宝交易号={}", refundNo, resp.getTradeNo());
            return true;
        } else {
            // 退款失败 (余额不足/超时/参数错等)
            String msg = resp.getSubMsg() == null ? resp.getMsg() : resp.getSubMsg();
            markRefundFailed(refund, msg);
            throw new BusinessException(400, "退款失败: " + msg);
        }
    }

    /** 退款失败: 退款单标记 3失败 + 记原因 */
    private void markRefundFailed(Refund refund, String msg) {
        refund.setStatus((byte) 3);
        refundMapper.updateById(refund);
        log.warn("[refund] 退款失败 refundNo={} msg={}", refund.getRefundNo(), msg);
    }

    /** 生成退款单号: REF + 时间 + userId + 4位随机 */
    private String genRefundNo(Long userId) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "REF" + ts + userId + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
