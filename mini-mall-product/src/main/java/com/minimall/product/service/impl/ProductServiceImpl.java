package com.minimall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minimall.product.entity.Product;
import com.minimall.product.mapper.ProductMapper;
import com.minimall.product.service.IProductService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
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
     * 击穿防护: 缓存未命中时, 用 SETNX 手写互斥锁重建缓存。
     * 目标: 热点 key 过期瞬间, 只放【一个】线程去查 DB + 回填,
     *       其他线程短暂等待后重读缓存, 避免一万个请求同时打 DB。
     */
    private Product loadWithMutex(Long id, String key) {
        String lockKey = "lock:product:" + id;
        boolean holdlock = false;          // ⭐ 第1处: 方法级标志, 记录"我是否真正持有锁"(finally 要用它)
        try {
            // TODO ① 抢锁: SETNX (不存在才设成功), 加 10 秒过期防死锁
                Boolean locked = redisTemplate.opsForValue()
                       .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);

            // TODO ② 没抢到锁(别人正在重建) → sleep 50ms 让一下, 再重走 getProductDetail
            //    (这次大概率缓存已被别人重建好, 直接命中缓存)
               if (!Boolean.TRUE.equals(locked)) {
                   Thread.sleep(50);
                    return getProductDetail(id);
                }
            holdlock = true;   // ⭐ 第2处: 能走到这 = 没被上面 return 掉 = 确实抢到了锁

            // TODO ③ 抢到锁了 → 双重检查: 再查一次缓存(等锁期间别人可能已重建好, 就不用查 DB 了)
            Object cached = redisTemplate.opsForValue().get(key);
               if (cached != null) {
                   if (cached instanceof String) return null;   // 空值标记
                    return (Product) cached;
                       }

            // ④ 确实没有 → 只有我这一个线程查 DB (下面回填逻辑复用 Step1)
            Product product = productMapper.selectById(id);
            if (product == null) {
                redisTemplate.opsForValue().set(key, "", 2, TimeUnit.MINUTES);   // 穿透: 空值
                return null;
            }
            long expireSeconds = 10 * 60 + ThreadLocalRandom.current().nextInt(120);   // 雪崩: 随机
            redisTemplate.opsForValue().set(key, product, expireSeconds, TimeUnit.SECONDS);
            return product;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return productMapper.selectById(id);   // 被中断兜底: 直查 DB, 不缓存
        } finally {
            // TODO ⑥ 释放锁: DEL lockKey (务必放 finally, 否则异常时锁不释放 = 死锁)
            //    ⚠️ 坑: 这里简化直接 del; 严谨做法要判断"是不是我的锁"(存唯一值 + Lua 删),
            //       否则可能误删别人的锁 —— 这正是 Redisson 用 UUID + Lua 解决的问题!
                if(holdlock)
            redisTemplate.delete(lockKey);
        }
    }

    // SEC-2: 商品变更后通知 search 服务对齐 ES 索引 (上下架联动)
    @Autowired
    private com.minimall.product.client.SearchFeignClient searchFeignClient;

    /**
     * SEC-2 辅助: 通知 search 增量同步, 失败只记日志绝不抛
     * —— 搜索同步是从流程, 挂了不能拖垮商品管理主流程 (最坏靠全量 /search/sync 兜底)。
     */
    private void notifySearchSync(Long productId) {
        try {
            searchFeignClient.syncById(productId);
        } catch (Exception e) {
            log.warn("[search-sync] 通知失败(忽略) productId={} err={}", productId, e.getMessage());
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
        }
        return ok;
    }

    /** 改 MySQL → 删缓存(下次详情请求会回查 + 重新写) → 通知 ES 对齐 */
    @Override
    public boolean updateProduct(Product product) {
        boolean ok = updateById(product);
        if (ok) {
            redisTemplate.delete("product:detail:" + product.getId());
            log.info("缓存已删除 key=product:detail:{}", product.getId());
            // ⭐ SEC-2: 编辑/上架/下架 共用本方法 → 通知 search 回查最新状态,
            //    上架→upsert 进索引, 下架→从索引删, 让"搜索结果"跟"数据库"对齐。
            //    顺序注意: 必须放在删缓存【之后】, search 回查 /product/{id} 才拿得到新数据。
            notifySearchSync(product.getId());
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
            // ⭐ SEC-2: 已删商品不该再被搜到, 直接从索引删 (失败同样只记日志)
            try {
                searchFeignClient.deleteById(id);
            } catch (Exception e) {
                log.warn("[search-sync] 索引删除失败(忽略) productId={} err={}", id, e.getMessage());
            }
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
