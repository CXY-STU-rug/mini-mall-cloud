package com.minimall.payment.controller;

import com.minimall.common.core.domain.Result;
import com.minimall.payment.dto.RefundApplyDTO;
import com.minimall.payment.service.IRefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退款 Controller。
 * <p>
 * POST /refund/apply  用户申请退款 (C 端写操作, 网关 isCEndWrite 白名单带 token)。
 * 退款是同步的, 不需要像支付那样有 /refund/notify (虽然网关白名单预留了, V1 没用上)。
 */
@RestController
@RequestMapping("/refund")
@RequiredArgsConstructor
public class RefundController {

    private final IRefundService refundService;

    @PostMapping("/apply")
    public Result<Boolean> apply(@RequestBody RefundApplyDTO dto) {
        return Result.success(refundService.apply(dto));
    }
}
