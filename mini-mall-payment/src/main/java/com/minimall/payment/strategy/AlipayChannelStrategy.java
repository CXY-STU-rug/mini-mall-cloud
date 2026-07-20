package com.minimall.payment.strategy;

import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.payment.config.AlipayProperties;
import com.minimall.payment.enums.PayChannel;
import com.minimall.payment.strategy.dto.PayNotifyResult;
import com.minimall.payment.strategy.dto.PayQueryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝渠道策略。
 *
 * 把原 PaymentServiceImpl 里跟支付宝绑死的三段逻辑搬到这里, 各自翻译成归一化结果:
 *   createPayForm   ← 原 buildAlipayForm
 *   verifyAndParse  ← 原 handleNotify 的"验签 + app_id + trade_status + 解析字段"部分
 *   query           ← 原 queryAndSyncFromAlipay 的"调支付宝查询 + 解析"部分
 *
 * 注意本类【只跟支付宝交互 + 解析】, 不碰数据库、不通知 order —— 那些通用编排留在 Service。
 * 私钥签名、支付宝公钥验签这对非对称密钥, 是整个支付安全的地基。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlipayChannelStrategy implements PayChannelStrategy {

    private final AlipayClient alipayClient;     // Phase 1 配好的"发动机"
    private final AlipayProperties props;

    @Override
    public PayChannel getChannel() {
        return PayChannel.ALIPAY;
    }

    // ════════════════════════════════════════════════════════════
    // ① 生成支付页 (电脑网站支付 alipay.trade.page.pay)
    // ════════════════════════════════════════════════════════════
    @Override
    public String createPayForm(String paymentNo, BigDecimal amount, String orderNo) {
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

    // ════════════════════════════════════════════════════════════
    // ② 验签 + 解析异步回调 (只判真伪与付没付, 不落库)
    // ════════════════════════════════════════════════════════════
    @Override
    public PayNotifyResult verifyAndParse(Map<String, String> params) {
        String outTradeNo = params.get("out_trade_no");   // 我方支付单号
        String tradeNo    = params.get("trade_no");       // 支付宝交易号
        String tradeStatus= params.get("trade_status");
        String notifyId   = params.get("notify_id");
        log.info("[pay-notify] 收到支付宝回调 outTradeNo={} tradeStatus={} notifyId={}",
                outTradeNo, tradeStatus, notifyId);

        try {
            // ── 验签 (RSA2) ──────────────────────────────────────
            // 用"支付宝公钥"验这批参数的签名。防的是: 有人伪造一个"支付成功"的 POST 打过来白拿货。
            // rsaCheckV1 会自动剔除 sign / sign_type 再验, 不用手动处理。
            boolean signOk = AlipaySignature.rsaCheckV1(
                    params, props.getAlipayPublicKey(), props.getCharset(), props.getSignType());
            if (!signOk) {
                log.warn("[pay-notify] 验签失败, 疑似伪造回调 outTradeNo={}", outTradeNo);
                return PayNotifyResult.invalid();
            }

            // ── app_id 校验 ──────────────────────────────────────
            // 得是我们自己的应用, 防"别的应用的回调"串进来
            if (!props.getAppId().equals(params.get("app_id"))) {
                log.warn("[pay-notify] app_id 不匹配 outTradeNo={}", outTradeNo);
                return PayNotifyResult.invalid();
            }

            // ── 交易状态终态判断 ──────────────────────────────────
            // 只认"支付成功/交易完成"两个终态, 其余(WAIT_BUYER_PAY 等)收到但不处理
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                log.info("[pay-notify] 非成功态, 忽略 tradeStatus={}", tradeStatus);
                return PayNotifyResult.ignored();
            }

            // ── 解析金额, 归一化返回 (金额核对由编排层统一做) ──────────
            BigDecimal amount = new BigDecimal(params.get("total_amount"));
            return PayNotifyResult.success(outTradeNo, tradeNo, amount, notifyId, tradeStatus);

        } catch (Exception e) {
            // 验签本身也可能抛异常; 归一化为 INVALID, 让编排层回 failure 令支付宝重发
            log.error("[pay-notify] 处理支付宝回调异常 outTradeNo={}", outTradeNo, e);
            return PayNotifyResult.invalid();
        }
    }

    // ════════════════════════════════════════════════════════════
    // ③ 主动查询 (alipay.trade.query), 回调靠不住时的兜底
    // ════════════════════════════════════════════════════════════
    @Override
    public PayQueryResult query(String paymentNo) {
        try {
            AlipayTradeQueryRequest req = new AlipayTradeQueryRequest();
            // 用我方支付单号(out_trade_no)问, 支付宝按它定位这笔交易
            req.setBizContent(String.format("{\"out_trade_no\":\"%s\"}", paymentNo));
            AlipayTradeQueryResponse resp = alipayClient.execute(req);

            if (!resp.isSuccess()) {
                // 常见 ACQ.TRADE_NOT_EXIST = 用户还没付/查无此单, 属正常情形, 不当错误
                log.info("[pay-query] 支付宝查询未成功 paymentNo={} subCode={} subMsg={}",
                        paymentNo, resp.getSubCode(), resp.getSubMsg());
                return PayQueryResult.notPaid();
            }
            String tradeStatus = resp.getTradeStatus();
            log.info("[pay-query] 支付宝返回 paymentNo={} tradeStatus={} tradeNo={}",
                    paymentNo, tradeStatus, resp.getTradeNo());

            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                BigDecimal amount = resp.getTotalAmount() == null ? null
                        : new BigDecimal(resp.getTotalAmount());
                return PayQueryResult.paid(resp.getTradeNo(), amount);
            }
            return PayQueryResult.notPaid();

        } catch (Exception e) {
            log.error("[pay-query] 主动查询支付宝异常 paymentNo={}", paymentNo, e);
            return PayQueryResult.notPaid();
        }
    }
}
