package com.minimall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minimall.product.config.ProductSearchMQConfig;
import com.minimall.product.entity.Product;
import com.minimall.product.mapper.ProductMapper;
import com.minimall.product.service.IProductService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;                    // SEC-4: 击穿互斥锁写法二用
import org.redisson.api.RedissonClient;           // SEC-4: 复用布隆那个 RedissonClient Bean
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;   // 雪崩防护: 给过期时间加随机值

/**
 * Product Service 实现 (G3.9 从单体搬 5 方法)
 *
 * 单体 ProductServiceImpl 原方法照抄, 包名换 + Redis Bean 类型用微服务的
 */
@Service
@Slf4j
public class ProductServiceImpl
        extends ServiceImpl<ProductMapper, Product>
        implements IProductService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // SEC-3: 布隆过滤器 (BloomFilterConfig 里初始化 + 启动预热全量商品 id)
    @Autowired
    private RBloomFilter<Long> productBloomFilter;

    // SEC-4: 击穿互斥锁写法二 —— 直接复用 BloomFilterConfig 里那个 RedissonClient Bean,
    //        不用新加依赖/配置 (order 模块同款升级, 全项目锁方案统一成 Redisson)
    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private  RabbitTemplate rabbitTemplate;


    /** 详情: 布隆前置拦截 → Redis 缓存 → 没中查 MySQL → 回写缓存
     *  防护: ⓪ 穿透第一层(布隆挡随机不存在 id)  ① 穿透第二层(缓存空值兜假阳性/已删商品)
     *        ② 雪崩(过期加随机)  ③ 击穿(互斥锁)
     */
    @Override
    public Product getProductDetail(Long id) {
        // ⭐ SEC-3 布隆前置拦截: false = 一定不存在, 缓存和 DB 都不用碰
        //    (true 只是"可能存在", 有 1% 假阳性, 靠下面空值缓存兜底)
        if (!productBloomFilter.contains(id)) {
            log.info("布隆拦截(id 一定不存在) id={}", id);
            return null;
        }

        String key = "product:detail:" + id;

        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            // ⭐ 穿透防护: 命中的是"空值标记"(空字符串) → 说明之前查过"无此商品",
            //    直接返回 null, 不再打 DB。(Product 存的是对象, 空标记存的是 String, 靠类型区分)
            if (cached instanceof String) {
                log.info("命中空值标记(防穿透) key={}", key);
                return null;
            }
            log.info("缓存命中 key={}", key);
            return (Product) cached;
        }

        // ⭐ 击穿防护: 缓存未命中 → 交给互斥锁方法重建, 只放一个线程查 DB
        log.info("缓存未命中, 进入互斥重建 key={}", key);
        return loadWithMutex(id, key);
    }

    /**
     * 击穿防护 · 写法二: Redisson RLock 互斥重建缓存 (SEC-4, 替换手写 SETNX 版)
     * 目标不变: 热点 key 过期瞬间, 只放【一个】线程去查 DB + 回填。
     *
     * 写法一(手写 SETNX)的三个硬伤 → RLock 怎么解:
     *   ① 固定 10s 过期: DB 慢查超 10s 锁自动没了, 别的线程闯入 → 并发窗口
     *      → tryLock 不传 leaseTime = 看门狗(watchdog)每 10s 自动续期, 干完活才放锁
     *   ② 直接 DEL 可能误删别人的锁 (要自己写 UUID + Lua 才严谨)
     *      → unlock() 内置 Lua 只删自己的锁, 再加 isHeldByCurrentThread() 双保险
     *   ③ 没抢到锁只能 sleep(50) 盲等 + 递归重试, 醒早了白跑一趟
     *      → tryLock(等待时间) 底层是 pub/sub: 持锁人 unlock 时【广播通知】等锁的人,
     *        锁一放立刻被唤醒, 不空转、不瞎猜时间
     */
    private Product loadWithMutex(Long id, String key) {
        // ① 拿锁对象 (只是本地对象, 还没碰 Redis; 锁名和写法一保持同一个 key)
        RLock lock = redissonClient.getLock("lock:product:" + id);
        try {
            // ② 抢锁: 最多等 3 秒 (缓存重建就一条 selectById, 3 秒等不到说明出大事了)
            //    ⚠️ 只传等待时间、不传 leaseTime —— 触发看门狗自动续期 (和 order 模块同款用法)
            if (!lock.tryLock(3, TimeUnit.SECONDS)) {
                // 等 3 秒还没拿到锁(极端情况) → 不再硬等, 直查 DB 兜底但【不回填缓存】
                // (回填是持锁人的活, 我抢着写可能把新数据盖成旧数据)
                log.warn("等锁超时, 直查DB兜底 key={}", key);
                return productMapper.selectById(id);
            }

            // ③ 抢到锁 → 双重检查: 等锁期间前一个持锁人大概率已经重建好缓存了
            //    (这正是 pub/sub 等锁比 sleep 好的地方: 被唤醒时缓存刚写完, 这里几乎必命中)
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                if (cached instanceof String) return null;   // 空值标记(防穿透)
                return (Product) cached;
            }

            // ④ 缓存确实没有 → 只有我这一个线程查 DB + 回填 (逻辑与写法一完全相同)
            Product product = productMapper.selectById(id);
            if (product == null) {
                redisTemplate.opsForValue().set(key, "", 2, TimeUnit.MINUTES);   // 穿透: 空值
                return null;
            }
            long expireSeconds = 10 * 60 + ThreadLocalRandom.current().nextInt(120);   // 雪崩: 随机
            redisTemplate.opsForValue().set(key, product, expireSeconds, TimeUnit.SECONDS);
            return product;

        } catch (InterruptedException e) {
            // tryLock(等待时间) 声明了 InterruptedException: 等锁途中线程被中断会走这里
            Thread.currentThread().interrupt();
            return productMapper.selectById(id);   // 被中断兜底: 直查 DB, 不缓存
        } finally {
            // ⑤ 释放锁: isHeldByCurrentThread 先问"锁真是我的吗"再放
            //    (防两种情况: 没抢到锁走了兜底分支 / 极端下锁已易主 —— 都不该 unlock)
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // SEC-2: 商品变更后发 MQ，让 search 服务异步对齐 ES 索引 (上下架/库存联动)


    private void publishSearchSyncEvent(Long productId, String routingKey) {
        if (productId == null) {
            return;
        }
        Runnable publisher = () -> {
            try {
                rabbitTemplate.convertAndSend(ProductSearchMQConfig.EXCHANGE, routingKey, productId.toString());
                log.info("[search-sync-mq] published routingKey={} productId={}", routingKey, productId);
            } catch (Exception e) {
                log.warn("[search-sync-mq] publish failed routingKey={} productId={} err={}",
                        routingKey, productId, e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publisher.run();
                }
            });
        } else {
            publisher.run();
        }
    }

    /**
     * SEC-3: 重写 save, 新增商品同步写进布隆
     * 两个入口(ProductController / AdminProductController)都走 service.save, 收口这一处;
     * super.save 落库后 MP 自动回填自增 id, 才拿得到 getId()
     * (删除不用管布隆 —— 它删不掉, 已删商品由空值缓存那层兜底)
     */
    @Override
    public boolean save(Product entity) {
        boolean ok = super.save(entity);
        if (ok) {
            productBloomFilter.add(entity.getId());
            log.info("[布隆同步] 新商品加入布隆 id={}", entity.getId());
            publishSearchSyncEvent(entity.getId(), ProductSearchMQConfig.ROUTING_KEY_UPDATED);
        }
        return ok;
    }

    /** 改 MySQL → 删缓存(下次详情请求会回查 + 重新写) → 通知 ES 对齐 */
    @Override
    public boolean updateProduct(Product product) {
        boolean ok = updateById(product);

        if (ok) {
            redisTemplate.delete(
                    "product:detail:" + product.getId()
            );

            log.info("缓存已删除");

            publishSearchSyncEvent(
                    product.getId(),
                    ProductSearchMQConfig.ROUTING_KEY_UPDATED);



        }
        return ok;
    }

    /** 删 MySQL(逻辑删) → 删缓存 → 从 ES 索引移除 */
    @Override
    public boolean deleteProduct(Long id) {
        boolean ok = removeById(id);
        if (ok) {
            redisTemplate.delete("product:detail:" + id);
            log.info("缓存已删除 key=product:detail:{}", id);
            // ⭐ SEC-2: 已删商品不该再被搜到, 发 MQ 让 search 从索引删除
            publishSearchSyncEvent(id, ProductSearchMQConfig.ROUTING_KEY_DELETED);
        }
        return ok;
    }

    /** 分页搜索 + 关键字写热搜 ZSet (24h 过期) */
    @Override
    public IPage<Product> searchProducts(Integer page, Integer size,
                                        Long categoryId, String keyword,
                                        BigDecimal minPrice, BigDecimal maxPrice) {
        Page<Product> pageObj = new Page<>(page, size);

        QueryWrapper<Product> w = new QueryWrapper<>();
        // ⭐ SEC-2 上下架过滤: C 端列表只展示"已上架"(status=1)商品。
        //    后台管理走 /admin/product/page(自己的查询, 支持按 status 筛选), 不受影响。
        //    注意 getProductDetail(详情)故意【不】过滤: order 下单时靠它拿到下架商品
        //    才能报"商品已下架"; C 端详情页也可给已收藏的下架商品显示状态。
        w.eq("status", (byte) 1);
        if (categoryId != null) w.eq("category_id", categoryId);
        if (StringUtils.hasText(keyword)) {
            w.like("name", keyword);
            redisTemplate.opsForZSet().incrementScore("hot:search", keyword, 1);
            redisTemplate.expire("hot:search", 24, TimeUnit.HOURS);
            log.info("记录热搜 keyword={}", keyword);
        }
        if (minPrice != null) w.ge("price", minPrice);
        if (maxPrice != null) w.le("price", maxPrice);
        w.orderByDesc("create_time");

        return this.page(pageObj, w);
    }

    /** G3.10 扣库存: 直接转发给 Mapper 的原子 SQL */
    @Override
    public int deductStock(Long productId, Integer quantity) {
        int rows = productMapper.deductStock(productId, quantity);
        if (rows > 0) {
            redisTemplate.delete("product:detail:" + productId);
            log.info("扣库存成功 productId={} qty={}", productId, quantity);
            publishSearchSyncEvent(productId, ProductSearchMQConfig.ROUTING_KEY_UPDATED);
        } else {
            log.warn("扣库存失败(库存不足) productId={} qty={}", productId, quantity);
        }
        return rows;
    }

    /** G3.10 回库存 */
    @Override
    public int restoreStock(Long productId, Integer quantity) {
        int rows = productMapper.restoreStock(productId, quantity);
        if (rows > 0) {
            redisTemplate.delete("product:detail:" + productId);
            log.info("回库存成功 productId={} qty={}", productId, quantity);
            publishSearchSyncEvent(productId, ProductSearchMQConfig.ROUTING_KEY_UPDATED);
        }
        return rows;
    }

    /** 取 ZSet 倒序前 N 个 + score */
    @Override
    public List<Map<String, Object>> getHotSearch(int topN) {
        Set<ZSetOperations.TypedTuple<Object>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores("hot:search", 0, topN - 1);

        List<Map<String, Object>> result = new ArrayList<>();
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<Object> t : tuples) {
                Map<String, Object> item = new HashMap<>();
                item.put("keyword", t.getValue());
                item.put("count", t.getScore());
                result.add(item);
            }
        }
        return result;
    }

    /**
     * G7.7 重算并回写商品评分聚合 (G7 重构: 改用 Feign 调 review 服务)
     *
     * Cache Aside 写流程:
     *   ① ★ Feign 调 review.getStats → 拿 (avgRating, reviewCount)
     *      (重构前: 自己 mapper 直查 reviews 表 - 违反"一服务一表")
     *   ② UPDATE product SET avg_rating=?, review_count=?
     *   ③ DEL product:detail:{id}
     */
    @Autowired
    private com.minimall.product.client.ReviewFeignClient reviewFeignClient;

    @Override
    public void refreshRating(Long productId) {
        // ─── Step 1: Feign 调 review 拿评分聚合 ────
        com.minimall.common.core.domain.Result<com.minimall.product.vo.ReviewStatsVO> resp =
                reviewFeignClient.getStats(productId);

        // 降级保护: review 挂了 fallback 返 null, 跳过本次刷新避免把已有评分清 0
        if (resp == null || resp.getCode() != 200 || resp.getData() == null) {
            log.warn("[refreshRating] 取评分聚合失败, 跳过 productId={}", productId);
            return;
        }
        com.minimall.product.vo.ReviewStatsVO stats = resp.getData();

        BigDecimal avgRating = stats.getAvgRating() == null
                ? BigDecimal.ZERO
                : stats.getAvgRating().setScale(1, BigDecimal.ROUND_HALF_UP);  // DECIMAL(2,1)
        Integer reviewCount  = stats.getReviewCount() == null ? 0 : stats.getReviewCount();

        // ─── Step 2: UPDATE product ──────────────
        Product p = new Product();
        p.setId(productId);
        p.setAvgRating(avgRating);
        p.setReviewCount(reviewCount);
        productMapper.updateById(p);

        // ─── Step 3: DEL 缓存 (Cache Aside 写策略) ──
        String cacheKey = "product:detail:" + productId;
        redisTemplate.delete(cacheKey);

        log.info("[refreshRating] productId={} avg={} count={} 缓存已删", productId, avgRating, reviewCount);
    }
    @Override
    public List<Product> listAllForSync() {
        // 只灌"已上架"商品 (status=1), MP 自动加 is_deleted=0 过滤
        return lambdaQuery().eq(Product::getStatus, (byte) 1).list();
    }
}
