package com.minimall.product.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimall.product.entity.Product;
import com.minimall.product.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SEC-3: 布隆过滤器配置 —— 商品详情"防穿透"的第一层
 *
 * 分工(双层防穿透):
 *   第 1 层 布隆过滤器: 事前拦截 —— 海量【随机】不存在 id, DB 一次都不查
 *   第 2 层 空值缓存:   事后兜底 —— 布隆的 1% 假阳性 + 已删商品(布隆删不掉)
 *
 * 语义铁律: contains=false 一定不存在(放心拒); contains=true 只是【可能】存在
 */
@Slf4j
@Configuration
public class BloomFilterConfig {

    /**
     * 手动建 RedissonClient (只给布隆用)
     * 不用 redisson-spring-boot-starter: starter 会接管 RedisConnectionFactory,
     * 影响现有 RedisTemplate(Lettuce); 裸包 + 手动 Bean 两套客户端互不干扰
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port) {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + host + ":" + port);
        return Redisson.create(config);
    }

    /**
     * 商品 id 布隆过滤器
     * tryInit(预期插入量, 误判率) 决定位数组大小和哈希函数个数:
     *   10 万 + 1% ≈ 117KB 位数组 + 7 个哈希, 内存代价极小
     *   插入量估小了误判率会飙升, 宁可放大余量
     * tryInit 幂等: Redis 里已存在同参数配置就跳过, 不会清已有数据
     */
    @Bean
    public RBloomFilter<Long> productBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<Long> filter = redissonClient.getBloomFilter("product:bloom");
        filter.tryInit(100_000L, 0.01);
        return filter;
    }

    /**
     * 启动预热: 把 DB 全量商品 id 灌进布隆
     * ApplicationRunner 在 Spring 容器就绪后执行一次;
     * 布隆不支持删除, 重启重灌能顺带"重建"掉历史脏位
     */
    @Bean
    public ApplicationRunner bloomWarmUp(RBloomFilter<Long> productBloomFilter,
                                         ProductMapper productMapper) {
        return args -> {
            // selectObjs + select("id"): 只查 id 列, 不把整行商品拖出来
            List<Object> ids = productMapper.selectObjs(
                    new QueryWrapper<Product>().select("id"));
            for (Object id : ids) {
                productBloomFilter.add(((Number) id).longValue());
            }
            log.info("[布隆预热] 完成, 共灌入商品 id {} 条", ids.size());
        };
    }
}
