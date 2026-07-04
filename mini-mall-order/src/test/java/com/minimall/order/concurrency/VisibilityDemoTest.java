package com.minimall.order.concurrency;

import org.junit.jupiter.api.Test;

/**
 * Lab 5: volatile 与可见性
 *
 * 现象: 主线程把 stop 改成 true, worker 线程却"看不见", 死循环不退出。
 * 原因: ① worker 一直读自己工作内存里的 stop 副本
 *       ② JIT 发现循环体内没人改 stop, 直接优化成 while(true)
 *
 * 玩法: 第一次直接跑 → 观察 worker 卡死 (测试打印 "还活着")
 *       第二次给 stop 加上 volatile → worker 秒停
 */
public class VisibilityDemoTest {

    // TODO 第二次跑之前: 在 boolean 前面加 volatile, 对比结果
    private static boolean stop = false;
    // 说明: 不能是局部变量(局部变量在线程私有栈上, 没有共享问题), 必须是成员/静态字段


    @Test
    public void visibilityDemo() throws InterruptedException {
        Thread worker = new Thread(() -> {
            System.out.println("[worker] 开始循环, 等 stop 变 true...");
            long count = 0;
            while (!stop) {
                count++;   // ⚠️ 循环体必须"干净": 不能放 println/sleep/synchronized,
                           //    它们内部带锁或内存屏障, 会"顺手"刷新缓存, 现象就没了
            }
            System.out.println("[worker] 看见 stop=true 了! 循环了 " + count + " 次, 正常退出");
        });
        worker.setDaemon(true);   // 守护线程: 就算它死循环, 测试结束时 JVM 也能退出, 不会挂住 Maven
        worker.start();

        Thread.sleep(1000);        // 让 worker 先跑热 1 秒 (JIT 要跑一会儿才会介入优化)
        stop = true;               // ★ 主线程改 stop —— 问题: worker 看得见吗?
        System.out.println("[main] 已把 stop 改成 true, 给 worker 2 秒时间退出...");

        worker.join(2000);         // 最多等 2 秒 (不加超时的话, worker 死循环会让测试永远卡住)

        if (worker.isAlive()) {
            System.out.println("[main] ❌ worker 还活着! 它根本没看见 stop=true → 可见性问题复现");
        } else {
            System.out.println("[main] ✅ worker 已退出 → 修改对它可见");
        }
    }
}
