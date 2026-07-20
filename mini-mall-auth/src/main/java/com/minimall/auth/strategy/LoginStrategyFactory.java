package com.minimall.auth.strategy;

import com.minimall.auth.enums.LoginType;
import com.minimall.common.core.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 登录策略工厂 (工厂模式)。
 *
 * 关键: 构造器注入 List<LoginStrategy>, Spring 会把容器里所有 LoginStrategy 实现自动收集进来,
 * 工厂据此建立 loginType → 策略 的映射。新增一种登录策略 (加 @Component) 会被自动纳入,
 * 本工厂无需改动 —— 这是策略+工厂能"开闭"的关键。
 */
@Component
public class LoginStrategyFactory {

    private final Map<LoginType, LoginStrategy> strategyMap = new EnumMap<>(LoginType.class);

    public LoginStrategyFactory(List<LoginStrategy> strategies) {
        for (LoginStrategy strategy : strategies) {
            strategyMap.put(strategy.getType(), strategy);
        }
    }

    /** 按登录方式取对应策略, 没有对应实现则报错 */
    public LoginStrategy getStrategy(LoginType loginType) {
        LoginStrategy strategy = strategyMap.get(loginType);
        if (strategy == null) {
            throw new BusinessException("不支持的登录方式: " + loginType);
        }
        return strategy;
    }
}
