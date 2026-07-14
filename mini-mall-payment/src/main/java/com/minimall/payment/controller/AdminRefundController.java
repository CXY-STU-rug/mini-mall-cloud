package com.minimall.payment.controller;

import com.minimall.common.core.domain.Result;
import com.minimall.payment.entity.Refund;
import com.minimall.payment.service.IRefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客服/管理端退款审批 Controller (退款改造新增)。
 *
 * ⭐ 为什么挂 /admin/refund 而不是 /refund/admin:
 *   网关 AuthGlobalFilter.isCEndWrite() 把【任何 /refund/** 写操作】都当成"用户本人可写",
 *   若审批接口叫 /refund/admin/approve, 普通用户就能自己批准自己的退款 = 严重越权。
 *   改挂 /admin/ 前缀后, 网关 needAdmin() 会强制 role=1, 只有管理员能调。
 *   (还需在 gateway 加一条路由 /admin/refund/** → mini-mall-payment)
 *
 * 端点:
 *   GET  /admin/refund/list            列出待审批的退款申请
 *   POST /admin/refund/approve/{id}    批准 → 真退款
 *   POST /admin/refund/reject/{id}     拒绝 → 订单回滚
 */
@RestController
@RequestMapping("/admin/refund")
@RequiredArgsConstructor
public class AdminRefundController {

    private final IRefundService refundService;

    /** 待审批退款列表 (status=0) */
    @GetMapping("/list")
    public Result<List<Refund>> list() {
        return Result.success(refundService.listPending());
    }

    /** 批准退款: 调支付宝真退款, 订单 → 已退款(6) */
    @PostMapping("/approve/{refundId}")
    public Result<Boolean> approve(@PathVariable("refundId") Long refundId) {
        return Result.success(refundService.approve(refundId));
    }

    /** 拒绝退款: 退款单标已拒绝, 订单回滚到原状态(1/2) */
    @PostMapping("/reject/{refundId}")
    public Result<Boolean> reject(@PathVariable("refundId") Long refundId) {
        return Result.success(refundService.reject(refundId));
    }
}
