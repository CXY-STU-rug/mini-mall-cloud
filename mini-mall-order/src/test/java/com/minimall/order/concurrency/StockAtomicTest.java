package com.minimall.order.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 并发 Lab 2(无锁版) —— 用 AtomicInteger + CAS 治超卖
 * ════════════════════════════════════════════════════════════════
 * 对照 StockRaceConditionTest(裸 int 会超卖):
 *   这里把 stock / sold 换成 AtomicInteger, 不加任何锁,
 *   靠 CAS 自旋实现"有条件的原子扣减", 让断言从"失败"变"通过"。
 *
 * ⚠️ 注意: 不能简单 stock.decrementAndGet()!
 *    那是无脑减, 没有"> 0 才减"的判断, 会一路减成负数。
 *    必须用 while + compareAndSet 自旋, 才能"还有货才扣"。
 * ════════════════════════════════════════════════════════════════
 */
public class StockAtomicTest {

    /** 共享库存: 原子整数, 初值 100 */
    private final AtomicInteger stock = new AtomicInteger(100);

    /** 卖出计数: 原子整数, 初值 0 */
    private final AtomicInteger sold = new AtomicInteger(0);

    private static final int THREADS = 1000;   // 多开点, 压力更大更能验证

    /**
     * ⭐ 原子扣减 —— 你来填 CAS 自旋循环。
     *
     * TODO 用 while(true) 自旋, 填 4 步:
     *   1. 读:   int cur = stock.get();              // 读当前库存
     *   2. 判断: if (cur <= 0) return;               // 没货了, 直接结束(不扣)
     *   3. CAS:  if (stock.compareAndSet(cur, cur-1)){// 只有 stock 仍等于 cur 才扣成功
     *   4. 成功:     sold.incrementAndGet();          //   扣成功才算真卖出
     *                return;                          //   干完退出循环
     *            }
     *            // 走到这 = CAS 失败(有人抢先改了) → 循环重来, 重读 cur 再试
     *
     * 想清楚: 第 3 步失败为什么要"重来"而不是"放弃"?
     *   失败只说明"我读到的 cur 过期了", 不代表没货了。
     *   重读新值, 可能还有货 → 该给这个用户再试一次机会。
     */
    private void atomicDeduct() {

        // TODO 在这里写 while(true){ 读 → 判断 → CAS → 成功则 sold+1 并 return }
        while (true) {
            int cur = stock.get();
            if (cur <= 0)
                return;
            if(stock.compareAndSet(cur, cur - 1))// 只有 stock 仍等于 cur 才扣成功
            {
                sold.incrementAndGet();          //   扣成功才算真卖出
                return;
            }

        }
    }
    @Test
    public void 无锁防超卖() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();      // 等起跑枪
                    atomicDeduct();         // 抢一次库存
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();    // 报告完工
                }
            });
        }

        startGate.countDown();   // 1000 个线程同一瞬间开抢
        endGate.await();
        pool.shutdown();

        System.out.println("========== 并发 Lab 2(无锁) 结果 ==========");
        System.out.println("剩余库存 stock = " + stock.get() + "   (正确应为 0)");
        System.out.println("实际卖出 sold  = " + sold.get()  + "   (正确应为 100)");
        System.out.println("守恒校验 stock+sold = " + (stock.get() + sold.get()) + "   (正确应为 100)");
        System.out.println("==========================================");

        // 这次应该【通过】: CAS 挡住了所有旧值写入, 一件都不会超卖
        assertEquals(0,   stock.get(), "库存不为 0 → CAS 没写对");
        assertEquals(100, sold.get(),  "卖出不为 100 → CAS 没写对");
    }
}
