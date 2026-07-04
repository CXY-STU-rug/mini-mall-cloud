package com.minimall.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimall.payment.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付单 Mapper。继承 BaseMapper 后自动拥有 insert/selectById/update 等,
 * 不用写任何 XML。启动类 @MapperScan("com.minimall.payment.mapper") 会扫到它生成实现。
 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
