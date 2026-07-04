package com.minimall.order.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 并发工具箱 —— 一个类里把常用工具类逐个跑一遍
 * ════════════════════════════════════════════════════════════════
 * 用法: IDEA 里右键【某一个】@Test 方法 → Run, 单独看它的输出。
 *       一个个跑, 对照上面聊天里的讲解, 建立"画面感"。
 *
 * 分两组:
 *   A 组(01~04): 怎么把代码跑成线程 —— 创建 & 管理
 *   B 组(05~08): 怎么让线程配合/不打架 —— 协调 & 保护
 * ════════════════════════════════════════════════════════════════
 */
public class ThreadToolboxTest {

    // ═══════════════════════ A 组: 创建 & 管理 ═══════════════════════

    /** A-1 最原始的 Thread: 手动开一根线程 */
    @Test
    public void test01_原始Thread() throws InterruptedException {
        // Runnable 就是"要在线程里跑的那段活", 这里用 Lambda 写
        Runnable 活 = () -> {
            // Thread.currentThread().getName() = 当前线程的名字, 用来看是谁在跑
            System.out.println("新线程在跑, 我叫: " + Thread.currentThread().getName());
        };

        Thread t = new Thread(活, "我的线程");
        t.start();   // ⭐ start() 才是"开新线程"; 若写成 t.run() 就是主线程自己跑, 没并发

        // 主线程等 t 跑完再往下 (不 join 的话, main 可能先结束, 看不到输出)
        t.join();
        System.out.println("主线程: 我等到新线程干完了, 我叫 " + Thread.currentThread().getName());
        // 观察点: 两行的线程名不一样 → 证明真的是两根不同的线程
    }

    /** A-2 Runnable vs Lambda: 其实是同一个东西的两种写法 */
    @Test
    public void test02_Runnable是什么() throws InterruptedException {
        // 老写法: new 一个匿名 Runnable, 实现它的 run()
        Runnable 老写法 = new Runnable() {
            @Override
            public void run() {
                System.out.println("老写法 run() 里");
            }
        };
        // 新写法: 上面这一坨等价于一句 Lambda (因为 Runnable 只有一个方法)
        Runnable 新写法 = () -> System.out.println("Lambda 写法里");

        new Thread(老写法).start();
        new Thread(新写法).start();
        Thread.sleep(200);   // 粗暴等一下, 保证两根线程都打印完 (真实项目别这么等)
        // 观察点: Runnable = "一段可以丢给线程去跑的活", Lambda 只是它的简写
    }

    /** A-3 线程池 ExecutorService: 真实项目管理线程的方式 (Lab 里用的) */
    @Test
    public void test03_线程池() throws InterruptedException {
        // 建一个固定 3 根线程的池子: 无论丢多少任务, 永远只有 3 根线程轮着干
        ExecutorService pool = Executors.newFixedThreadPool(3);

        for (int i = 1; i <= 6; i++) {
            int taskId = i;   // Lambda 里要用的外部变量必须是 final/等效 final, 所以拷一份
            pool.submit(() -> {
                // 看名字: 只会出现 3 个不同的 pool-...-thread-1/2/3, 反复复用
                System.out.println("任务 " + taskId + " 由 " + Thread.currentThread().getName() + " 执行");
            });
        }

        pool.shutdown();   // 告诉池子: 不再收新任务, 干完现有的就关
        // awaitTermination = 等池子里的活全干完 (最多等 2 秒)
        pool.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
        // 观察点: 6 个任务, 却只有 3 根线程 → 线程被"复用"了, 这就是池子的意义
    }

    /** A-4 Future: 从线程里"拿回返回值" (submit 一个有返回值的任务) */
    @Test
    public void test04_Future拿返回值() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(1);

        // submit 一个 Callable(有返回值的活), 拿到一张"未来才有结果的凭证" Future
        Future<Integer> 凭证 = pool.submit(() -> {
            Thread.sleep(300);   // 假装算了一会儿
            return 42;           // 算出的结果
        });

        System.out.println("主线程: 我先干别的, 结果以后再取...");
        Integer 结果 = 凭证.get();   // ⭐ get() 会阻塞, 直到那根线程算完把 42 交出来
        System.out.println("主线程: 拿到结果 = " + 结果);
        pool.shutdown();
        // 观察点: Future 让你"异步发起 + 稍后取结果", 不用干等着
    }

    // ═══════════════════════ B 组: 协调 & 保护 ═══════════════════════

    /** B-1 CountDownLatch: 等一组线程全部干完 (Lab 里的"完工哨") */
    @Test
    public void test05_CountDownLatch() throws InterruptedException {
        int n = 3;
        CountDownLatch 完工哨 = new CountDownLatch(n);   // 计数器从 3 开始

        ExecutorService pool = Executors.newFixedThreadPool(n);
        for (int i = 1; i <= n; i++) {
            int id = i;
            pool.submit(() -> {
                try {
                    Thread.sleep(id * 100L);   // 每个线程干活时长不同
                    System.out.println("工人 " + id + " 干完了");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    完工哨.countDown();   // 每干完一个, 计数器 -1
                }
            });
        }

        完工哨.await();   // ⭐ 主线程卡在这, 直到计数器归 0 (三个都干完)
        System.out.println("主线程: 确认三个工人全部完工, 我继续");
        pool.shutdown();
        // 观察点: "主线程: 全部完工" 这行一定最后打印 —— await 把主线程拦住了
    }

    /** B-2 synchronized: 互斥锁, 治超卖的第一招 */
    @Test
    public void test06_synchronized() throws InterruptedException {
        // 用一个数组当"可变的共享计数", 让 500 个线程各 +1
        int[] count = {0};
        Object 锁 = new Object();   // 随便找个对象当"锁", 大家抢同一把才有意义

        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(500);
        for (int i = 0; i < 500; i++) {
            pool.submit(() -> {
                synchronized (锁) {   // ⭐ 抢到"锁"这把锁的线程才能进; 同一刻只一个
                    count[0]++;        // count++ 本是"读-加-写"三步, 现在被锁成原子
                }
                latch.countDown();
            });
        }
        latch.await();
        pool.shutdown();
        System.out.println("加锁后 count = " + count[0] + "  (正确应为 500)");
        // 观察点: 一定是 500。把 synchronized 那行删掉再跑, 大概率 < 500 (丢更新)
    }

    /** B-3 ReentrantLock: 跟 synchronized 一个作用, 但要手动 lock/unlock */
    @Test
    public void test07_ReentrantLock() throws InterruptedException {
        int[] count = {0};
        ReentrantLock lock = new ReentrantLock();   // 一把显式的锁

        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(500);
        for (int i = 0; i < 500; i++) {
            pool.submit(() -> {
                lock.lock();          // 手动上锁
                try {
                    count[0]++;
                } finally {
                    lock.unlock();    // ⭐ 必须放 finally! 否则出异常锁没释放 = 死锁
                }
                latch.countDown();
            });
        }
        latch.await();
        pool.shutdown();
        System.out.println("ReentrantLock 后 count = " + count[0] + "  (正确应为 500)");
        // 观察点: 结果同 synchronized。区别是 Lock 能 tryLock(超时)、可中断, 更灵活
    }

    /** B-4 AtomicInteger: 不加锁也能原子自增 (CAS 无锁) */
    @Test
    public void test08_AtomicInteger() throws InterruptedException {
        AtomicInteger count = new AtomicInteger(0);   // 原子整数

        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(500);
        for (int i = 0; i < 500; i++) {
            pool.submit(() -> {
                count.incrementAndGet();   // ⭐ 等价 ++count, 但底层用 CPU 的 CAS 指令保证原子
                latch.countDown();
            });
        }
        latch.await();
        pool.shutdown();
        System.out.println("AtomicInteger 后 count = " + count.get() + "  (正确应为 500)");
        // 观察点: 也是 500, 但没用任何锁 —— 性能通常比锁好, 适合"简单计数"场景
    }
}
