package com.minimall.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.minimall.common.core.context.SecurityContextHolder;
import com.minimall.common.core.domain.Result;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.payment.client.OrderFeignClient;
import com.minimall.payment.client.dto.OrderInfo;
import com.minimall.payment.dto.CreatePayDTO;
import com.minimall.payment.entity.Payment;
import com.minimall.payment.entity.PaymentNotifyLog;
import com.minimall.payment.enums.PayChannel;
import com.minimall.payment.mapper.PaymentMapper;
import com.minimall.payment.mapper.PaymentNotifyLogMapper;
import com.minimall.payment.service.IPaymentService;
import com.minimall.payment.strategy.PayChannelStrategy;
import com.minimall.payment.strategy.PayChannelStrategyFactory;
import com.minimall.payment.strategy.dto.PayNotifyResult;
import com.minimall.payment.strategy.dto.PayQueryResult;
import com.minimall.payment.vo.PayStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付服务实现 —— 只做【与渠道无关的通用编排】。
 *
 * 所有跟具体支付平台绑死的动作 (生成支付页 / 验签解析回调 / 主动查询) 都委托给
 * PayChannelStrategy, 本类通过 PayChannelStrategyFactory 按渠道取策略。
 *
 * 三条主线各自的通用编排:
 *   create        查订单 → 校验 → 建单(幂等复用) → 让策略生成支付页
 *   handleNotify  让策略验签解析 → 幂等落库 → 金额核对 → CAS 改状态 → 通知 order
 *   queryStatus   归属校验 → (待支付时)让策略主动查 → 金额核对 → CAS 改状态 → 通知 order
 *
 * 金额核对原先在"回调"和"主动查询"里各写一遍, 现已统一收进 applyPaidIfAmountMatch 一处。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements IPaymentService {

    private final PaymentMapper paymentMapper;
    private final PaymentNotifyLogMapper notifyLogMapper;
    private final OrderFeignClient orderFeignClient;
    private final PayChannelStrategyFactory strategyFactory;   // 按渠道取策略

    // ════════════════════════════════════════════════════════════
    // ① 创建支付单
    // ════════════════════════════════════════════════════════════
    @Override
    public String create(CreatePayDTO dto) {
        // ① 当前登录用户
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (dto == null || dto.getOrderId() == null) {
            throw new BusinessException(400, "缺少订单号");
        }

        // ①.5 解析渠道并取对应策略 (不传默认支付宝; 不认识的渠道名 ofName 会抛)
        PayChannel channel = PayChannel.ofName(dto.getChannel());
        PayChannelStrategy strategy = strategyFactory.get(channel);

        // ② Feign 查订单 (FeignAuthInterceptor 自动透传 X-User-Id, order 会校验归属)
        Result<OrderInfo> resp = orderFeignClient.getOrder(dto.getOrderId());
        if (resp == null || resp.getCode() == null || resp.getCode() != 200 || resp.getData() == null) {
            // order 返 403(不是你的单)/404(不存在)/503(服务挂) 都到这
            throw new BusinessException(400, resp == null ? "查询订单失败" : resp.getMessage());
        }
        OrderInfo order = resp.getData();

        // ③ ⭐ 状态校验: 只有"待付款(0)"才能发起支付。
        //    已付款重复付=错, 已取消付款=错 —— 这道闸挡住这些非法态。
        if (order.getStatus() == null || order.getStatus() != 0) {
            throw new BusinessException(400, "订单当前状态不可支付");
        }

        // ③.5 ⭐ 金额权威来源: 用 order 返回的 totalAmount, 绝不用前端传的任何金额。
        BigDecimal amount = order.getTotalAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "订单金额异常");
        }

        // ─────────────────────────────────────────────────────────
        // ④ 建支付单 (幂等复用: 同订单+同渠道已有待支付单就复用, 不重复建)
        //    渠道那边 out_trade_no(=paymentNo) 相同会认成同一笔, 天然不会重复扣钱。
        // ─────────────────────────────────────────────────────────
        // 查"该订单 + 该渠道 + 待支付(status=0)"的那一条; orderByDesc+limit 1 防历史多条报错
        QueryWrapper<Payment> qw = new QueryWrapper<>();
        qw.eq("order_id", order.getOrderId())
          .eq("channel", channel.getCode())
          .eq("status", (byte) 0)
          .orderByDesc("id")
          .last("limit 1");
        Payment existing = paymentMapper.selectOne(qw);   // ⚠ 查不到会返回 null, 下面必须判空

        String paymentNo;
        if (existing != null) {
            // 复用: 直接拿旧单号, 【不再 insert】(它已经在库里了, 再插会主键冲突)
            paymentNo = existing.getPaymentNo();
            log.info("[pay-create] 复用已有待支付单 paymentNo={} orderNo={} channel={}",
                    paymentNo, order.getOrderNo(), channel);
        } else {
            // 新建: new 一个干净对象(id 为空才能走 AUTO 自增), 再 insert
            paymentNo = genPaymentNo(userId);
            Payment payment = new Payment();
            payment.setPaymentNo(paymentNo);
            payment.setOrderId(order.getOrderId());
            payment.setOrderNo(order.getOrderNo());
            payment.setUserId(userId);
            payment.setAmount(amount);
            payment.setChannel(channel.getCode());   // 落库当初选的渠道, queryStatus 时靠它反查策略
            payment.setStatus((byte) 0);              // 0=待支付
            paymentMapper.insert(payment);
            log.info("[pay-create] 新建支付单 paymentNo={} orderNo={} amount={} channel={}",
                    paymentNo, order.getOrderNo(), amount, channel);
        }

        // ⑤ 让渠道策略生成支付页 (新建/复用都走这里, 只有一个出口)
        return strategy.createPayForm(paymentNo, amount, order.getOrderNo());
    }

    // ════════════════════════════════════════════════════════════
    // ② 异步回调 (策略验签解析 → 编排层落库: 幂等 → 金额核对+CAS → 通知 order)
    // ════════════════════════════════════════════════════════════
    @Override
    public String handleNotify(PayChannel channel, Map<String, String> params) {
        PayChannelStrategy strategy = strategyFactory.get(channel);

        // ── 第 1 步: 交给策略验签 + 解析, 拿归一化结果 ──────────────
        PayNotifyResult result = strategy.verifyAndParse(params);
        switch (result.getStatus()) {
            case INVALID:
                // 验签失败/app 不符/异常 → 让渠道稍后重发
                return "failure";
            case IGNORED:
                // 验签过但非成功终态 → 收到了, 别再重发, 但不处理
                return "success";
            default:
                // SUCCESS → 往下走落库
                break;
        }

        try {
            // ── 第 2 步: 幂等 (notify_id 唯一索引) ─────────────────
            // 渠道为确保送达会重复发同一条通知。先把这条落"黑匣子", notify_id 唯一键:
            //   插入成功 = 第一次见, 继续处理; DuplicateKeyException = 处理过 → 回 success 让它别再发。
            PaymentNotifyLog logRow = new PaymentNotifyLog();
            logRow.setNotifyId(result.getNotifyId());
            logRow.setNotifyType((byte) 1);              // 1=支付回调
            logRow.setOutTradeNo(result.getPaymentNo());
            logRow.setTradeNo(result.getTradeNo());
            logRow.setTradeStatus(result.getRawStatus());
            logRow.setRawBody(params.toString());
            logRow.setVerifyResult((byte) 1);            // 验签已通过
            try {
                notifyLogMapper.insert(logRow);
            } catch (DuplicateKeyException dup) {
                log.info("[pay-notify] 通知已处理过(幂等), 直接返回 success notifyId={}", result.getNotifyId());
                return "success";
            }

            // ── 第 3 步: 查支付单 ─────────────────────────────────
            Payment payment = getByPaymentNo(result.getPaymentNo());
            if (payment == null) {
                log.warn("[pay-notify] 支付单不存在 outTradeNo={}", result.getPaymentNo());
                return "failure";
            }

            // ── 第 4 步: 金额核对 + CAS 改状态 + 通知 order (与主动查询共用) ──
            //    金额不一致会被 applyPaidIfAmountMatch 挡下并回 false, 这里转成 failure。
            boolean ok = applyPaidIfAmountMatch(payment, result.getAmount(), result.getTradeNo());
            return ok ? "success" : "failure";

        } catch (Exception e) {
            log.error("[pay-notify] 落库处理异常 outTradeNo={}", result.getPaymentNo(), e);
            return "failure";
        }
    }

    // ════════════════════════════════════════════════════════════
    // ③ 支付状态查询 (前端轮询)
    // ════════════════════════════════════════════════════════════
    @Override
    public PayStatusVO queryStatus(Long orderId) {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        // ⭐ 归属校验直接写进查询条件: user_id=当前用户, 查不到就是"不是你的单/还没建单"
        QueryWrapper<Payment> qw = new QueryWrapper<>();
        qw.eq("order_id", orderId).eq("user_id", userId)
          .orderByDesc("id").last("limit 1");
        Payment payment = paymentMapper.selectOne(qw);

        PayStatusVO vo = new PayStatusVO();
        vo.setOrderId(orderId);
        if (payment == null) {
            // 还没建过支付单 (下单后没点支付), 返回 null 状态 + 未支付
            vo.setStatus(null);
            vo.setStatusDesc("未创建支付");
            vo.setPaid(false);
            return vo;
        }

        // ⭐ 主动查询兜底: DB 还是待支付(0)时, 别干等异步回调 —— 沙箱回调经常不发/发慢,
        //    让对应渠道策略去问"这单到底付了没", 已付就地落库。生产里它也是对账的一部分。
        if (payment.getStatus() != null && payment.getStatus() == 0) {
            PayChannelStrategy strategy = strategyFactory.get(PayChannel.ofCode(payment.getChannel()));
            PayQueryResult qr = strategy.query(payment.getPaymentNo());
            if (qr.isPaid()) {
                applyPaidIfAmountMatch(payment, qr.getAmount(), qr.getTradeNo());
            }
            payment = getByPaymentNo(payment.getPaymentNo());  // 上一步可能已翻成 1, 重读拿最新态
        }

        vo.setPaymentNo(payment.getPaymentNo());
        vo.setStatus(payment.getStatus());
        vo.setStatusDesc(statusDesc(payment.getStatus()));
        vo.setPaid(payment.getStatus() != null && payment.getStatus() == 1);
        return vo;
    }

    // ════════════════════════════════════════════════════════════
    // 通用私有方法
    // ════════════════════════════════════════════════════════════

    /** 支付单状态码翻译 */
    private String statusDesc(Byte status) {
        if (status == null) return "未知";
        switch (status) {
            case 0:  return "待支付";
            case 1:  return "已支付";
            case 2:  return "已关闭";
            case 3:  return "退款中";
            case 4:  return "已退款";
            default: return "未知";
        }
    }

    /** 按支付单号查支付单 */
    private Payment getByPaymentNo(String paymentNo) {
        QueryWrapper<Payment> qw = new QueryWrapper<>();
        qw.eq("payment_no", paymentNo).last("limit 1");
        return paymentMapper.selectOne(qw);
    }

    /**
     * 金额核对 → CAS 改支付单 0→1 → (真翻状态时)通知 order 标记已付款。
     * <p>
     * 异步回调、主动查询两条路【共用】它, 保证核对与落库行为完全一致:
     *   - 金额核对: 渠道返回金额必须与建单金额一致, 不一致直接拒绝 (防"改小金额"类篡改)。
     *   - WHERE status=0 的 CAS 保证并发/重复下只生效一次 (业务幂等最后一道)。
     *   - rows==0 说明别的线程/别条路已付过了, 直接返回、不重复通知 order。
     *   - 只有真正翻状态成功 (rows>0) 才通知 order, 避免重复 markPaid。
     *
     * @return true=金额一致且已处理妥当(含"已被处理过"); false=金额不一致, 调用方应回 failure
     */
    private boolean applyPaidIfAmountMatch(Payment payment, BigDecimal channelAmount, String tradeNo) {
        // ⭐ 金额核对
        if (channelAmount == null || channelAmount.compareTo(payment.getAmount()) != 0) {
            log.error("[pay-paid] 金额不一致! 渠道={} 建单={} paymentNo={}",
                    channelAmount, payment.getAmount(), payment.getPaymentNo());
            return false;
        }

        // ⭐ CAS 0→1
        UpdateWrapper<Payment> uw = new UpdateWrapper<>();
        uw.eq("payment_no", payment.getPaymentNo()).eq("status", 0)
          .set("status", 1)
          .set("trade_no", tradeNo)
          .set("notify_time", LocalDateTime.now());
        int rows = paymentMapper.update(null, uw);
        if (rows == 0) {
            log.info("[pay-paid] 支付单已是已支付态(幂等), paymentNo={}", payment.getPaymentNo());
            return true;
        }

        // ⚠ 若这步失败(order 挂), 会出现"钱到账、支付单已支付, 但订单还待付款"的不一致。
        //   仍不回滚支付单(钱确实到了), 只 log 标记, 靠定时对账 job 补 markPaid。
        try {
            Result<Boolean> r = orderFeignClient.markPaid(payment.getOrderId());
            if (r == null || r.getCode() == null || r.getCode() != 200) {
                log.error("[pay-paid] 通知 order 失败, 待对账补偿 orderId={}", payment.getOrderId());
            } else {
                log.info("[pay-paid] 支付完成并已通知 order orderId={} changed={}",
                        payment.getOrderId(), r.getData());
            }
        } catch (Exception e) {
            log.error("[pay-paid] 通知 order 异常, 待对账补偿 orderId={}", payment.getOrderId(), e);
        }
        return true;
    }

    /** 生成支付单号: PAY + 时间 + userId + 4位随机, 保证唯一 */
    private String genPaymentNo(Long userId) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "PAY" + ts + userId + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
