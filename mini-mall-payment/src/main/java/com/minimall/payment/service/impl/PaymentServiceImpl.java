package com.minimall.payment.service.impl;

import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.minimall.common.core.context.SecurityContextHolder;
import com.minimall.common.core.domain.Result;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.payment.client.OrderFeignClient;
import com.minimall.payment.client.dto.OrderInfo;
import com.minimall.payment.config.AlipayProperties;
import com.minimall.payment.dto.CreatePayDTO;
import com.minimall.payment.entity.Payment;
import com.minimall.payment.entity.PaymentNotifyLog;
import com.minimall.payment.mapper.PaymentMapper;
import com.minimall.payment.mapper.PaymentNotifyLogMapper;
import com.minimall.payment.service.IPaymentService;
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
 * 支付服务实现 (Phase 2: 创建支付单)。
 *
 * create 流程 (每一步都有安全意图):
 *   ① 拿当前用户 (SecurityContextHolder, 由网关透传的 X-User-Id 塞进来的)
 *   ② Feign 查订单 —— 金额和归属都以 order 为准, 前端说了不算
 *   ③ 校验: 订单存在 / 是待付款状态
 *   ④ 建支付单 payment (status=0 待支付)
 *   ⑤ 调支付宝生成支付页表单, 返给前端跳转
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements IPaymentService {

    private final PaymentMapper paymentMapper;
    private final PaymentNotifyLogMapper notifyLogMapper;
    private final OrderFeignClient orderFeignClient;
    private final AlipayClient alipayClient;       // Phase 1 配好的"发动机"
    private final AlipayProperties props;

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
        // ④ 建支付单 (幂等复用: 已有待支付单就复用, 不重复建)
        //    支付宝那边 out_trade_no(=paymentNo) 相同会认成同一笔, 天然不会重复扣钱。
        // ─────────────────────────────────────────────────────────
        // 查"该订单 + 待支付(status=0)"的那一条; orderByDesc+limit 1 防历史多条报错
        QueryWrapper<Payment> qw = new QueryWrapper<>();
        qw.eq("order_id", order.getOrderId())
          .eq("status", (byte) 0)
          .orderByDesc("id")
          .last("limit 1");
        Payment existing = paymentMapper.selectOne(qw);   // ⚠ 查不到会返回 null, 下面必须判空

        String paymentNo;
        if (existing != null) {
            // 复用: 直接拿旧单号, 【不再 insert】(它已经在库里了, 再插会主键冲突)
            paymentNo = existing.getPaymentNo();
            log.info("[pay-create] 复用已有待支付单 paymentNo={} orderNo={}", paymentNo, order.getOrderNo());
        } else {
            // 新建: new 一个干净对象(id 为空才能走 AUTO 自增), 再 insert
            paymentNo = genPaymentNo(userId);
            Payment payment = new Payment();
            payment.setPaymentNo(paymentNo);
            payment.setOrderId(order.getOrderId());
            payment.setOrderNo(order.getOrderNo());
            payment.setUserId(userId);
            payment.setAmount(amount);
            payment.setChannel((byte) 1);     // 1=支付宝
            payment.setStatus((byte) 0);      // 0=待支付
            paymentMapper.insert(payment);
            log.info("[pay-create] 新建支付单 paymentNo={} orderNo={} amount={}", paymentNo, order.getOrderNo(), amount);
        }

        // ⑤ 调支付宝生成"电脑网站支付"页面表单 (新建/复用都走这里, 只有一个出口)
        return buildAlipayForm(paymentNo, amount, order.getOrderNo());
    }


    // ════════════════════════════════════════════════════════════
    // Phase 3: 支付宝异步回调 (四关: 验签 → 幂等 → CAS 改支付单 → 通知 order)
    // ════════════════════════════════════════════════════════════
    @Override
    public String handleNotify(Map<String, String> params) {
        String outTradeNo = params.get("out_trade_no");   // 我方支付单号
        String tradeNo    = params.get("trade_no");       // 支付宝交易号
        String tradeStatus= params.get("trade_status");
        String notifyId   = params.get("notify_id");
        log.info("[pay-notify] 收到回调 outTradeNo={} tradeStatus={} notifyId={}", outTradeNo, tradeStatus, notifyId);

        try {
            // ── 第 1 关: 验签 (RSA2) ──────────────────────────────
            // 用"支付宝公钥"验这批参数的签名。防的是: 有人伪造一个"支付成功"的 POST 打过来白拿货。
            // rsaCheckV1 会自动剔除 sign / sign_type 再验, 我们不用手动处理。
            boolean signOk = AlipaySignature.rsaCheckV1(
                    params, props.getAlipayPublicKey(), props.getCharset(), props.getSignType());
            if (!signOk) {
                log.warn("[pay-notify] 验签失败, 疑似伪造回调 outTradeNo={}", outTradeNo);
                return "failure";   // 不是支付宝发的, 拒绝
            }

            // ── 第 2 关: 幂等 (notify_id 唯一索引) ─────────────────
            // 支付宝为确保送达会重复发同一条通知。先把这条通知落"黑匣子", notify_id 唯一键:
            //   插入成功 = 第一次见这条通知, 继续处理;
            //   DuplicateKeyException = 之前处理过 = 直接回 success 让它别再发。
            PaymentNotifyLog logRow = new PaymentNotifyLog();
            logRow.setNotifyId(notifyId);
            logRow.setNotifyType((byte) 1);       // 1=支付回调
            logRow.setOutTradeNo(outTradeNo);
            logRow.setTradeNo(tradeNo);
            logRow.setTradeStatus(tradeStatus);
            logRow.setRawBody(params.toString());
            logRow.setVerifyResult((byte) 1);     // 验签已通过
            try {
                notifyLogMapper.insert(logRow);
            } catch (DuplicateKeyException dup) {
                log.info("[pay-notify] 通知已处理过(幂等), 直接返回 success notifyId={}", notifyId);
                return "success";
            }

            // ── 第 3 关: 业务校验 (app_id / 交易状态 / 单据 / 金额) ──
            // app_id 得是我们自己的应用, 防"别的应用的回调"串进来
            if (!props.getAppId().equals(params.get("app_id"))) {
                log.warn("[pay-notify] app_id 不匹配 outTradeNo={}", outTradeNo);
                return "failure";
            }
            // 只认"支付成功/交易完成"两个终态, 其余(WAIT_BUYER_PAY 等)不处理但也回 success 免重发
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                log.info("[pay-notify] 非成功态, 忽略 tradeStatus={}", tradeStatus);
                return "success";
            }
            // 查支付单
            Payment payment = getByPaymentNo(outTradeNo);
            if (payment == null) {
                log.warn("[pay-notify] 支付单不存在 outTradeNo={}", outTradeNo);
                return "failure";
            }
            // ⭐ 金额校验: 回调金额必须跟我们建单时的金额一致。防"改小金额"类篡改。
            BigDecimal notifyAmount = new BigDecimal(params.get("total_amount"));
            if (notifyAmount.compareTo(payment.getAmount()) != 0) {
                log.error("[pay-notify] 金额不一致! 回调={} 建单={} outTradeNo={}",
                        notifyAmount, payment.getAmount(), outTradeNo);
                return "failure";
            }

            // ── 第 4 关: CAS 改支付单 0→1 (业务幂等的最后一道) ──────
            // 即便前面 notify_id 那层被绕过, 这里 WHERE status=0 也保证只有一次真正生效。
            UpdateWrapper<Payment> uw = new UpdateWrapper<>();
            uw.eq("payment_no", outTradeNo).eq("status", 0)
              .set("status", 1)
              .set("trade_no", tradeNo)
              .set("notify_time", LocalDateTime.now());
            int rows = paymentMapper.update(null, uw);
            if (rows == 0) {
                log.info("[pay-notify] 支付单已是已支付态(幂等), outTradeNo={}", outTradeNo);
                return "success";
            }

            // ── 收尾: Feign 通知 order 标记已付款 ─────────────────
            // ⚠ 若这步失败(order 挂), 会出现"钱到账、支付单已支付, 但订单还待付款"的不一致。
            //    这里只 log.error 标记, 仍回 success(钱确实到了, 不该让支付宝重发)。
            //    生产靠定时对账 job: 扫 payment.status=1 但 order 未付款的, 补一次 markPaid。
            try {
                Result<Boolean> r = orderFeignClient.markPaid(payment.getOrderId());
                if (r == null || r.getCode() == null || r.getCode() != 200) {
                    log.error("[pay-notify] 通知 order 失败, 待对账补偿 orderId={}", payment.getOrderId());
                } else {
                    log.info("[pay-notify] 支付完成并已通知 order orderId={} changed={}",
                            payment.getOrderId(), r.getData());
                }
            } catch (Exception e) {
                log.error("[pay-notify] 通知 order 异常, 待对账补偿 orderId={}", payment.getOrderId(), e);
            }
            return "success";

        } catch (Exception e) {
            // 验签本身也可能抛异常; 兜底回 failure 让支付宝稍后重发
            log.error("[pay-notify] 处理回调异常 outTradeNo={}", outTradeNo, e);
            return "failure";
        }
    }

    // ════════════════════════════════════════════════════════════
    // Phase 4: 支付状态查询 (前端轮询)
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
        vo.setPaymentNo(payment.getPaymentNo());
        vo.setStatus(payment.getStatus());
        vo.setStatusDesc(statusDesc(payment.getStatus()));
        vo.setPaid(payment.getStatus() != null && payment.getStatus() == 1);
        return vo;
    }

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
     * 组装并调用支付宝"电脑网站支付"(alipay.trade.page.pay), 返回自动提交表单 HTML。
     */
    private String buildAlipayForm(String paymentNo, BigDecimal amount, String orderNo) {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        // 异步回调(支付宝→我方, 改状态只认它)和同步跳回(给用户看)
        request.setNotifyUrl(props.getNotifyUrl());
        request.setReturnUrl(props.getReturnUrl());

        // bizContent = 本次交易的业务参数, 手拼 JSON (字段少, 不引 JSON 库)
        //   out_trade_no 用我方支付单号; total_amount 金额; subject 标题; product_code 固定值
        String bizContent = String.format(
                "{\"out_trade_no\":\"%s\",\"total_amount\":\"%s\",\"subject\":\"订单%s\",\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}",
                paymentNo, amount.toPlainString(), orderNo);
        request.setBizContent(bizContent);

        try {
            // pageExecute: 生成一段带自动提交 <form> 的 HTML, 前端 document.write 即跳到支付宝收银台
            String form = alipayClient.pageExecute(request).getBody();
            log.info("[pay-create] 已生成支付宝支付页 paymentNo={}", paymentNo);
            return form;
        } catch (Exception e) {
            log.error("[pay-create] 调支付宝生成支付页失败 paymentNo={}", paymentNo, e);
            throw new BusinessException(500, "生成支付页失败: " + e.getMessage());
        }
    }

    /** 生成支付单号: PAY + 时间 + userId + 4位随机, 保证唯一 */
    private String genPaymentNo(Long userId) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "PAY" + ts + userId + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
