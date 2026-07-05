package com.minimall.order.dto;

import lombok.Data;

/**
 * 秒杀抢购请求。
 *
 * 秒杀成功后会直接生成普通订单，所以必须提前确定收货地址。
 */
@Data
public class SeckillRequestDTO {

    /** 收货地址 ID */
    private Long addressId;
}
