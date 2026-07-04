package com.minimall.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimall.payment.entity.Refund;
import org.apache.ibatis.annotations.Mapper;

/** 退款单 Mapper。 */
@Mapper
public interface RefundMapper extends BaseMapper<Refund> {
}
