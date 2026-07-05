package com.minimall.order.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SEC-4: Redisson 客户端配置 —— 给 RLock 分布式锁用
 *
 * 为什么从手写 RedisLockUtil 换成 Redisson RLock:
 *   手写版硬伤: 锁 10 秒过期, 业务万一超过 10 秒(慢 SQL/Feign 超时重试),
 *   锁自动失效 → 第二个请求进临界区 → 两个线程并发执行
 *   (UUID owner 只能保证"不误删", 保证不了"不并发")
 *   Redisson 看门狗(watchdog): 不传 leaseTime 时后台线程每 10 秒续期到 30 秒,
 *   业务跑多久锁就活多久, 业务结束/进程崩溃才释放 —— 从根上关掉这个窗口
 *
 * 跟 product 的 BloomFilterConfig 同款写法: 裸包 + 手动 Bean,
 * 不用 redisson-spring-boot-starter (它会接管 RedisConnectionFactory 影响现有 RedisTemplate)
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port) {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + host + ":" + port);
        return Redisson.create(config);
    }
}
