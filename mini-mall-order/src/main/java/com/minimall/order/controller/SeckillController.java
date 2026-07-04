package com.minimall.order.controller;

import com.minimall.common.core.context.SecurityContextHolder;
import com.minimall.common.core.domain.Result;
import com.minimall.order.dto.SeckillActivityDTO;
import com.minimall.order.service.ISeckillActivityService;
import com.minimall.order.vo.SeckillActivityVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 秒杀 Controller (G3.8)
 *
 * 端点 (网关代理 /seckill/** → mini-mall-order):
 *   POST /seckill/activity              → 管理员发布活动
 *   GET  /seckill/activities            → 列活动 (公开, 不需 X-User-Id)
 *   POST /seckill/{activityId}          → 抢购 (核心)
 *   GET  /seckill/result/{activityId}   → 查我的秒杀结果 (轮询)
 */
@RestController
@RequestMapping("/seckill")
public class SeckillController {

    @Autowired
    private ISeckillActivityService seckillActivityService;

    /** ① 管理员发布秒杀活动 (权限已由网关拦截: POST /seckill/activity 要求 role=1, 见 AuthGlobalFilter.needAdmin) */
    @PostMapping("/activity")
    public Result<Long> publish(@RequestBody SeckillActivityDTO dto) {
        return Result.success(seckillActivityService.publishActivity(dto));
    }

    /** ② 列活动 (公开, 网关白名单已加 /seckill, 不需 X-User-Id) */
    @GetMapping("/activities")
    public Result<List<SeckillActivityVO>> list() {
        return Result.success(seckillActivityService.listActiveActivities());
    }

    /** ③ ⭐ 抢购核心入口 */
    @PostMapping("/{activityId}")
    public Result<String> seckill(
            @PathVariable Long activityId
    ) {
        Long userId= SecurityContextHolder.getUserId();
        return Result.success(seckillActivityService.seckill(userId, activityId));
    }

    /** ④ 查我的秒杀结果 (前端轮询用, 一般 1 秒一次) */
    @GetMapping("/result/{activityId}")
    public Result<Map<String, Object>> queryResult(
            @PathVariable Long activityId
    ) {
        Long userId= SecurityContextHolder.getUserId();
        return Result.success(seckillActivityService.querySeckillResult(userId, activityId));
    }

    /** ⑤ 秒杀订单支付 */
    @PostMapping("/pay/{orderNo}")
    public Result<Void> paySeckillOrder(@PathVariable String orderNo) {
        Long userId = SecurityContextHolder.getUserId();
        seckillActivityService.paySeckillOrder(userId, orderNo);
        return Result.success();
    }
}
