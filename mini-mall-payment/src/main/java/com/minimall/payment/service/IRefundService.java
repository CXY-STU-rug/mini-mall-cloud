package com.minimall.payment.service;

import com.minimall.payment.dto.RefundApplyDTO;
import com.minimall.payment.entity.Refund;

import java.util.List;

/**
 * 退款服务。
 * ⭐ 关键认知: 支付宝退款 alipay.trade.refund 是【同步】接口, execute 当场返回结果, 不需要 notify_url。
 *
 * 改造后是【两段式】(需求: 退款要客服审批, 不能秒退):
 *   ① 用户 apply()   —— 只建退款单(待审批) + 订单进"申请退款中", 不碰支付宝
 *   ② 客服 approve() —— 真正调支付宝退款 + 订单进"已退款"
 *      客服 reject()  —— 驳回, 订单回滚到原状态
 */
public interface IRefundService {

    /**
     * 用户申请退款 (V1 全额退)。只登记申请, 不立即退款。
     * @param dto 含 orderId + reason
     * @return true=申请已提交(订单进入"申请退款中")
     */
    boolean apply(RefundApplyDTO dto);

    /** 客服端: 列出所有待审批(status=0)的退款申请。 */
    List<Refund> listPending();

    /**
     * 客服批准退款: 调支付宝真退款 + 订单标记已退款。
     * @param refundId 退款单 id
     * @return true=退款成功
     */
    boolean approve(Long refundId);

    /**
     * 客服拒绝退款: 退款单标记已拒绝 + 订单回滚到申请前状态。
     * @param refundId 退款单 id
     * @return true=已拒绝
     */
    boolean reject(Long refundId);
}
