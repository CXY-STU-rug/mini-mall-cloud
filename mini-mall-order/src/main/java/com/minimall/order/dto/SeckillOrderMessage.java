package com.minimall.order.dto;

import lombok.Data;

/**
 * 秒杀成功后投递到 MQ 的订单创建消息。
 *
 * MQ 消费线程没有用户登录上下文，所以这里携带已经校验过的地址快照，
 * 消费者不再依赖 X-User-Id 去二次查询地址。
 */
@Data
public class SeckillOrderMessage {

    private Long activityId;

    private Long userId;

    private Long addressId;

    private String receiver;

    private String phone;

    private String address;
}
