package com.minimall.order.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 并发 Lab 3 —— 为什么单机锁救不了分布式
 * ════════════════════════════════════════════════════════════════
 * 用「两把锁」模拟「两个 JVM 实例」:
 *   test_同一把锁_安全      → 单机世界: 全员抢同一把锁 → 不超卖 ✅
 *   test_两把锁_模拟两实例   → 分布式: 各锁各的 → 又超卖 ❌
 *
 * 结论: synchronized/Lock/Atomic 只在"一个 JVM 内"有效。
 *       上线多实例后, 必须把原子性下沉到公共组件 → Redis(Lua) / DB 行锁。
 *       这就是你项目秒杀用 Lua、而不用 synchronized 的根本原因。
 * ════════════════════════════════════════════════════════════════
 */
public class DistributedLockDemoTest {

    /** 共享库存 = 模拟"同一个数据库里的一行库存", 两个实例都读写它 */
    private int stock = 100;
    private int sold  = 0;

    private static final int THREADS = 1000;

    /**
     * 带锁扣减: 锁住"读-判断-写"整个单元。
     * @param lock 传进来的锁对象 —— 谁传同一把, 谁之间才互斥
     */
    private void deductWith(Object lock) {
        synchronized (lock) {         // 抢到 lock 才能进; 但只对"用同一个 lock 的线程"生效
            int s = stock;            // 读
            if (s > 0) {              // 判断
                Thread.yield();       // 撑大窗口, 放大问题
                stock = s - 1;        // 写
                sold++;
            }
        }
    }

    // ══════════ 场景一: 单机 —— 全员同一把锁 ══════════
    @Test
    public void test_同一把锁_安全() throws InterruptedException {
        reset();
        Object 唯一锁 = new Object();   // 只有一把锁, 所有线程都抢它

        runConcurrently(i -> deductWith(唯一锁));   // ⭐ 不管线程编号, 全用同一把

        print("单机(同一把锁)");
        assertEquals(0, stock, "同一把锁下不该超卖");
    }

    // ══════════ 场景二: 分布式 —— 两把锁模拟两实例 ══════════
    @Test
    public void test_两把锁_模拟两实例() throws InterruptedException {
        reset();
        Object 实例A的锁 = new Object();   // 实例 A 自己的锁
        Object 实例B的锁 = new Object();   // 实例 B 自己的锁 (跟 A 是两个对象!)

        runConcurrently(i -> {
            // 奇偶分流: 一半线程走"实例A", 一半走"实例B"
            Object lock = (i % 2 == 0) ? 实例A的锁 : 实例B的锁;
            deductWith(lock);   // ⭐ A 组抢 A 锁, B 组抢 B 锁, 两组互不阻塞
        });

        print("分布式(两把锁模拟两实例)");
        // 这个断言会【失败】: A、B 两组同时进临界区, 又开始抢同一个 stock
        assertEquals(0, stock, "两把锁 = 两组线程互不互斥 → 超卖, 正如多实例部署");
    }

    // ─────────── 下面是复用的脚手架, 不用改 ───────────

    private void reset() { stock = 100; sold = 0; }

    /** 起 THREADS 个线程并发跑 task, 用双闸门保证同时起跑、等待全部完工 */
    private void runConcurrently(java.util.function.IntConsumer task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(THREADS);
        for (int i = 0; i < THREADS; i++) {
            int id = i;
            pool.submit(() -> {
                try {
                    startGate.await();
                    task.accept(id);
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
    }

    private void print(String scene) {
        System.out.println("========== " + scene + " ==========");
        System.out.println("剩余库存 stock = " + stock + "   (正确应为 0)");
        System.out.println("实际卖出 sold  = " + sold  + "   (正确应为 100)");
        System.out.println("守恒 stock+sold = " + (stock + sold) + "   (正确应为 100)");
    }
}
