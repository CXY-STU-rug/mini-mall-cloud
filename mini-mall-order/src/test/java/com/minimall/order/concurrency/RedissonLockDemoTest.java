package com.minimall.order.concurrency;

import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 并发 Lab 4 —— Redisson 分布式锁：把 Lab3 的超卖治好
 * ════════════════════════════════════════════════════════════════
 * 对照 Lab3(DistributedLockDemoTest):
 *   Lab3: 两把【JVM 内存锁】模拟两实例 → 各锁各的 → 超卖 ❌
 *   Lab4: 两个 RedissonClient 模拟两实例, 抢【Redis 里同一个 key 的锁】
 *         → 锁是所有实例共享的 → 全局互斥 → 不超卖 ✅
 *
 * 唯一的变化: 锁的"载体"从 JVM 内存对象, 换成了公共的 Redis。
 *
 * 前置条件: 本机 Redis 已启动 (127.0.0.1:6379)。你秒杀的 Lua 能跑, 它就是通的。
 * 跑法: 右键 分布式锁_两实例不超卖 → Run
 * ════════════════════════════════════════════════════════════════
 */
public class RedissonLockDemoTest {

    /** 共享库存 = 模拟"数据库里的一行库存", 两个实例都读写它 */
    private int stock = 100;
    private int sold  = 0;

    private static final int THREADS = 300;
    /** 锁的 key: 所有实例、所有线程都抢这一个 key, 才叫"全局锁" */
    private static final String LOCK_KEY = "lab:seckill:stock:lock";

    /** 造一个 RedissonClient = 造一个"应用实例"的 Redis 连接 */
    private RedissonClient newInstance() {
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://127.0.0.1:6379")   // 本机 Redis
              .setDatabase(0);
        return Redisson.create(config);   // 每 create 一个, 就是一个独立"实例"
    }

    @Test
    public void 分布式锁_两实例不超卖() throws InterruptedException {
        // ⭐ 两个 client = 模拟两台机器上各跑一个 mini-mall-order 实例
        RedissonClient 实例A = newInstance();
        RedissonClient 实例B = newInstance();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            int id = i;
            pool.submit(() -> {
                // 奇偶分流: 一半线程走实例A, 一半走实例B (对照 Lab3 的两组)
                RedissonClient 实例 = (id % 2 == 0) ? 实例A : 实例B;
                // 两个实例 getLock 同一个 key → 拿到的是 Redis 里【同一把】分布式锁
                RLock lock = 实例.getLock(LOCK_KEY);

                try {
                    startGate.await();

                    // TODO ① 上锁 (抢到 Redis 里这把全局锁才能进):
                       lock.lock();

                    try {
                        int s = stock;            // 读
                        if (s > 0) {              // 判断
                            Thread.yield();       // 撑大窗口; 但这次别的实例进不来了
                            stock = s - 1;        // 写
                            sold++;
                        }
                    } finally {
                        // TODO ② 解锁 (必须放 finally! 否则异常时锁不释放, 靠看门狗过期兜底也慢):
                           lock.unlock();
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await();
        pool.shutdown();
        实例A.shutdown();   // 关掉 Redisson 连接, 不然测试进程不退出
        实例B.shutdown();

        System.out.println("========== 并发 Lab 4(Redisson 分布式锁) ==========");
        System.out.println("剩余库存 stock = " + stock + "   (正确应为 0)");
        System.out.println("实际卖出 sold  = " + sold  + "   (正确应为 100)");
        System.out.println("守恒 stock+sold = " + (stock + sold) + "   (正确应为 100)");
        System.out.println("=================================================");

        // 这次应【通过】: 锁在共享的 Redis 里, 两实例也拦得住
        assertEquals(0,   stock, "两实例抢同一 Redis 锁, 不该超卖");
        assertEquals(100, sold,  "守恒被破 → 分布式锁没写对");
    }
}
