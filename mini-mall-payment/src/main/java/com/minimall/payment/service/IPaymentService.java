package com.minimall.payment.service;

import com.minimall.payment.dto.CreatePayDTO;
import com.minimall.payment.vo.PayStatusVO;

import java.util.Map;

/**
 * 支付服务接口。
 * Phase 2 create(创建支付单); Phase 3 handleNotify(异步回调); Phase 4 状态查询后续加。
 */
public interface IPaymentService {

    /**
     * 创建支付单并生成支付宝支付页表单。
     * @param dto 含 orderId
     * @return 支付宝返回的自动提交表单 HTML (前端 document.write 即跳转支付宝收银台)
     */
    String create(CreatePayDTO dto);

    /**
     * 处理支付宝【异步】支付回调 (Phase 3 核心)。
     * @param params 支付宝 POST 过来的全部表单参数
     * @return 必须返回纯文本 "success"(已收妥, 别再发) 或 "failure"(没处理好, 支付宝会重发)
     */
    String handleNotify(Map<String, String> params);

    /**
     * 查某订单的支付状态 (Phase 4, 前端轮询用)。只能查自己的单。
     * @param orderId 订单 id
     * @return 含 status/paid 的 VO
     */
    PayStatusVO queryStatus(Long orderId);
}
