# 03 Redis 高频考点（缓存三大问题 + 分布式锁）

> 本项目的商品详情接口 `ProductServiceImpl.getProductDetail()` 一个方法就把穿透/击穿/雪崩三个问题全防了，是面试最好用的一段代码。

## 知识点 1：缓存穿透（查不存在的数据）—— 布隆过滤器 + 空值缓存双层防

**【面试怎么问】** 大量请求查不存在的 id（恶意攻击），缓存永远不命中全打到 DB 怎么办？

**【项目代码】** `mini-mall-product/.../service/impl/ProductServiceImpl.java`

```java
public Product getProductDetail(Long id) {
    // ⓪ 第一层: 布隆过滤器前置拦截。false = 一定不存在, 缓存和 DB 都不用碰
    if (!productBloomFilter.contains(id)) {
        return null;
    }
    String key = "product:detail:" + id;
    Object cached = redisTemplate.opsForValue().get(key);
    if (cached != null) {
        // ① 第二层: 命中"空值标记"(存的是空字符串) → 之前查过"无此商品", 不再打 DB
        if (cached instanceof String) return null;
        return (Product) cached;
    }
    return loadWithMutex(id, key);   // 未命中 → 互斥重建(见击穿)
}
```

布隆的初始化与预热 `BloomFilterConfig.java`：

```java
@Bean
public RBloomFilter<Long> productBloomFilter(RedissonClient redissonClient) {
    RBloomFilter<Long> filter = redissonClient.getBloomFilter("product:bloom");
    filter.tryInit(100_000L, 0.01);   // 预期10万商品, 1%误判率 ≈ 117KB位数组+7个哈希函数
    return filter;
}
@Bean
public ApplicationRunner bloomWarmUp(...) {   // 启动时把全量商品 id 灌进布隆
    return args -> { for (Object id : ids) productBloomFilter.add(...); };
}
```

**【讲解】**
- 布隆语义铁律：**false = 一定不存在（放心拒绝）；true = 只是可能存在**（有 1% 假阳性）。所以布隆只能当第一层，假阳性漏过去的靠第二层空值缓存兜底。
- 空值缓存 TTL 只给 2 分钟（真数据 10 分钟）：万一后来商品创建了，脏空值最多存活 2 分钟。
- 布隆不支持删除 → 两个配套设计：新增商品时 `save()` 里同步 `bloomFilter.add(id)`；已删商品布隆里还是 true，由空值缓存兜住；重启预热顺带"重建"掉历史脏位。
- 用类型区分空值标记（Product 对象 vs String 空串），不用魔法值。

**【一分钟回答】** 双层防：第一层布隆过滤器启动时预热全量 id，contains=false 直接拒绝，连缓存都不查；第二层空值缓存兜布隆 1% 假阳性和已删商品（布隆删不掉），空值 TTL 短一些减少脏窗口。新增商品实时 add 进布隆。

---

## 知识点 2：缓存击穿（热点 key 过期瞬间）—— Redisson 互斥锁重建

**【面试怎么问】** 热点 key 过期一瞬间上千请求同时打 DB 怎么办？

**【项目代码】** 同文件 `loadWithMutex()`：

```java
private Product loadWithMutex(Long id, String key) {
    RLock lock = redissonClient.getLock("lock:product:" + id);
    try {
        // ② 抢锁最多等3秒; ⚠️ 只传等待时间、不传 leaseTime → 触发看门狗自动续期
        if (!lock.tryLock(3, TimeUnit.SECONDS)) {
            return productMapper.selectById(id);   // 等锁超时 → 直查DB兜底但【不回填缓存】
        }
        // ③ 双重检查: 等锁期间前一个持锁人大概率已经把缓存建好了
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) { ... return (Product) cached; }

        // ④ 只有我一个线程查 DB + 回填
        Product product = productMapper.selectById(id);
        if (product == null) {
            redisTemplate.opsForValue().set(key, "", 2, TimeUnit.MINUTES);      // 穿透:空值
            return null;
        }
        long expireSeconds = 10 * 60 + ThreadLocalRandom.current().nextInt(120); // 雪崩:随机TTL
        redisTemplate.opsForValue().set(key, product, expireSeconds, TimeUnit.SECONDS);
        return product;
    } finally {
        if (lock.isHeldByCurrentThread()) lock.unlock();   // 锁真是我的才放
    }
}
```

**【讲解】**（为什么用 Redisson 不手写 SETNX——项目是先手写后升级的，两个版本都能讲）
- 手写 SETNX 三个硬伤 → Redisson 对应解法：
  1. **固定过期时间**：DB 慢查超过锁 TTL 锁自动没了，别的线程闯入 → 看门狗（watchdog）每 10s 自动续期，干完活才放锁；
  2. **DEL 可能误删别人的锁**：要自己写 UUID+Lua → `unlock()` 内置 Lua 只删自己的，外加 `isHeldByCurrentThread()` 双保险；
  3. **抢不到锁只能 sleep 盲等**：→ `tryLock(等待时间)` 底层是 pub/sub，持锁人 unlock 时广播唤醒等待者，不空转。
- **双重检查**必须有：被唤醒时缓存刚被上一个持锁人写好，直接命中，不重复查 DB。
- 等锁超时的兜底"直查 DB 但不回填缓存"：回填是持锁人的职责，我抢着写可能把新数据盖成旧数据。

**【一分钟回答】** 缓存未命中先抢 Redisson 分布式锁，只放一个线程查 DB 重建缓存；拿到锁先双重检查（等锁期间可能已重建好）；看门狗解决业务超时锁失效，unlock 内置 Lua 防误删，pub/sub 唤醒替代轮询。等锁超时降级直查 DB 但不回填。

---

## 知识点 3：缓存雪崩（大批 key 同时过期）—— 过期时间加随机

**【项目代码】** 一行，就在上面第④步里：

```java
// 基础10分钟 + 随机0~120秒: 同一批写入的key过期时间被打散, 不会同一秒集体失效
long expireSeconds = 10 * 60 + ThreadLocalRandom.current().nextInt(120);
```

**【一分钟回答】** 雪崩是大量 key 同时过期导致 DB 瞬时洪峰，我们给 TTL 加随机抖动打散过期时间点；更完整的方案还有多级缓存、热点永不过期+异步刷新、以及 Redis 高可用（哨兵/集群）防的是"Redis 整个挂了"这种雪崩。

---

## 知识点 4：缓存一致性（Cache Aside 旁路缓存）

**【面试怎么问】** 更新数据时先改库还是先删缓存？

**【项目代码】** `ProductServiceImpl.updateProduct()`：

```java
public boolean updateProduct(Product product) {
    boolean ok = updateById(product);                          // ① 先更新 MySQL
    if (ok) {
        redisTemplate.delete("product:detail:" + product.getId());   // ② 再删缓存
        publishSearchSyncEvent(product.getId(), ROUTING_KEY_UPDATED); // ③ 发MQ让ES对齐
    }
    return ok;
}
```

**【讲解】**
- 项目选的是最经典的 **Cache Aside：更新 DB → 删缓存**（不是改缓存）。删而不改的原因：改缓存在并发下会出现旧值覆盖新值；删了之后下次读自动回源重建，逻辑最简单。
- 为什么"先库后删"而不是"先删后库"：先删后库的窗口期里，读请求会把**旧库值**重新灌进缓存，脏得更久。
- 读路径回填 + 写路径删除，配合空值缓存和随机 TTL，构成完整闭环。
- 评分聚合 `refreshRating()` 也是同一套：Feign 拿统计 → UPDATE product → DEL 缓存。

**【一分钟回答】** Cache Aside：读时未命中回源并回填；写时先更新数据库再删除缓存（删除而非更新，避免并发写覆盖）。极端的"删除后、回填前读到旧值"窗口存在，但概率低且有 TTL 兜底，对电商详情页可接受；要求更强一致可以用延迟双删或订阅 binlog。

---

## 知识点 5：分布式锁的业务应用（防重复下单）

**【面试怎么问】** 分布式锁用在哪？为什么不用 synchronized？

**【项目代码】** `mini-mall-order/.../OrdersServiceImpl.createOrder()`：

```java
// 同用户并发下单互斥(防双击/防重复下单); 多实例部署时 synchronized 只锁得住单个JVM
RLock lock = redissonClient.getLock("lock:order:user:" + userId);
if (!lock.tryLock()) {                       // 无参 = 非阻塞, 抢不到立即失败
    throw new BusinessException(429, "操作太频繁, 请稍后再试");
}
try {
    ... // 事务内建单
} finally {
    if (lock.isHeldByCurrentThread()) lock.unlock();
}
```

**【讲解】**
- 锁粒度是设计点：下单锁 `user:{userId}`（同一用户串行，不同用户互不影响）；支付锁 `pay:{orderId}`（允许同时支付多个订单，但同一单只能一次）。**key 里编码了业务语义**。
- 非阻塞 `tryLock()` + 立即报 429：用户双击的第二次请求应该快速失败，而不是排队再下一单。
- 注意：锁防的是"重复动作"，数据正确性的最后防线是数据库 CAS 状态机（见 06 一致性）——锁 + CAS 是两层，不是二选一。

**【一分钟回答】** 多实例部署 JVM 锁失效，用 Redisson 分布式锁。下单按 userId 加锁防双击，非阻塞抢锁失败直接 429。锁只是第一层拦截，真正的正确性由数据库条件 UPDATE 兜底——锁丢了也不会出错，只会多打一次 DB。

---

## 知识点 6：Redis 数据结构的业务化使用

**【项目代码】** 项目里每种结构都有真实落点：

| 结构 | 项目用途 | 代码位置 |
|------|---------|---------|
| String | 商品详情缓存、空值标记、验证码、JWT黑名单 | ProductServiceImpl / EmailAuthController |
| String(计数) | 秒杀库存 `DECR`、验证码错误计数 `INCR` | seckill_stock.lua |
| Set | 秒杀已购用户 `SADD/SISMEMBER`（防一人多单） | seckill_stock.lua |
| ZSet | 热搜榜 `incrementScore` + `reverseRangeWithScores` | ProductServiceImpl.searchProducts |
| SETNX | 验证码60秒重发限制 | EmailAuthController |
| 布隆(bitmap) | 商品id防穿透 | BloomFilterConfig |
| 向量检索(Redis Stack) | AI 知识库 EmbeddingStore | LangChainConfig |

热搜榜两行代码：

```java
redisTemplate.opsForZSet().incrementScore("hot:search", keyword, 1);   // 搜索词计数+1
// 取榜: reverseRangeWithScores("hot:search", 0, topN-1) 倒序前N
```

**【一分钟回答】** 按场景选结构：计数用 String 原子 INCR/DECR，去重判断用 Set，排行榜用 ZSet（score 即热度），互斥/限频用 SETNX+TTL，海量存在性判断用布隆。我们项目热搜榜就是一个 ZSet 两行代码搞定。
