# 05 RabbitMQ 消息队列

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
