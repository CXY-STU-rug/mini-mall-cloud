# mini-mall-cloud 面试知识点（按项目组织）

> 用法：每个文件 = 一个面试主题。每个知识点的结构固定为
> **【面试怎么问】→【项目里的代码在哪】→【代码段+讲解】→【一分钟回答模板】**。
> 面试前把"回答模板"过一遍，被追问细节时能直接落到自己写过的代码上——这是背八股永远做不到的。

## 文档目录

| 文件 | 主题 | 高频指数 |
|------|------|---------|
| [01-微服务架构与网关.md](01-微服务架构与网关.md) | Nacos注册发现、Gateway路由、过滤器链、traceId | ★★★★★ |
| [02-认证授权与安全设计.md](02-认证授权与安全设计.md) | JWT、登出黑名单、RBAC默认拒绝、OAuth2、上传魔数校验 | ★★★★★ |
| [03-Redis高频考点.md](03-Redis高频考点.md) | 缓存穿透/击穿/雪崩、布隆过滤器、Redisson分布式锁 | ★★★★★ |
| [04-秒杀系统.md](04-秒杀系统.md) | Lua原子扣减、MQ异步下单、防超卖防重复 | ★★★★★ |
| [05-RabbitMQ消息队列.md](05-RabbitMQ消息队列.md) | TTL+死信延迟关单、手动ACK、幂等消费、幽灵消息 | ★★★★☆ |
| [06-事务与数据一致性.md](06-事务与数据一致性.md) | CAS状态机、Seata、afterCommit、对账补偿、优惠券防超发 | ★★★★★ |
| [07-OpenFeign与Sentinel.md](07-OpenFeign与Sentinel.md) | 声明式调用、fallback设计、网关限流、规则持久化 | ★★★★☆ |
| [08-支付与退款.md](08-支付与退款.md) | 支付宝对接、回调四关（验签/幂等/校验/CAS）、两段式退款 | ★★★★☆ |
| [09-Elasticsearch搜索.md](09-Elasticsearch搜索.md) | must/filter、MQ驱动索引同步、数据一致性取舍 | ★★★☆☆ |
| [10-AI客服Agent.md](10-AI客服Agent.md) | LangChain4j、RAG、Function Calling、对话记忆隔离 | ★★★☆☆（差异化亮点） |
| [11-可观测性与调试实战.md](11-可观测性与调试实战.md) | SkyWalking链路追踪、traceId透传、一次真实bug排查 | ★★★☆☆（讲故事神器） |

## 项目架构总图（面试开场 30 秒画出来）

```
                         ┌──────────────────────────┐
  浏览器/App ──HTTP──►   │  mini-mall-gateway :9080  │
                         │  RequestLog(-150)         │
                         │  → Auth JWT鉴权(-100)     │
                         │  → Sentinel限流(0)        │
                         │  → 路由转发(lb://)        │
                         └─────────┬────────────────┘
                                   │ 服务发现: Nacos :8848
        ┌──────────┬──────────┬────┼──────┬──────────┬──────────┐
        ▼          ▼          ▼    ▼      ▼          ▼          ▼
      user      product    order  auth  payment    search      ai
      :9001     :9002      :9003  :9007 :9008      :9005      :9009
      优惠券     缓存/布隆   秒杀/购物车    支付宝    ES搜索    LangChain4j
        │          │          │           │          │          │
        └──────────┴────┬─────┴───────────┴──────────┴──────────┘
                        │ 中间件层
     MySQL  Redis(缓存/锁/秒杀/向量库)  RabbitMQ(延迟关单/秒杀/ES同步)
     Elasticsearch  MinIO(文件)  Seata(分布式事务)  SkyWalking(链路追踪)
```

## 面试叙事主线（自我介绍项目时按这条线讲）

1. **架构**：11 个模块（9 业务服务 + gateway + common 公共包），Nacos 注册发现，Gateway 统一入口做 JWT 鉴权 + 限流。
2. **流量入口**：网关三层过滤器（日志→鉴权→限流），身份用 `X-User-Id` header 透传，服务间 Feign 自动续传。
3. **高并发**：商品详情三级防护（布隆→缓存→互斥重建），秒杀 Redis Lua 原子扣减 + MQ 异步落库。
4. **一致性**：订单状态机全部用条件 UPDATE（CAS），跨服务用 Seata AT + MQ 补偿 + 定时对账，三种方案都有落地。
5. **真实支付**：接支付宝沙箱，回调做验签/幂等/金额校验/CAS 四道关，退款做两段式客服审批。
6. **亮点差异化**：AI 客服（RAG + Function Calling）、SkyWalking 全链路追踪、一次真实的网关限流 bug 排查（能讲 10 分钟的 debug 故事）。

## 各服务与端口速查

| 服务 | 端口 | 负责 |
|------|------|------|
| mini-mall-gateway | 9080 | 路由、JWT 鉴权、限流 |
| mini-mall-user | 9001 | 用户、地址、优惠券 |
| mini-mall-product | 9002 | 商品、分类、收藏、缓存防护 |
| mini-mall-order | 9003 | 订单、购物车、秒杀 |
| mini-mall-review | 9004 | 评价 |
| mini-mall-search | 9005 | Elasticsearch 搜索 |
| mini-mall-auth | 9007 | 登录注册、OAuth、邮箱验证码 |
| mini-mall-payment | 9008 | 支付宝支付/退款、对账 |
| mini-mall-file | - | MinIO 文件上传 |
| mini-mall-ai | 9009 | AI 客服 Agent |

基础设施：Nacos 8848 / Redis 6379 / RabbitMQ 5672(管理 15672) / MySQL 3306 / ES 9200 / MinIO 9010 / Sentinel 控制台 8858 / SkyWalking UI 18080
