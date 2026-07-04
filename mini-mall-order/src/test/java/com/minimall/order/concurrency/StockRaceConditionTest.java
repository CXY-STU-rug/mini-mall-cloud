package com.minimall.order.concurrency;

import org.junit.jupiter.api.Test;   // 改回 JUnit 5(原来误导入了 TestNG 的 @Test), 和其他 Lab 一致

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 并发 Lab 1 —— 亲手制造一次「超卖」
 * ════════════════════════════════════════════════════════════════
 * 目标: 用最小场景复现 race condition(竞态条件), 亲眼看到「卖出 > 库存」。
 *       不依赖 Spring / Redis / DB, 纯 Java 多线程, 秒级跑完。
 *
 * 核心概念(跑完你就懂):
 *   - 竞态条件 race condition: 多线程对同一份数据「读-判断-写」时互相插队
 *   - 原子性 atomicity: 一组操作要么全做完、要么全不做, 中途不被打断
 *   - 你项目秒杀的 Lua 脚本, 正是把「检查+扣减」做成原子, 才防住了超卖
 *
 * 跑法:
 *   IDEA 里右键这个类 → Run 'StockRaceConditionTest'
 *   或命令行: mvn -pl mini-mall-order test -Dtest=StockRaceConditionTest
 * ════════════════════════════════════════════════════════════════
 */
public class StockRaceConditionTest {

    /** 共享库存: 100 件, 会被 100 个线程同时争抢 */
    private int stock = 100;

    /** 实际卖出计数: 每成功扣一次 +1(正确结果应该也是 100) */
    private int sold = 0;

    /** 并发线程数 = 模拟 100 个用户同一瞬间抢购 */
    private static final int THREADS = 100;

    /**
     * ⭐ 朴素扣减 —— 故意写成「读 → 判断 → 写」三步, 不加任何同步锁。
     *
     * TODO ① 你来写这三步(这就是超卖的案发现场):
     *   1. 读:   int s = stock;          // 先把当前库存读进局部变量
     *   2. 判断: if (s > 0) {            // 还有货吗
     *   3. 写:       stock = s - 1;      //   扣 1
     *                sold++;             //   卖出 +1
     *            }
     *
     * 为什么这三步会超卖(想清楚再写):
     *   线程 A 读到 s=1, 还没执行「写」, 时间片被抢走;
     *   线程 B 也读到 s=1, 判断 >0, 扣成 stock=0, 卖出 +1;
     *   线程 A 恢复, 用它手里的旧 s=1 继续写 stock=0, 又卖出 +1;
     *   → 库存只有 1, 却卖出了 2 件。这就是「读-判断-写」不原子的后果。
     */
    private void naiveDeduct() {
        // TODO ① 在这里写「读-判断-写」三步
        int s =stock;
        if(s>0)
        {  Thread.yield();       // 主动让出 CPU, 逼调度器切到别的线程
            stock = s - 1;
            sold++;
        }
    }

    @Test
    public void 复现超卖() throws InterruptedException {
        // 线程池: 固定 100 个线程, 一人一个模拟并发用户
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        // startGate: 起跑枪。所有线程都 await 在这, 主线程一 countDown 全部同时冲
        //            —— 让并发「更集中」, 更容易撞出 race condition
        CountDownLatch startGate = new CountDownLatch(1);

        // endGate: 完工哨。每个线程干完 countDown 一次, 主线程 await 等全部完工
        CountDownLatch endGate = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                try {
                    // TODO ② 等待起跑枪(阻塞在这, 直到主线程打响):
                    //   startGate.await();
                    startGate.await();
                    naiveDeduct();   // 抢一次库存

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    // TODO ③ 报告完工(务必放 finally, 保证一定 -1):
                    //   endGate.countDown();
                    endGate.countDown();
                }
            });
        }

        // TODO ④ 打响起跑枪(让 100 个线程同一瞬间全部开抢):
        //   startGate.countDown();
        startGate.countDown();
        endGate.await();     // 主线程在这等: 直到 100 个线程全部完工
        pool.shutdown();     // 关线程池

        System.out.println("========== 并发 Lab 1 结果 ==========");
        System.out.println("剩余库存 stock = " + stock + "   (正确应为 0)");
        System.out.println("实际卖出 sold  = " + sold  + "   (正确应为 100)");
        System.out.println("=====================================");

        // ⭐ 这个断言大概率【失败】—— 失败正好证明超卖发生了。
        //    (并发 bug 有偶然性, 偶尔也可能凑巧过; 多跑几次, 或把 THREADS 调大到 1000)
        assertEquals(0, stock,
                "库存被卖穿了! stock 不为 0 → 发生超卖, 这就是 race condition");
    }
}
