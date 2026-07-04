package com.minimall.payment.controller;

import com.minimall.common.core.domain.Result;
import com.minimall.payment.dto.CreatePayDTO;
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
 * 端点(Phase 2 先 1 个, 后续加):
 *   POST /pay/create   创建支付单 + 拿支付宝支付页表单 (C 端用户发起, 需登录)
 * <p>
 * 网关侧(Phase 7 要配): /pay/create 是用户写操作 → 进 isCEndWrite 白名单(带 token);
 *   而后面的 /pay/notify 是支付宝服务器调、无 token → 进 WHITE_LIST 完全免鉴权。
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
     * 支付宝【异步】支付回调。
     * <p>
     * 注意几点跟普通接口不一样:
     *   - 调用方是支付宝服务器, 没有 JWT → 网关 Phase7 要把 /pay/notify 放进 WHITE_LIST 完全免鉴权
     *   - 支付宝用 form 表单 POST, 参数从 request.getParameterMap() 拿, 不是 @RequestBody
     *   - 返回【纯文本】 "success"/"failure" (不是 Result JSON!), 支付宝只认这两个词决定要不要重发
     */
    @PostMapping("/notify")
    public String notify(HttpServletRequest request) {
        // 把支付宝 POST 过来的所有 form 参数收进 Map<String,String>
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, v.length > 0 ? v[0] : ""));
        return paymentService.handleNotify(params);
    }

    /** 查支付状态 (前端下单跳支付宝后, 每隔一两秒轮询, paid=true 就跳成功页)。 */
    @GetMapping("/status/{orderId}")
    public Result<PayStatusVO> status(@PathVariable("orderId") Long orderId) {
        return Result.success(paymentService.queryStatus(orderId));
    }
}
