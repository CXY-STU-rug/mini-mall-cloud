package com.minimall.payment.controller;

import com.minimall.common.core.domain.Result;
import com.minimall.payment.dto.CreatePayDTO;
import com.minimall.payment.enums.PayChannel;
import com.minimall.payment.service.IPaymentService;
import com.minimall.payment.vo.PayStatusVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付 Controller。
 * <p>
 * 端点:
 *   POST /pay/create            创建支付单 + 拿支付页 (C 端用户发起, 需登录; 渠道由 dto.channel 决定)
 *   POST /pay/notify/{channel}  渠道异步回调 (按渠道分入口, 如 /pay/notify/alipay; 渠道服务器调, 无 token)
 *   GET  /pay/status/{orderId}  查支付状态 (前端轮询)
 * <p>
 * 网关侧: /pay/create 是用户写操作 → 进 isCEndWrite 白名单(带 token);
 *   /pay/notify/** 是渠道服务器调、无 token → 进 WHITE_LIST 完全免鉴权
 *   (白名单按 startsWith("/pay/notify") 匹配, 天然覆盖 /pay/notify/alipay)。
 */
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PaymentController {

    private final IPaymentService paymentService;

    /**
     * 创建支付单, 返回支付宝自动提交表单 HTML。
     * 前端拿到 data 后 document.write(data) 即自动跳转支付宝收银台。
     */
    @PostMapping("/create")
    public Result<String> create(@RequestBody CreatePayDTO dto) {
        String form = paymentService.create(dto);
        return Result.success(form);
    }

    /**
     * 【异步】支付回调 (按渠道分入口: /pay/notify/alipay、/pay/notify/wechat ...)。
     * <p>
     * 为什么按 {channel} 分路径: 每家支付的回调 URL 和报文格式天生不同, 各配各的回调地址,
     *   由路径直接确定用哪个渠道策略验签解析。新增渠道 = 新加一个策略类, 本端点无需改动。
     * <p>
     * 注意几点跟普通接口不一样:
     *   - 调用方是渠道服务器, 没有 JWT → 网关要把 /pay/notify/** 放进 WHITE_LIST 完全免鉴权
     *   - 渠道用 form 表单 POST, 参数从 request.getParameterMap() 拿, 不是 @RequestBody
     *   - 返回【纯文本】 "success"/"failure" (不是 Result JSON!), 渠道只认这两个词决定要不要重发
     */
    @PostMapping("/notify/{channel}")
    public String notify(@PathVariable("channel") String channel, HttpServletRequest request) {
        // 把渠道 POST 过来的所有 form 参数收进 Map<String,String>
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, v.length > 0 ? v[0] : ""));
        // 路径里的渠道名解析成枚举 (不认识的渠道名会抛异常 → 回调方收到非 success 会重发, 可从日志排查)
        return paymentService.handleNotify(PayChannel.ofName(channel), params);
    }

    /** 查支付状态 (前端下单跳支付宝后, 每隔一两秒轮询, paid=true 就跳成功页)。 */
    @GetMapping("/status/{orderId}")
    public Result<PayStatusVO> status(@PathVariable("orderId") Long orderId) {
        return Result.success(paymentService.queryStatus(orderId));
    }
}
