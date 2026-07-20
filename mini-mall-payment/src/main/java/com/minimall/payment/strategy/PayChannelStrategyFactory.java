package com.minimall.payment.strategy;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.payment.enums.PayChannel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 支付渠道策略工厂 (工厂模式)。
 *
 * 构造器注入 List<PayChannelStrategy>: Spring 会把容器里所有 PayChannelStrategy 实现自动收集进来,
 * 工厂据此建立 渠道→策略 的映射。新增渠道 (加 @Component) 会被自动纳入, 本工厂无需改动 ——
 * 这跟登录的 LoginStrategyFactory 是同一个套路。
 */
@Component
public class PayChannelStrategyFactory {

    private final Map<PayChannel, PayChannelStrategy> strategyMap = new EnumMap<>(PayChannel.class);

    public PayChannelStrategyFactory(List<PayChannelStrategy> strategies) {
        for (PayChannelStrategy strategy : strategies) {
            strategyMap.put(strategy.getChannel(), strategy);
        }
    }

    /** 按渠道取对应策略, 没有对应实现则报错 */
    public PayChannelStrategy get(PayChannel channel) {
        PayChannelStrategy strategy = strategyMap.get(channel);
        if (strategy == null) {
            throw new BusinessException("不支持的支付渠道: " + channel);
        }
        return strategy;
    }
}
