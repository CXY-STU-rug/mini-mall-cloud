package com.minimall.order.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁 (从单体 common.util 搬过来, 0 改动)
 *
 * 为啥要分布式锁:
 *   单体环境 synchronized / ReentrantLock 只锁本 JVM,
 *   微服务多实例时 (order 服务起 2 个), 本地锁互不可见, 必须 Redis 这种【外部共享存储】.
 *
 * 核心两个方法:
 *   tryLock(key, seconds) → 抢锁, 成功返回【持有者标识】, 失败返回 null
 *   unlock(key, owner)    → 释放锁 (Lua 原子: 先校验 owner 再 DEL, 防误删)
 *
 * 设计要点:
 *   ① owner 用 UUID, 防止 A 的锁被 B 误删 (A 锁过期了, B 抢到, 此时 A 跑完去 unlock 不能删 B 的)
 *   ② SET NX + EX 一次完成 (setIfAbsent), 不能 SET + EXPIRE 两步 (两步之间 crash 会死锁)
 *   ③ 释放锁必须 Lua 原子 (GET + DEL 一起做)
 *
 * ⚠️ SEC-4 已被 Redisson RLock 替换 (OrdersServiceImpl 三处), 本类保留做学习对照:
 *   手写版硬伤 = 锁固定 10s 过期, 业务超时后锁失效会放进第二个线程 (UUID 只防误删, 不防并发);
 *   Redisson 看门狗自动续期解决了这一点, 且 isHeldByCurrentThread 等价 owner 校验
 */
@Deprecated
@Component
public class RedisLockUtil {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisLockUtil(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** Lua 脚本: 比对 owner 才 DEL, 类加载时编译一次 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setScriptText(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end"
        );
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 尝试加锁
     * @return 成功返回 owner (释放时用), 失败返回 null
     */
    public String tryLock(String key, long expireSeconds) {
        String owner = UUID.randomUUID().toString();
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, owner, expireSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success) ? owner : null;
    }

    /** 释放锁 (owner 不匹配会拒绝) */
    public boolean unlock(String key, String owner) {
        if (owner == null) return false;
        Long result = stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(key),
                owner
        );
        return Long.valueOf(1L).equals(result);
    }
}
