# 05 RabbitMQ 消息队列

> **配套教科书笔记（系统基础，配这份面试速记一起看）**
> - `../散笔记/rabbitMq和kafka/RabbitMQ基础与实战.docx` —— AMQP模型 / 4种交换机 / 可靠性三环 / 死信延迟 / Spring整合全代码
> - `../散笔记/rabbitMq和kafka/Kafka基础与实战.docx` —— 分区副本 / ISR / acks机制 / 高性能原理 / RabbitMQ对比选型
>
> 本文件是**结合项目的面试速记**；上面两份 docx 是**从零讲起的原理教程**。基础不牢先看 docx，冲面试背本文件。

## 项目里 MQ 的三个用途（先总览）

| 队列组 | 用途 | 模式 |
|--------|------|------|
| order.delay.queue → order.close.queue | 订单 30 分钟超时自动关单 | TTL + 死信(DLX) |
| seckill.queue | 秒杀异步建单削峰 | 普通队列 + 手动ACK |
| product.search.sync 队列 | 商品变更同步 ES 索引 | 事件解耦（只传 id） |

---

## 知识点 1：延迟队列实现订单超时关单（TTL + 死信）

**【面试怎么问】** 订单 30 分钟未支付自动取消怎么实现？为什么不用定时任务扫表？

**【项目代码】** `mini-mall-order/.../config/RabbitMQConfig.java`

```java
@Bean
public Queue delayQueue() {
    Map<String, Object> args = new HashMap<>();
    args.put("x-message-ttl", 1800000);                       // ⏱ 30分钟 消息存活时间
    args.put("x-dead-letter-exchange", CLOSE_EXCHANGE);       // 💀 TTL到期变死信, 转发到关单交换机
    args.put("x-dead-letter-routing-key", CLOSE_ROUTING_KEY);
    return new Queue(DELAY_QUEUE, true, false, false, args);  // durable=true 持久化
}
```

链路全图（`OrderCloseListener` 类注释原图）：

```
createOrder() ──发──► delay.queue (无消费者, 消息死等30分钟)
                          │ TTL到期 → 成为死信
                          ▼ 按 x-dead-letter-* 配置转发
                      close.queue
                          │ @RabbitListener
                          ▼
              ordersService.closeOrderByMQ(orderId)
                          ▼
     UPDATE orders SET status=4 WHERE id=? AND status=0   ⭐ 幂等关键
```

**【讲解】**
- 原理：**故意造一个没有消费者的队列**，消息在里面等满 TTL 变成"死信"，RabbitMQ 自动按 `x-dead-letter-exchange` 转投到真正有消费者的关单队列——延迟效果 = TTL + 死信转发。
- vs 定时扫表：扫表有精度差（扫描间隔）、全表扫描压力、多实例重复执行问题；MQ 方案事件驱动、精度到秒、天然分布式。
- 一个真实教训写在注释里：TTL 原来是 30 秒（测试值），真付款来不及，会出现"付款成功但订单已被关"的资损，后来改 30 分钟——**TTL 值是业务参数不是技术参数**。
- 关单必须幂等 + 并发安全：消息到期时用户可能刚好在支付，`WHERE status=0` 的条件 UPDATE 保证"支付"和"关单"只有一方赢（详见 06）。

**【一分钟回答】** 下单成功发一条消息进"无消费者"的延迟队列，30 分钟 TTL 到期变死信，自动转投关单队列，消费者用 `UPDATE ... WHERE status=0` 的 CAS 关单——已支付的订单 rows=0 不受影响，重复消息也天然幂等。比扫表精度高、无重复执行问题。

---

## 知识点 2：幽灵消息问题（事务和 MQ 的时序）

**【面试怎么问】** 事务里发 MQ 有什么坑？

**【项目代码】** `OrdersServiceImpl.createOrder()`：

```java
// ⭐ 用 TransactionTemplate 而非 @Transactional 注解: 为了精确控制"事务边界在哪结束"
Map<String, Object> orderResult = transactionTemplate.execute(status -> {
    ... // 建订单、扣库存、清购物车 —— 全在事务内
    return result;
});

// ⭐⭐⭐ 发MQ放在 execute() 返回之后 = 事务已提交, 订单已确定落库
rabbitTemplate.convertAndSend(DELAY_EXCHANGE, DELAY_ROUTING_KEY, orderId);
```

另一个方向的时序问题（先提交后回调），`ProductServiceImpl.publishSearchSyncEvent()`：

```java
// 若当前在事务里: 注册 afterCommit 钩子, 事务提交后才发MQ
if (TransactionSynchronizationManager.isSynchronizationActive()) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { publisher.run(); }
    });
} else {
    publisher.run();
}
```

**【讲解】**
- **幽灵消息**：如果发 MQ 写在事务里，事务后面回滚了，消息却已经飞出去——30 分钟后消费者去关一个从没存在过的订单。所以必须"先提交，后发消息"。
- 两种写法达到同一目的：① `TransactionTemplate` 手动划边界，MQ 代码写在边界外（下单用这种，直白）；② `afterCommit` 事务同步钩子（商品同步用这种，适合发消息的代码深埋在事务方法内部时）。
- 反向风险要会答：先提交后发送，如果发送失败怎么办？→ 订单场景可接受（关单兜底丢了只是订单不自动关，可人工/对账处理）；要求高的场景用"本地消息表 + 定时补偿投递"。

**【一分钟回答】** 事务内发 MQ 会产生幽灵消息——事务回滚但消息已出。解法是保证"先提交后发送"：要么 TransactionTemplate 手动收窄事务边界把发送放外面，要么注册 afterCommit 回调。发送失败的反向风险按业务重要性选择容忍或本地消息表补偿。

---

## 知识点 3：手动 ACK / NACK 与消费可靠性

**【面试怎么问】** 消息怎么保证不丢？消费失败怎么处理？

**【项目代码】** `SeckillOrderListener`（所有消费者统一套路）：

```java
@RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
public void onSeckillMessage(String msg, Message message, Channel channel) throws IOException {
    long deliveryTag = message.getMessageProperties().getDeliveryTag();
    try {
        ...业务处理...
        channel.basicAck(deliveryTag, false);        // ✅ 成功才ACK, 消息从队列删除
    } catch (Exception e) {
        ...补偿(回滚Redis资格)...
        channel.basicNack(deliveryTag, false, false); // ❌ NACK且不requeue
    }
}
```

**【讲解】**
- 自动 ACK 的问题：消息一投递就算成功，消费者处理到一半崩了消息就丢了。手动 ACK 把"确认"推迟到业务成功后，消费者崩溃时未 ACK 的消息会重新投递（所以又引出幂等）。
- `basicNack(requeue=false)` 的选择：requeue=true 时**必然失败的消息**（脏数据）会无限循环重投打爆消费者；false 则丢弃（生产上应再绑一个死信队列收集这些消息供人工排查——项目注释里明确写了这是教学简化点，面试主动说出来是加分）。
- 不丢消息的完整链：生产端 confirm 机制（项目未开，可作为"我知道但没做"的诚实回答）→ 交换机/队列持久化（durable=true 项目有）→ 消费端手动 ACK（项目有）。

**【一分钟回答】** 全部消费者手动 ACK：业务成功才 basicAck；异常时先做补偿再 basicNack 不 requeue（防止毒消息无限循环），生产环境应配死信队列接住这些失败消息。配合队列持久化和消费幂等，构成消费侧的可靠性闭环。

---

## 知识点 4：用 MQ 做服务间解耦（商品→ES 同步）

**【面试怎么问】** 服务之间除了 Feign 同步调用，什么时候用 MQ？

**【项目代码】** 生产端 product 服务（改库后发事件）：

```java
rabbitTemplate.convertAndSend(ProductSearchMQConfig.EXCHANGE, routingKey, productId.toString());
// routingKey 两种: product.updated(上架/编辑/库存变动) / product.deleted(删除)
```

消费端 search 服务 `ProductSearchSyncListener`：

```java
// ⭐ 消息体只有 productId! 消费时回查 product 服务拿最新数据
if (ROUTING_KEY_UPDATED.equals(routingKey)) {
    productSearchService.syncById(productId);   // 回查: 在售→upsert进ES; 下架/没了→从ES删
}
```

**【讲解】**
- Feign vs MQ 的选择标准，项目里正反例都有：
  - **下单扣库存用 Feign（同步）**：结果必须立刻知道（库存不足要马上告诉用户），强一致诉求。
  - **商品变更同步 ES 用 MQ（异步）**：搜索索引晚几百毫秒更新完全无感，且 product 不应该因为 search 挂了而无法编辑商品——**削峰、解耦、容忍最终一致**的场景用 MQ。
- **消息只传 id 不传全量数据**是个精妙设计：① 消息永不过期作废（消费时回查的总是最新态）；② 重复消费 = 重复回查 upsert，天然幂等；③ 消息乱序也不怕（后消费的也是查最新数据）。
- 消费端还有个容错细节：product 服务不可用（Feign fallback 返回 503）时**跳过而不是删索引**——"宁可搜索结果暂时陈旧，也不能因服务抖动让商品从搜索里消失"。

**【一分钟回答】** 需要即时结果、强一致的用 Feign 同步调（扣库存）；容忍延迟、需要解耦的用 MQ（ES 索引同步）。我们的事件消息只携带 productId，消费者回查最新数据再决定 upsert 还是删除，天然幂等且不怕乱序；源服务抖动时选择跳过而非删索引，是可用性优先的取舍。

---

## 知识点 5：重复消费与幂等（五种方案，项目用①+②）

**【面试怎么问】** MQ 怎么保证消息不被重复消费？消费幂等怎么做？

**【为什么会重复】** 消费者业务处理成功了，但 `basicAck` 还没发出去就宕机 → Broker 以为没消费 → 重新投递。所以 MQ 本质是"至少一次(at least once)"，**重复是常态，幂等必须消费端自己兜**。

**【五种幂等方案】**

| 方案 | 做法 | 定位 |
|------|------|------|
| ① 业务唯一键 select 判重 | 处理前先查"这单处理过没" | 优化，减少无用操作 |
| ② 数据库唯一索引 | 同一业务键第二次 insert 直接报错 | **最可靠，安全底线** |
| ③ Redis SETNX 去重表 | `setIfAbsent(消息ID)` 成功才处理 | 无业务唯一键时用 |
| ④ 状态机 | 只允许 待付款→已付款，重复的挡在状态判断外 | 有状态流转场景 |
| ⑤ 乐观锁 version | `update ... where version=旧值` | 并发更新场景 |

**【项目代码】** 秒杀是 **①select优化 + ②唯一索引兜底** 的标准组合，`SeckillOrderListener.createNormalOrder()`：

```java
// ① select 预判：查过就跳过（优化，挡掉大部分重复）
QueryWrapper<SeckillOrder> w = new QueryWrapper<>();
w.eq("user_id", userId).eq("seckill_activity_id", activityId);
if (seckillOrderMapper.selectCount(w) > 0) {
    return null;   // 已处理，幂等跳过
}
// ② 即使两条并发都 select 到 0 都往下走，
//    seckill_order 表的唯一索引 uk_user_activity(user_id, seckill_activity_id)
//    会让第二条 insert 直接抛异常 → 数据库层兜底，绝不重复下单
```

**【讲解】**
- **核心原则**：select 预判 = 优化（省掉大部分无用功），唯一索引 = 安全底线（并发也拦得住）。只有 select 没唯一索引 = 有并发漏洞；只有唯一索引没 select = 每次都要 insert 一次撞库效率低。**两个一起才是完整方案。**
- 关单场景（知识点1）用的是**方案④状态机**：`UPDATE ... WHERE status=0`，重复消息 rows=0 天然幂等。
- 商品→ES 同步（知识点4）用的是**"只传 id 回查 upsert"**，重复消费 = 重复 upsert，也天然幂等。

**【一分钟回答】** MQ 只保证至少一次，重复靠消费端幂等。项目秒杀用 select 预判 + 唯一索引 uk_user_activity 双保险：select 挡掉绝大多数重复是优化，唯一索引是并发下的安全底线。关单用状态机 WHERE status=0，ES 同步用只传 id 回查 upsert，三种幂等手段按场景选。

---

## 知识点 6：生产者可靠性 —— Publisher Confirm（项目缺口，诚实答）

**【面试怎么问】** 消息从生产者到 Broker 这一段怎么保证不丢？

**【不丢消息三环节】** 一条消息要经过三段路，每段都可能丢：

```
① 生产者 ──> ② 交换机/队列(Broker存储) ──> ③ 消费者
   confirm确认      持久化 durable          手动ACK
   （项目❌未开）    （项目✅ durable=true）  （项目✅ manual ack）
```

**【生产者这一环的两个机制】**

| 机制 | 解决 | 配置 |
|------|------|------|
| publisher-confirm | 消息有没有到交换机 | `confirm-type: correlated` |
| publisher-return | 到了交换机但没进队列（routingKey写错） | `publisher-returns: true` + `mandatory: true` |

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated   # 到交换机的确认回调
    publisher-returns: true              # 没进队列的退回回调
    template:
      mandatory: true
```
```java
rabbitTemplate.setConfirmCallback((cd, ack, cause) -> {
    if (!ack) log.error("消息没到交换机, 原因:{}", cause);   // 补发/记录
});
rabbitTemplate.setReturnsCallback(r ->
    log.error("消息没进队列:{}", r.getMessage()));
```

**【讲解】**
- 项目当前 yml **没配这两项**，这是真实可靠性缺口。面试可以诚实答："我们持久化和消费端手动 ACK 都做了，生产端 confirm 目前没开——因为订单场景有关单兜底和对账，能容忍极低概率的生产端丢失；要求更高会补 confirm + 本地消息表。" 这种"知道缺什么、为什么可以缺"的回答比假装全都做了更可信。
- confirm 是**异步回调**，不阻塞发送；不像同步等待，性能损耗小。

**【一分钟回答】** 不丢消息分三环：生产端 publisher-confirm/return、Broker 端持久化、消费端手动 ACK。我们项目持久化和手动 ACK 都做了，生产端 confirm 暂未开——订单有关单兜底和对账可容忍，要求更高会补 confirm 加本地消息表。

---

## 知识点 7：消息真丢了怎么补救（本地消息表 + 对账）

**【面试怎么问】** 万一消息还是丢了，数据不一致了怎么恢复？

**【三层补救，从自动到人工】**

```
① 本地消息表 + 定时重发   —— 自动重试，最常用
② 对账 / 补偿定时任务     —— 兜底扫描不一致数据修复
③ 死信队列 + 人工         —— 前两层都失败，人工介入
```

**【① 本地消息表原理】** 关键是**消息记录和业务数据写在同一个本地事务里**，消息一定不会因为发 MQ 失败而丢：

```
同一个数据库事务：
   ├── 写业务数据（如订单）
   └── 写 mq_message 表一条记录 status=0(待确认)
事务提交后才发 MQ；confirm 回调成功 → 把 status 改 1(已确认)

定时任务扫描：status=0 且 create_time <= now-1分钟 且 retry_count<3
   → 说明发了但没确认（可能丢了）→ 重发 + retry_count+1
```

**【mq_message 表字段】**（可参照 `LogisticsScheduledTask` 定时任务模板放在 order 服务 task/ 包）：

| 字段 | 说明 |
|------|------|
| message_id | 消息唯一ID（消费端拿它做幂等） |
| exchange / routing_key | 重发时要用 |
| payload | 消息体 |
| status | 0待确认 / 1已确认 / 2重试超限告警 |
| retry_count | 重试次数，超限停止并告警 |
| create_time / update_time | 扫描时间窗判断 |

**【讲解】**
- 时间窗（now-1分钟）的意义和 `LogisticsScheduledTask` 的 7 天 cutoff 是同一个思路：**给正常流程留出缓冲，只捞真正卡住的**。刚发出去还没来得及 confirm 的别急着重发。
- **重发必然导致重复消费** → 所以消费端必须幂等（回到知识点5，闭环）。message_id 就是消费端幂等的唯一键。
- **一个真实资损场景**（项目秒杀）：若生产端消息丢了，Redis 库存已扣但订单没建，而 `rollbackSeckillQuota` 在消费端 catch 里、消费者根本没触发 → **只能靠对账任务**扫 Redis 已扣但无对应订单的记录来回补。这就是为什么光有 catch 回滚不够，还要对账。
- 多实例重复调度问题：`@Scheduled` 每个实例都跑，多实例会重复扫描重发 → 用 Redis 分布式锁/选主/XXL-Job（`LogisticsScheduledTask` 注释里有对比）。

**【一分钟回答】** 三层补救：本地消息表把消息记录和业务写在同一事务，定时任务扫 status=0 超时未确认的重发（重发靠 message_id 幂等去重）；对账任务兜底扫不一致数据，比如秒杀 Redis 扣了库存但没订单的记录回补；死信队列 + 告警做最后人工兜底。

---

## 知识点 8：百万消息堆积怎么办（泄洪三步）

**【面试怎么问】** 线上 MQ 堆积了 100 万消息怎么处理？

**【堆积 = 消费速度跟不上生产速度，三步走：先定位 → 再泄洪 → 后预防】**

**① 先定位**（管理台 `localhost:15672` 看积压数 + 消费速率）：

| 原因 | 现象 |
|------|------|
| 消费者挂了 | 消费速率=0，积压直线涨 |
| 消费太慢 | 速率远低于生产 |
| 卡在毒消息 | 一条反复 nack-requeue 堵住后面 |
| 流量突增 | 秒杀/大促洪峰 |

**② 再泄洪**：
```
1. 扩容消费者：多起实例，一队列多消费者竞争消费（N倍速）
2. 调大并发：concurrency:10  max-concurrency:20  prefetch:50
   （项目现在 prefetch=1 太保守，堆积时临时调大）
3. 临时多队列分流：写搬运程序把积压均分到 N 个临时队列，N倍消费者并行
4. 消费端提速：批量DB、去掉不必要的同步 Feign 调用
```

**③ 一个坑：堆积 + TTL = 丢消息**
- 项目延迟队列有 `x-message-ttl=30分钟`，堆积超过 TTL 消息会过期变死信被丢/转走 → **泄洪要抢在 TTL 之前**。

**【项目视角的亮点】** 秒杀链路 **Redis 预扣库存挡在 MQ 之前**：百万请求先被 Redis 库存拦掉 99%，进 MQ 的消息量 ≈ 库存数量（比如 1000 件），**天然到不了百万**。这说明"MQ 不是用来扛百万并发的，前面要有削峰"，面试点出这个是加分。

**【一分钟回答】** 三步：先看管理台定位是消费者挂了、太慢还是卡毒消息；再泄洪——最快是水平扩容消费者 + 调大 concurrency/prefetch，队列结构受限就临时多队列分流；后预防上游限流削峰。我们项目秒杀 Redis 预扣库存挡在 MQ 前，进队列的量被库存数限死，天然不会堆到百万；另外带 TTL 的队列堆积会丢消息，泄洪得抢在过期前。
