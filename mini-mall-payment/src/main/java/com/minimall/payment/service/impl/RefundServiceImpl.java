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
import java.util.List;
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
        // ═══ 第一段: 用户申请。只登记 + 订单进"申请退款中", 绝不碰支付宝、不退钱 ═══
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

        // ③ 幂等: 该支付单已有"待审批(0)/处理中(1)/成功(2)"的退款单就不再发起
        QueryWrapper<Refund> rq = new QueryWrapper<>();
        rq.eq("payment_id", payment.getId()).in("status", 0, 1, 2).last("limit 1");
        if (refundMapper.selectOne(rq) != null) {
            throw new BusinessException(400, "该订单已在退款中或已退款");
        }

        // ④ 建退款单 (status=0 待客服审批; 金额以支付单为准, V1 全额退)
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
        refund.setStatus((byte) 0);   // 0 = 待客服审批 (不再当场调支付宝)
        refundMapper.insert(refund);
        log.info("[refund] 收到退款申请 refundNo={} paymentNo={} amount={}", refundNo, payment.getPaymentNo(), amount);

        // ⑤ 通知 order 把订单打成"申请退款中(5)", 并拿回退款前的原状态(1/2)
        //   ⭐ 关键: 只有 order 那边 CAS 成功(订单确实在 1/2)才算申请成立;
        //     若订单已完成/已取消/已在退款中 → preStatus=null → 本次申请作废(退款单标失败)
        Integer preStatus;
        try {
            Result<Integer> r = orderFeignClient.markRefundApplying(payment.getOrderId());
            if (r == null || r.getCode() == null || r.getCode() != 200) {
                markRefundFailed(refund, "订单服务不可用");
                throw new BusinessException(500, "订单服务不可用, 请稍后再试");
            }
            preStatus = r.getData();
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            markRefundFailed(refund, "调订单服务异常: " + e.getMessage());
            throw new BusinessException(500, "申请退款失败, 请稍后再试");
        }
        if (preStatus == null) {
            markRefundFailed(refund, "订单状态不允许申请退款");
            throw new BusinessException(400, "当前订单状态不支持申请退款");
        }

        // ⑥ 回填"退款前订单状态", 供客服拒绝时回滚
        refund.setPreOrderStatus(preStatus);
        refundMapper.updateById(refund);
        log.info("[refund] 退款申请已提交, 待客服审批 refundNo={} orderId={} preStatus={}",
                refundNo, payment.getOrderId(), preStatus);
        return true;
    }

    @Override
    public List<Refund> listPending() {
        // 客服端: 拉所有 status=0(待审批) 的退款申请, 最新的在前
        QueryWrapper<Refund> q = new QueryWrapper<>();
        q.eq("status", 0).orderByDesc("id");
        return refundMapper.selectList(q);
    }

    @Override
    public boolean approve(Long refundId) {
        // ═══ 第二段之"批准": 到这一步才真正调支付宝退款 ═══
        Refund refund = refundMapper.selectById(refundId);
        if (refund == null) throw new BusinessException(404, "退款单不存在");
        if (refund.getStatus() == null || refund.getStatus() != 0) {
            throw new BusinessException(400, "该退款单已处理, 不能重复审批");
        }

        // 调支付宝真退款 (这段就是原来 apply 里的 ⑤⑥, 只是触发时机改到"审批通过")
        AlipayTradeRefundResponse resp;
        try {
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            String bizContent = String.format(
                    "{\"out_trade_no\":\"%s\",\"refund_amount\":\"%s\",\"out_request_no\":\"%s\",\"refund_reason\":\"%s\"}",
                    refund.getPaymentNo(), refund.getAmount().toPlainString(), refund.getRefundNo(),
                    refund.getReason() == null ? "客服批准退款" : refund.getReason());
            request.setBizContent(bizContent);
            resp = alipayClient.execute(request);   // ⭐ execute(同步), 不是 pageExecute
        } catch (Exception e) {
            markRefundFailed(refund, "调支付宝异常: " + e.getMessage());
            rollbackOrderAfterFailedRefund(refund);   // 退款没成功, 订单回到原状态, 别卡在"申请退款中"
            throw new BusinessException(500, "退款失败: " + e.getMessage());
        }

        // isSuccess()=网关+业务都成功(code=10000); fund_change=Y 表示确实退了钱
        if (resp.isSuccess() && "Y".equals(resp.getFundChange())) {
            refund.setStatus((byte) 2);                 // 退款单 → 成功
            refund.setRefundTradeNo(resp.getTradeNo());
            refundMapper.updateById(refund);

            // 支付单 CAS 1(已支付)→4(已退款), 幂等
            UpdateWrapper<Payment> uw = new UpdateWrapper<>();
            uw.eq("id", refund.getPaymentId()).eq("status", 1).set("status", 4);
            paymentMapper.update(null, uw);

            // 通知 order 标记"已退款(6)" (失败只 log, 靠对账补偿, 跟支付回调同理)
            try {
                Result<Boolean> r = orderFeignClient.markRefunded(refund.getOrderId());
                if (r == null || r.getCode() == null || r.getCode() != 200) {
                    log.error("[refund] 通知 order 退款失败, 待对账补偿 orderId={}", refund.getOrderId());
                }
            } catch (Exception e) {
                log.error("[refund] 通知 order 退款异常, 待对账补偿 orderId={}", refund.getOrderId(), e);
            }
            log.info("[refund] 客服批准退款成功 refundNo={} 支付宝交易号={}", refund.getRefundNo(), resp.getTradeNo());
            return true;
        } else {
            String msg = resp.getSubMsg() == null ? resp.getMsg() : resp.getSubMsg();
            markRefundFailed(refund, msg);
            rollbackOrderAfterFailedRefund(refund);
            throw new BusinessException(400, "退款失败: " + msg);
        }
    }

    @Override
    public boolean reject(Long refundId) {
        // ═══ 第二段之"拒绝": 不退钱, 退款单标已拒绝, 订单回滚到原状态 ═══
        Refund refund = refundMapper.selectById(refundId);
        if (refund == null) throw new BusinessException(404, "退款单不存在");
        if (refund.getStatus() == null || refund.getStatus() != 0) {
            throw new BusinessException(400, "该退款单已处理, 不能重复驳回");
        }
        refund.setStatus((byte) 4);   // 4 = 已拒绝
        refundMapper.updateById(refund);

        // 订单从"申请退款中(5)"回滚到申请前的原状态(1/2)
        try {
            Result<Boolean> r = orderFeignClient.markRefundReject(refund.getOrderId(), refund.getPreOrderStatus());
            if (r == null || r.getCode() == null || r.getCode() != 200) {
                log.error("[refund] 拒绝后回滚订单失败 orderId={}", refund.getOrderId());
            }
        } catch (Exception e) {
            log.error("[refund] 拒绝后回滚订单异常 orderId={}", refund.getOrderId(), e);
        }
        log.info("[refund] 客服拒绝退款 refundNo={}", refund.getRefundNo());
        return true;
    }

    /** 退款没退成时, 把订单从"申请退款中(5)"回滚到原状态, 避免订单永久卡住 */
    private void rollbackOrderAfterFailedRefund(Refund refund) {
        try {
            orderFeignClient.markRefundReject(refund.getOrderId(), refund.getPreOrderStatus());
        } catch (Exception e) {
            log.error("[refund] 退款失败回滚订单异常 orderId={}", refund.getOrderId(), e);
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
