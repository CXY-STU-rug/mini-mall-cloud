package com.minimall.payment.service;

import com.minimall.payment.dto.RefundApplyDTO;

/**
 * 退款服务 (Phase 5-6)。
 * ⭐ 关键认知: 支付宝退款 alipay.trade.refund 是【同步】接口, execute 当场返回结果,
 *   不像支付要异步回调。所以退款一个 apply 接口就能闭环, 不需要 notify_url。
 */
public interface IRefundService {

    /**
     * 申请退款 (V1 全额退, 同步执行)。
     * @param dto 含 orderId + reason
     * @return 是否退款成功
     */
    boolean apply(RefundApplyDTO dto);
}
