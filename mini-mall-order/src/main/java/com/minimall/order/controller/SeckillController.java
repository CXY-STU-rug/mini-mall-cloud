package com.minimall.order.controller;

import com.minimall.common.core.context.SecurityContextHolder;
import com.minimall.common.core.domain.Result;
import com.minimall.order.dto.SeckillActivityDTO;
import com.minimall.order.dto.SeckillRequestDTO;
import com.minimall.order.service.ISeckillActivityService;
import com.minimall.order.vo.SeckillActivityVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 秒杀接口。
 *
 * 现在的链路是：抢到资格 -> MQ 异步创建普通订单 -> 复用普通订单支付。
 */
@RestController
@RequestMapping("/seckill")
public class SeckillController {

    @Autowired
    private ISeckillActivityService seckillActivityService;

    /** 管理员发布秒杀活动。 */
    @PostMapping("/activity")
    public Result<Long> publish(@RequestBody SeckillActivityDTO dto) {
        return Result.success(seckillActivityService.publishActivity(dto));
    }

    /** 列出待开始或进行中的秒杀活动。 */
    @GetMapping("/activities")
    public Result<List<SeckillActivityVO>> list() {
        return Result.success(seckillActivityService.listActiveActivities());
    }

    /**
     * 预热活动：开抢前把活动起止时间 + 库存写进 Redis，之后抢购入口零 DB。
     * 生产上由定时任务在活动开始前 N 分钟自动触发；这里暴露手动接口便于压测/运维。
     */
    @PostMapping("/preheat/{activityId}")
    public Result<Void> preheat(@PathVariable Long activityId) {
        seckillActivityService.preheatActivity(activityId);
        return Result.success();
    }

    /** 抢购入口：必须带收货地址，成功后异步创建普通订单。 */
    @PostMapping("/{activityId}")
    public Result<String> seckill(
            @PathVariable Long activityId,
            @RequestBody SeckillRequestDTO dto
    ) {
        Long userId = SecurityContextHolder.getUserId();
        Long addressId = dto == null ? null : dto.getAddressId();
        return Result.success(seckillActivityService.seckill(userId, activityId, addressId));
    }

    /** 查询我的秒杀结果，成功时返回普通订单号和普通订单 ID。 */
    @GetMapping("/result/{activityId}")
    public Result<Map<String, Object>> queryResult(@PathVariable Long activityId) {
        Long userId = SecurityContextHolder.getUserId();
        return Result.success(seckillActivityService.querySeckillResult(userId, activityId));
    }

    /**
     * 兼容旧前端的秒杀支付入口。
     *
     * 新链路已经不再使用它；前端会跳到 /pay/{orderNo}?orderId={orderId}。
     */
    @PostMapping("/pay/{orderNo}")
    public Result<Void> paySeckillOrder(@PathVariable String orderNo) {
        Long userId = SecurityContextHolder.getUserId();
        seckillActivityService.paySeckillOrder(userId, orderNo);
        return Result.success();
    }
}
