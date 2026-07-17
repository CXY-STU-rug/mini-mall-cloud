# mini-mall-cloud

> Spring Cloud Alibaba 微服务电商项目 —— 从单体 [`mini-mall`](https://github.com/CXY-STU-rug/mini-mall) 重构而来,**7 个业务服务 + 1 网关 + 2 个 common 库**,完整覆盖电商核心闭环 + 支付退款 + AI 客服.

[![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/SpringBoot-3.3.5-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/SpringCloud-2023.0.3-6DB33F?logo=springboot)](https://spring.io/projects/spring-cloud)
[![SCA](https://img.shields.io/badge/SCA-2023.0.1.2-FF6A00?logo=alibabacloud)](https://github.com/alibaba/spring-cloud-alibaba)
[![License](https://img.shields.io/badge/License-MIT-blue)](#license)

---

## 📌 这是什么

一个**完整可跑、教学导向**的 Spring Cloud 微服务电商项目.覆盖了一个真实电商需要的核心业务模块和分布式基础设施.

**特点**:
- ✅ 业务闭环 100% (用户/商品/订单/库存/物流/评价/优惠券/秒杀/搜索/**支付/退款**)
- ✅ 分布式三件套全接入 (Nacos / Sentinel / Seata) + **SkyWalking 全链路追踪**
- ✅ 真实业务流程 (扣库存 / 用券 / 退券 / Cache Aside 评分回写 / Lua 秒杀 / **支付宝支付 + 对账 + 两阶段退款审批**)
- ✅ **AI 客服 Agent** (LangChain4j, 多轮对话记忆, 可查订单/退款)
- ✅ 详细中文笔记 (近 12000 行 markdown 学习记录, 每个里程碑都有原理 + 踩坑)

---

## 🏗️ 整体架构

```
                  ┌────────────────────────────────┐
                  │      前端 / curl / Postman      │
                  └───────────────┬────────────────┘
                                  ▼
                       ┌───────────────────┐
                       │  Gateway  :9080   │  统一入口
                       │  Path 路由 + JWT  │  全局过滤器鉴权
                       │  lb:// 负载均衡   │  RequestLogFilter 追踪
                       └─────────┬─────────┘
        ┌──────┬──────┬──────┬───┼───┬──────┬──────┐
        ▼      ▼      ▼      ▼   ▼   ▼      ▼      ▼
     ┌──────┐┌──────┐┌──────┐┌─────┐┌─────┐┌──────┐┌──────┐
     │ user ││produc││order ││revie││searc││ pay  ││  ai  │
     │:9001 ││:9002 ││:9003 ││:9004││:9005││:9008 ││:9009 │
     │登录  ││商品  ││购物车││评分 ││ ES  ││支付宝││客服  │
     │JWT券 ││库存  ││订单  ││回写 ││搜索 ││对账  ││Agent │
     │地址  ││收藏  ││秒杀锁││     ││     ││退款  ││LC4j  │
     └───┬──┘└───┬──┘└───┬──┘└──┬──┘└──┬──┘└───┬──┘└───┬──┘
         └───────┴───────┴──────┴──────┴───────┴───────┘
                                  │
              ┌───────────────────┼───────────────────┐
              ▼                   ▼                   ▼
       ┌─────────────┐    ┌──────────────┐    ┌──────────────┐
       │ Nacos :8848 │    │Sentinel:8858 │    │ Seata :8091  │
       │ 注册中心    │    │ 限流/降级    │    │ 分布式事务   │
       │ 配置中心    │    │ 规则持久化   │    │  AT 模式     │
       └─────────────┘    └──────────────┘    └──────────────┘

       ┌───────────┐ ┌───────────┐ ┌────────┐ ┌──────────┐ ┌────────────────┐
       │MySQL :3306│ │Redis :6379│ │ES :9200│ │MQ :5672  │ │SkyWalking:18080│
       │  业务库   │ │缓存/分布锁│ │ 搜索   │ │ 异步消息 │ │  全链路追踪    │
       └───────────┘ └───────────┘ └────────┘ └──────────┘ └────────────────┘
```

---

## 🎯 模块清单

### 业务服务 (7 个)

| 服务 | 端口 | 业务 | 核心特性 |
|---|---|---|---|
| **mini-mall-user** | 9001 | 用户/认证/地址/优惠券 | JWT 登录, 5 种券领用/抵扣/退还 |
| **mini-mall-product** | 9002 | 商品/分类/收藏/库存 | Cache Aside, internal 扣/回库存端点 |
| **mini-mall-order** | 9003 | 购物车/订单/秒杀/物流 | Seata 全局事务, **Redisson 分布式锁**, MQ 异步关单, Lua 秒杀, 7 天定时签收 |
| **mini-mall-review** | 9004 | 评价 + 商品评分回写 | afterCommit 跨服务回写, Feign 反向调用 |
| **mini-mall-search** | 9005 | ES 商品搜索 | BoolQuery (must/filter), 5 种排序, **MQ 异步增量同步** |
| **mini-mall-payment** | 9008 | 支付/退款/对账 | 支付宝沙箱支付, 定时对账补偿, **两阶段退款审批** (申请→客服审批) |
| **mini-mall-ai** | 9009 | AI 客服 Agent | LangChain4j, 多轮对话记忆, Feign 查订单/退款 |

### 基础设施 (1 + 2)

| 模块 | 用途 |
|---|---|
| **mini-mall-gateway** :9080 | Spring Cloud Gateway, JWT 全局鉴权 + RequestLogFilter |
| **mini-mall-common-core** | `Result` / `BusinessException` / `GlobalExceptionHandler` / `SecurityContextHolder` |
| **mini-mall-common-redis** | 统一 `RedisConfig` (JavaTimeModule) + `RedisService` 工具类 |
| (规划) common-security | 统一鉴权拦截器 (现各服务自行处理) |
| (规划) common-swagger | Knife4j 公共配置 |

---

## 🛠️ 技术栈

**核心框架**
- Spring Boot 3.3.5 + Spring Cloud 2023.0.3 + Spring Cloud Alibaba 2023.0.1.2
- MyBatis-Plus 3.5.7 (自动逻辑删除/分页/链式查询)
- JDK 21 (record / switch 表达式 / `var`)

**分布式中间件**
- Nacos 2.5.x — 服务注册 + 配置中心 (规则推送)
- Sentinel 1.8.x — 限流/熔断/降级 + 规则持久化到 Nacos
- Seata 2.0.0 — AT 模式分布式事务 (跨服务扣库存原子)
- OpenFeign + LoadBalancer — 跨服务 HTTP 调用 (lb://, Fallback 降级)

**存储 / 消息**
- MySQL 8.0 — 业务库
- Redis 7.x — 缓存 + Lua 秒杀脚本 + Redisson 分布式锁
- RabbitMQ 3.x — 异步消息 (订单超时关单 + 秒杀异步下单 + 评价通知 + 搜索增量同步 + 退款/对账事件)
- Elasticsearch 8.13.4 + Kibana — 商品搜索

**支付 / AI / 监控**
- 支付宝 SDK (沙箱环境) — 下单 / 异步回调验签 / 定时对账补偿
- LangChain4j — AI 客服 Agent (OpenAI 兼容接口调 DeepSeek + 本地 Ollama, 多轮对话记忆)
- SkyWalking 9.x — 全链路追踪 (Agent 探针 + OAP + UI :18080)

---

## 🚀 快速启动

### 前置条件

| 依赖 | 版本 | 必须? |
|---|---|---|
| JDK | 21+ | ✅ |
| Maven | 3.9+ | ✅ |
| MySQL | 8.0+ | ✅ |
| Redis | 6.0+ | ✅ |
| Docker | 任何 | ✅ (中间件用) |

### 1️⃣ 起中间件 Docker 容器

```bash
docker run -d --name minimall-nacos -p 8848:8848 -p 9848:9848 \
  -e MODE=standalone nacos/nacos-server:v2.5.0

docker run -d --name minimall-sentinel -p 8858:8858 \
  bladex/sentinel-dashboard:1.8.6

docker run -d --name seata-server -p 8091:8091 -p 7091:7091 \
  -e SEATA_IP=127.0.0.1 apache/seata-server:2.0.0

docker run -d --name mini-mall-rabbitmq -p 5672:5672 -p 15672:15672 \
  rabbitmq:3-management

docker run -d --name mini-mall-es -p 9200:9200 -p 9300:9300 \
  -e "discovery.type=single-node" -e "xpack.security.enabled=false" \
  docker.elastic.co/elasticsearch/elasticsearch:8.13.4

docker run -d --name mini-mall-kibana -p 5601:5601 \
  -e ELASTICSEARCH_HOSTS=http://host.docker.internal:9200 \
  -e I18N_LOCALE=zh-CN docker.elastic.co/kibana/kibana:8.13.4
```

### 2️⃣ 准备数据库

```bash
# 建库 + 跑全量 schema
mysql -uroot -p < sql/schema.sql

# Seata 元数据表 (4 张) + 业务库 undo_log
mysql -uroot -p < sql/seata.sql
```

### 3️⃣ 复制 example yml 加密码

```bash
cd mini-mall-cloud
for svc in user product order review search payment ai gateway; do
  cp mini-mall-$svc/src/main/resources/application.yml.example \
     mini-mall-$svc/src/main/resources/application.yml
done
# 然后编辑 application.yml 改 MySQL/Redis 密码
```

### 4️⃣ 编译

```bash
mvn clean install -DskipTests
```

### 5️⃣ 启动各服务(各开终端)

```bash
# 必须先后顺序: gateway 最后 (要等其他服务上线)
java -jar mini-mall-user/target/mini-mall-user-0.0.1-SNAPSHOT.jar
java -jar mini-mall-product/target/mini-mall-product-0.0.1-SNAPSHOT.jar
java -jar mini-mall-order/target/mini-mall-order-0.0.1-SNAPSHOT.jar
java -jar mini-mall-review/target/mini-mall-review-0.0.1-SNAPSHOT.jar
java -jar mini-mall-search/target/mini-mall-search-0.0.1-SNAPSHOT.jar
java -jar mini-mall-payment/target/mini-mall-payment-0.0.1-SNAPSHOT.jar
java -jar mini-mall-ai/target/mini-mall-ai-0.0.1-SNAPSHOT.jar
java -jar mini-mall-gateway/target/mini-mall-gateway-0.0.1-SNAPSHOT.jar
```

### 6️⃣ 验证

```bash
# Nacos 控制台
open http://127.0.0.1:8848/nacos       # 用户/密码: nacos/nacos
# 期望看到 7 服务 (user/product/order/review/search/payment/ai) + gateway 全部在线

# 通过网关访问
curl -X POST http://127.0.0.1:9080/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'
```

---

## 📋 API 一览 (通过网关 :9080)

> 🔐 = 需要 JWT (Header `Authorization: Bearer <token>`)

### 👤 用户 + 地址 + 优惠券 (`/user`, `/coupon`)

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/user/register` | 注册 |
| POST | `/user/login` | 登录返 JWT |
| GET | `/user/me` 🔐 | 当前用户信息 |
| GET/POST/PUT/DELETE | `/user/address/*` 🔐 | 地址 CRUD |
| POST | `/coupon` 🔐 | 创建券 (运营) |
| GET | `/coupon/available` | 可领券列表 |
| POST | `/coupon/{id}/receive` 🔐 | 领券 |
| GET | `/coupon/my` 🔐 | 我的券 |

### 🛍️ 商品 + 分类 + 收藏 (`/product`, `/category`, `/favorite`)

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/product/{id}` | 商品详情 (含评分) |
| GET | `/product/hot-search` | 热搜词 |
| POST | `/product/internal/deduct-stock` | 扣库存 (内部) |
| GET | `/category` | 分类列表 |
| POST | `/favorite/{productId}` 🔐 | 收藏 |
| GET | `/favorite/my` 🔐 | 我的收藏 |

### 🛒 购物车 + 订单 + 秒杀 (`/cart`, `/order`, `/seckill`)

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/cart` 🔐 | 加购物车 |
| GET | `/cart/my` 🔐 | 我的购物车 |
| POST | `/order` 🔐 | 创建订单 (扣库存 + 用券 / Seata 全局事务) |
| GET | `/order/{id}` 🔐 | 订单详情 (含物流) |
| POST | `/order/{id}/cancel` 🔐 | 取消订单 (回库存 + 退券) |
| POST | `/order/{id}/ship` 🔐 | 发货 (运营) |
| POST | `/order/{id}/sign` 🔐 | 签收 |
| POST | `/seckill/{id}` 🔐 | 秒杀 (Redis Lua 原子扣库存 + MQ 异步下单) |

### ⭐ 评价 (`/review`)

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/review` 🔐 | 创建评价 (回写商品评分) |
| GET | `/review/product/{id}` | 商品评价列表 |
| GET | `/review/my` 🔐 | 我的评价 |

### 🔍 搜索 (`/search`)

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/search/sync` | 全量同步 product → ES (运维, 初始化用) |
| GET | `/search/product` | 关键词 + 分类 + 价格区间 + 5 种排序 |
| DELETE | `/search/{productId}` | 单条删除 |

> 日常商品增删改由 product 服务发 MQ 消息, search 监听后**异步增量同步**到 ES, 无需手动调 `/search/sync`.

### 💰 支付 (`/pay`)

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/pay/create` 🔐 | 创建支付, 返回支付宝沙箱链接 |
| POST | `/pay/notify` | 支付宝异步回调 (验签, 支付宝服务器调, 无需 JWT) |
| GET | `/pay/status/{orderId}` 🔐 | 查询支付状态 |

### 💸 退款 (`/refund`, `/admin/refund`)

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/refund/apply` 🔐 | 用户申请退款 (第 1 阶段: 落申请单) |
| GET | `/admin/refund/list` 🔐👮 | 客服查待审批退款列表 |
| POST | `/admin/refund/approve/{refundId}` 🔐👮 | 客服批准 (第 2 阶段: 调支付宝原路退款) |
| POST | `/admin/refund/reject/{refundId}` 🔐👮 | 客服驳回 |

### 🤖 AI 客服 (`/ai`)

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/ai/chat` 🔐 | 多轮对话, Agent 可通过 Feign 查订单/退款状态回答 |

> 👮 = 需客服/管理员角色 (网关对 `/admin/**` 强制校验 `role=1`).

---

## 📚 学习笔记

| 文档 | 内容 |
|---|---|
| [`微服务E阶段_Nacos注册中心_笔记.md`](微服务E阶段_Nacos注册中心_笔记.md) | **主笔记 (~12000 行)**, 从 A 阶段 common 到 G9 ES 搜索全过程, 含 ASCII 架构图/踩坑速查/教学 API |
| [`G9-ES搜索模块笔记.docx`](G9-ES搜索模块笔记.docx) | G9 单独模块笔记 (基础知识点 + 代码 + 解释 三段式) |
| [`版本迭代知识点/`](版本迭代知识点/) | 早期 v2~v8 知识点迭代记录 |

**推荐阅读路径** (按里程碑顺序):

```
A 基础库 (common-core/common-redis)
  ↓
B user 服务 + JWT 登录
  ↓
C product 服务 + Feign 跨服务
  ↓
D Gateway + 全局鉴权
  ↓
E Nacos discovery (lb:// 负载均衡)
  ↓
F Nacos config + Sentinel 限流
  ↓
G 业务 9 大里程碑:
   G1 Redis → G2 RabbitMQ → G3 各业务搬迁 → G3.10 跨服务扣库存
   → G5 Seata 分布式事务 → G6 物流定时任务
   → G7 评价 (Cache Aside) → G8 优惠券 → G9 ES 搜索
  ↓
H Feign + Sentinel 降级 (统一 Fallback)
```

---

## 📁 项目结构

```
mini-mall-cloud/
├── pom.xml                          # 父 pom (依赖管理 + modules)
├── mini-mall-gateway/               # 9080 - 网关
├── mini-mall-user/                  # 9001 - 用户/认证/地址/券
├── mini-mall-product/               # 9002 - 商品/分类/收藏/库存
├── mini-mall-order/                 # 9003 - 购物车/订单/秒杀/物流
├── mini-mall-review/                # 9004 - 评价
├── mini-mall-search/                # 9005 - ES 搜索 (G9 新增)
├── mini-mall-payment/               # 9008 - 支付/退款/对账 (支付宝沙箱)
├── mini-mall-ai/                    # 9009 - AI 客服 Agent (LangChain4j)
├── mini-mall-common/
│   ├── mini-mall-common-core/       # Result/Exception/Context
│   └── mini-mall-common-redis/      # RedisConfig + RedisService (G9 抽取)
├── sql/
│   ├── schema.sql                   # 业务库 DDL (全量)
│   ├── seata.sql                    # Seata 元数据 4 表 + undo_log
│   └── g*.sql                       # 各里程碑增量
└── 微服务E阶段_Nacos注册中心_笔记.md  # 主学习笔记
```

---

## ✅ 已完成 / 🟡 规划中

**已完成 (100% 业务闭环)**
- ✅ A 基础库 (common-core + common-redis)
- ✅ B 用户服务 + JWT
- ✅ C product + Feign 跨服务
- ✅ D Gateway + 全局鉴权 + 请求日志
- ✅ E Nacos 服务发现 + lb:// 负载均衡
- ✅ F Nacos 配置中心 + Sentinel 限流 (规则推 Nacos)
- ✅ G1~G9 业务模块 (购物车/订单/秒杀/评价/优惠券/搜索...)
- ✅ H Feign + Fallback 降级
- ✅ 支付宝支付 + 定时对账 + 两阶段退款审批 (order 加 Redisson 分布式锁)
- ✅ AI 客服 Agent (LangChain4j, 可查订单/退款)
- ✅ SkyWalking 全链路追踪 (UI :18080)

**规划中 (锦上添花)**
- 🟡 common-security: 抽 JWT 解析/Header 拦截到 common
- 🟡 common-swagger: Knife4j 统一 API 文档
- 🟡 G10: 后台管理 (运营端 CRUD)
- 🟡 CI/CD (GitHub Actions)

---

## 🤝 贡献 / 反馈

issue / PR 欢迎.如发现笔记里有错或不清楚的地方,直接开 issue 讨论.

## License

MIT
