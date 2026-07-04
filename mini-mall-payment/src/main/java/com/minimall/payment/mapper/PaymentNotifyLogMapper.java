package com.minimall.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimall.payment.entity.PaymentNotifyLog;
import org.apache.ibatis.annotations.Mapper;

/** 回调流水 Mapper。 */
@Mapper
public interface PaymentNotifyLogMapper extends BaseMapper<PaymentNotifyLog> {
}
