package com.minimall.search.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * search 服务线程池配置。
 *
 * 这里的线程池供全量灌 ES (syncAll) 的批次并行写入使用, 通过 CompletableFuture.runAsync
 * 直接提交任务, 不依赖 @Async 注解 (所以本类无需 @EnableAsync)。
 */
@Configuration
public class SearchAsyncConfig {

    /**
     * 全量同步专用线程池。
     *
     * 并行度刻意压小 (固定 4 线程) 的原因: ES 有写入压力保护, 单个过大的 bulk 会被
     * 429 es_rejected_execution_exception 拒收 (阈值约为堆内存的 10%)。批量灌数时若无节制地
     * 并发提交所有批次, 会把并发写入压力重新堆高、再次触发拒收。按每批 5000 条(约 3MB)估算,
     * 4 线程并发约 12MB, 远低于阈值, 既能提速又不至于压垮 ES。
     */
    @Bean("esSyncExecutor")
    public ThreadPoolTaskExecutor esSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);        // 固定并发度: 核心=最大=4
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(64);      // 批次任务排队缓冲 (有界, 防堆积)
        executor.setThreadNamePrefix("es-sync-");
        // 队列满时由提交线程自己执行该批, 形成背压, 不丢批次
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅停机: 关闭时等在灌的批次写完再退, 避免索引写一半
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
