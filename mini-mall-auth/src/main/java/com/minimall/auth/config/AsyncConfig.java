package com.minimall.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ASYNC-1: 异步线程池配置 —— 全项目第一个业务线程池
 *
 * 作用:给 @Async 方法提供"跑腿的后台线程",让发邮件这种慢 IO 不再阻塞主请求线程。
 *
 * 两个关键注解:
 *   @Configuration → 这是一个配置类, 里面 @Bean 方法产出的对象交给 Spring 容器管理
 *   @EnableAsync   → 异步功能的【总开关】。不加这行, 所有 @Async 注解全部失效(还是同步跑)!
 *                    它的原理:开启后 Spring 会给带 @Async 的 Bean 生成 AOP 代理,
 *                    调用被代理的方法时, 代理把任务丢进线程池, 主线程立刻返回。
 */
@Configuration          // 声明配置类
@EnableAsync            // 打开 @Async 总开关(整个 auth 服务生效)
public class AsyncConfig {

    /**
     * 邮件专用线程池。
     *
     * 为什么要【自定义】而不用 Spring 默认的?
     *   @Async 不指定线程池时, 默认用 SimpleAsyncTaskExecutor —— 它"来一个任务就 new 一个线程",
     *   根本不复用、不限流。高并发下会瞬间 new 出成千上万个线程, 直接把机器拖垮。
     *   所以生产环境必须自己配一个"有边界"的线程池。
     *
     * 为什么用 ThreadPoolTaskExecutor 而不是原生 ThreadPoolExecutor?
     *   ThreadPoolTaskExecutor 是 Spring 对原生线程池的封装, 能被 @Async 直接按 Bean 名字引用,
     *   还自带优雅停机、线程命名等便利。底层跑的还是原生 ThreadPoolExecutor, 七个参数一一对应。
     *
     * 方法名 "mailExecutor" 很重要:@Async("mailExecutor") 就是靠这个名字找到本线程池。
     */
    @Bean("mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // ── 七大参数(线程池的灵魂, 面试必问)──────────────────────────

        // ① 核心线程数:常驻不销毁的线程。发邮件是低频 IO, 平时 2 个够用。
        executor.setCorePoolSize(2);

        // ② 最大线程数:高峰期最多扩到几个线程。
        //    注意扩容时机:任务先进队列, 队列【满了】才会新建线程到 max, 不是核心满了就扩!
        executor.setMaxPoolSize(5);

        // ③ 队列容量:核心线程都忙时, 新任务先在这个【有界】队列里排队。
        //    绝不能用无界队列(Integer.MAX_VALUE)—— 任务堆积会撑爆内存 OOM。
        executor.setQueueCapacity(100);

        // ④ 空闲存活时间:超过核心数的那些"临时工"线程, 空闲 60 秒后自动回收, 省资源。
        executor.setKeepAliveSeconds(60);

        // ⑤ 线程名前缀:线程取名 mail-async-1、mail-async-2...
        //    出问题看日志/线程栈时, 一眼能认出"这是发邮件的线程", 排查神器。
        executor.setThreadNamePrefix("mail-async-");

        // ⑥ 拒绝策略:线程满(到 max)且队列也满时, 新任务怎么办?
        //    CallerRunsPolicy = "谁提交谁自己跑"—— 让调用者线程(主线程)亲自执行这次发送。
        //    效果:降级回同步、给系统减速的"背压", 但保证【邮件不丢】。比默认直接抛异常丢任务更稳。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // ⑦ 优雅停机:服务关闭时, 等待队列里已提交的邮件发完再退出, 别把人家验证码丢了。
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);   // 最多等 30 秒, 超了也强制退, 防止卡死停机

        executor.initialize();   // 初始化线程池(必须调, 否则线程池没真正建起来)
        return executor;
    }
}
