package com.minimall.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.minimall.order.dto.SeckillActivityDTO;
import com.minimall.order.entity.SeckillActivity;
import com.minimall.order.vo.SeckillActivityVO;

import java.util.List;
import java.util.Map;

/**
 * 秒杀活动 Service 接口 (从单体搬, 改: seckill / querySeckillResult 加 userId 入参)
 *
 * 4 方法:
 *   publishActivity     管理员发布活动 (不要 userId, 是管理员的事)
 *   listActiveActivities 列活动 (不要 userId, 公开列表)
 *   seckill             ⭐ 核心抢购入口, 必须 userId (Lua 要 userId 防重复)
 *   querySeckillResult  前端轮询查结果, 必须 userId (查"我"的抢购结果)
 */
public interface ISeckillActivityService extends IService<SeckillActivity> {

    /** ① 管理员发布秒杀活动 (校验 + 入库, 返新活动 id) */
    Long publishActivity(SeckillActivityDTO dto);

    /** ② 查"进行中或即将开始"的活动列表 */
    List<SeckillActivityVO> listActiveActivities();

    /** ③ 秒杀核心入口, 返回抢购结果文案 (实际订单异步生成) */
    String seckill(Long userId, Long activityId, Long addressId);

    /** ④ 查"我"的秒杀结果, 3 态 SUCCESS/PROCESSING/NOT_FOUND */
    Map<String, Object> querySeckillResult(Long userId, Long activityId);


    // ISeckillActivityService
    /** ⑤ 秒杀订单支付 */
    void paySeckillOrder(Long userId, String orderNo);

    /**
     * ⑥ 预热活动：开抢前把活动的起止时间 + 库存一次性写进 Redis。
     *    之后抢购入口只读 Redis、零 DB —— 避免 5000 人开抢瞬间一起冲 DB(缓存击穿)。
     *    生产上由"定时任务在活动开始前 N 分钟"触发，这里同时暴露一个手动接口便于压测。
     */
    void preheatActivity(Long activityId);
}
