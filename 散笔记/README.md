# 散笔记 · 知识点分类总索引

> 覆盖 44 份 docx + 4 份 md（已排除备份文件）。文件已按**知识域**重排进 16 个编号文件夹（`00-` ~ `15-`，编号即建议阅读顺序）。
> 同一份笔记若跨域，物理上只放**主家**一个文件夹，其余知识域在下面表格里照常引用它——找东西认准文件名即可。
> 找东西的顺序：先看第 0 节「按问题反查」→ 再进对应编号文件夹。

---

## 目录地图（编号 = 阅读顺序）

| 文件夹 | 装了什么 |
|---|---|
| `00-面试/` | 面试汇报逐字稿 + 八股追问链 |
| `01-Java语言与设计模式/` | Java 集合、Stream；工厂/策略/责任链设计模式 |
| `02-JVM/` | 内存区、对象一生、GC、类加载、排障工具 |
| `03-并发与锁/` | 并发三问、synchronized/Lock/CAS、JMM/volatile、线程池、CompletableFuture |
| `04-Redis/` | 五大类型+编码、Redisson 锁源码、持久化、内存与高可用、缓存三大问题、Lua |
| `05-数据库与分布式事务/` | **MySQL 索引与 SQL 调优**、**MyBatis 原理**、Seata AT + MVCC/redo/undo、数据一致性五手段、接口幂等 |
| `06-消息队列/` | RabbitMQ、Kafka |
| `07-搜索ES/` | 倒排索引、DSL、8.x 客户端源码 |
| `08-SpringBoot与HTTP/` | 🧱**【Spring Boot 层·单个服务内部怎么造】** Spring 三大件(IoC/AOP/事务/MVC/Boot)、HTTP 报文、拦截器洋葱、RestTemplate、Swagger |
| `09-SpringCloud/` | 🏙**【Spring Cloud 层·多个服务之间怎么协作】** Nacos 注册配置、Feign、Gateway、Sentinel 限流、common-security 抽取 |
| `10-安全鉴权/` | Spring Security 三层改造、网关统一错误返回、越权专题、上线安全整改、强制失效 |
| `11-支付与退款/` | 支付宝全流程、主动查询兜底、退款两段式审批、全量代码 |
| `12-存储与文件上传/` | MinIO 对象存储、MultipartFile 原理 |
| `13-性能压测/` | 压测方法论剧本、秒杀瓶颈优化实录 |
| `14-部署运维/` | Git、Docker、Linux、本项目上线部署方案 |
| `15-AI/` | AI Agent 五大组件、推理范式、Function Calling/MCP、RAG |

---

## 08 vs 09：Spring Boot 还是 微服务，一句话分清

两个文件夹都跟 Spring 有关，容易混。**按“层次”分**，不是随便分：

```
08-SpringBoot与HTTP  = Spring Boot 层  ── 单个服务【内部】怎么造出来
                   IoC / AOP / 事务 / SpringMVC / 自动配置 / HTTP / MyBatis
                   ★ 单体应用也要这些，和“微服务”无关 ★
                        ↑ 建立在它之上
09-SpringCloud    = Spring Cloud 层 ── 多个服务【之间】怎么协作
                   Nacos 注册/配置 · Feign 调用 · Gateway 网关 · Sentinel 限流 · Seata 事务
                   ★ 只有把系统拆成微服务才需要 ★
```

**比喻**：08 是“盖房子的砖”（Spring Boot），09 是“小区物业管理”（Spring Cloud）。
**面试也这么分**：“讲讲 Spring 事务 / IoC / 循环依赖” 是 08 类；“Nacos 和 Eureka 区别 / 服务雪崩怎么解决” 是 09 类。

> ⚠️ **唯一的例外**：`09-SpringCloud/微服务基础知识点阶段v8---32.docx` 是一份“什么都塞”的大文档，横跨两层，别被它误导——
> - 它的 **23~29 章（IoC/AOP/自动装配/动态代理）、21.3/21.4（SpringMVC/WebFlux）、30/31 章（ThreadLocal/JWT）其实是 08 的 Spring Boot 内容**，只是当初写在了这份大文档里；
> - 它的 **一~二十一章（Maven 多模块/Gateway）、E阶段 md 的 Nacos/Sentinel 才是 09 的微服务治理内容**。
>
> 想系统学 Spring 本身 → 优先看 `08-SpringBoot与HTTP/Spring三大件_面试专题.docx`（已抽干净）；这份 v8 当“项目搭建流水账”看即可。

---

## 0. 按问题反查（想查什么 → 去哪）

| 我想查 | 去哪份 · 哪一节 |
|---|---|
| HashMap 底层 / Stream 收集器 | `01-Java语言与设计模式/Java集合.docx` 6.1、8.2 |
| OOM 了怎么排查 | `02-JVM/JVM专题_内存对象类加载GC排障.docx` 7.5、7.6 |
| volatile 为什么能保证可见性 | `03-并发与锁/并发编程专题_线程与锁.docx` 7.5~7.8 |
| synchronized 锁升级 / MarkWord / AQS / ReentrantLock | `03-并发与锁/synchronized锁升级与AQS_面试专题.docx` |
| 线程池七参数 / 拒绝策略 | `03-并发与锁/线程池专题_ThreadPoolExecutor深入解析.docx` 三、五 |
| 三次握手四次挥手 / TIME_WAIT / 拥塞控制 / 粘包 | `08-SpringBoot与HTTP/TCP与计算机网络_面试专题.docx` |
| MVCC / ReadView / 幻读 | `05-数据库与分布式事务/Seata_AT模式分布式事务深挖.docx` 0.11 |
| 慢查询定位 / 索引失效 / 回表覆盖 / B+树 | `05-数据库与分布式事务/MySQL索引与SQL调优_面试专题.docx` |
| Spring 事务失效 / 循环依赖三级缓存 / SpringBoot 自动配置 | `08-SpringBoot与HTTP/Spring三大件_IoC-AOP-事务-MVC-Boot_面试专题.docx` |
| MyBatis 一二级缓存 / 延迟加载 / 执行流程 | `05-数据库与分布式事务/MyBatis执行流程与缓存_面试专题.docx` |
| 缓存穿透击穿雪崩 | `04-Redis/Redis缓存三大问题与击穿锁实战.docx` |
| 分布式锁看门狗源码 | `04-Redis/Redisson分布式锁源码剖析.docx` 四 |
| 秒杀怎么防超卖 | `04-Redis/Redis Lua脚本专题_原子性利器.docx` 四 + `13-性能压测/秒杀瓶颈优化-改造实录.docx` |
| 消息丢了怎么办 / 延迟队列 | `06-消息队列/RabbitMQ基础与实战.docx` 六、八 |
| 接口重复提交怎么防 | `05-数据库与分布式事务/接口幂等性专题_三层防线与CAS状态机.docx` |
| 越权漏洞怎么防 | `10-安全鉴权/第80章_C端商城+越权安全专题.docx` 80.6 |
| 网关 401/403/429 返回体不统一 | `10-安全鉴权/网关错误返回和其他链错误返回和全局错误返回统一体.docx` |
| 支付回调四道防线 | `11-支付与退款/支付宝支付服务_全流程详解.docx` 3.4 |
| 限流算法 / Sentinel 规则 | `09-SpringCloud/SpringGateway与Sentinel限流全解.docx` 二、五 |
| ES 查询 DSL 怎么拼 | `07-搜索ES/G9-自主ES搜索模块笔记.docx` 13、18 |
| 上线部署 / Docker 网络坑 | `14-部署运维/` 三份 |
| 面试怎么开口讲项目 | `00-面试/mini-mall项目面试报告_汇报逐字稿与八股追问链.docx` |

---

## 一、Java 语言基础

| 文件 | 核心知识点 |
|---|---|
| `01-Java语言与设计模式/Java集合.docx` | 四种底层结构对比；ArrayList/LinkedList；Set 去重原理（hashCode/equals）；HashMap 数组+链表+红黑树；Collections 工具类；**Stream + Collectors 下游收集器**（重点） |
| `08-SpringBoot与HTTP/Chapter78_OAUTH登录mvc和resttemplate.docx` 一~二 | 反射 API；反射与序列化的关系；Jackson / MyBatis / Serializable **三套序列化对比** |
| `09-SpringCloud/微服务基础知识点阶段v8---32.docx` 十六 | Java 异常体系：受检 vs 非受检、为什么 BusinessException 继承 RuntimeException、@ExceptionHandler 匹配优先级 |
| `03-并发与锁/线程池专题_ThreadPoolExecutor深入解析.docx` 二十 | Lambda / 匿名内部类 / 方法引用四种写法；捕获变量必须 effectively final |

## 二、JVM

| 文件 | 核心知识点 |
|---|---|
| `02-JVM/JVM专题_内存对象类加载GC排障.docx` | **运行时数据区**（两类五区、各区抛什么错）；**对象一生**（指针碰撞/TLAB/对象头/逃逸分析/DCL 半成品）；**GC**（弱分代假说、可达性分析、四种引用、三色标记、多标漏标、安全点）；**收集器演进** Serial→CMS→G1→ZGC；**类加载**五步 + 双亲委派三次打破；**排障工具** jps/jstat/jmap/jstack/Arthas + OOM/CPU100% 排查 SOP |
| `09-SpringCloud/微服务基础知识点阶段v8---32.docx` 26 | ClassLoader、NoClassDefFoundError、fat jar 解剖 |

## 三、并发编程

| 文件 | 核心知识点 |
|---|---|
| `03-并发与锁/并发编程专题_线程与锁.docx` | 并发三大问题；Runnable vs Callable；Future/FutureTask；CountDownLatch；**synchronized / ReentrantLock / CAS 三把武器选型**；超卖案例；**JMM、volatile 两保证一不保证、happens-before、指令重排、DCL 单例** |
| `03-并发与锁/synchronized锁升级与AQS_面试专题.docx` | **synchronized 底层 Monitor**；**对象头 MarkWord**；**锁升级** 无锁→偏向→轻量级→重量级；**CAS**（Unsafe/自旋/ABA/乐观悲观）；**AQS**（volatile state + FIFO 双向队列 + 公平非公平）；**ReentrantLock**（CAS+AQS/重入/公平参数）；synchronized vs Lock 对比 |
| `03-并发与锁/线程池专题_ThreadPoolExecutor深入解析.docx` | 七大核心参数；任务提交执行流程；四种拒绝策略；队列选择；Executors 隐患；5 个生命周期状态；线程数怎么设；优雅关闭；**CompletableFuture runAsync/supplyAsync/allOf** |
| `09-SpringCloud/微服务基础知识点阶段v8---32.docx` 30 | ThreadLocal 内存模型、ThreadLocalMap 弱引用 key、线程池下数据丢失、**TTL 跨线程拷贝原理** |

## 四、数据库 · 事务 · ORM

| 文件 | 核心知识点 |
|---|---|
| `05-数据库与分布式事务/MySQL索引与SQL调优_面试专题.docx` | **慢查询定位**（慢日志/explain 四字段）；**B+树 vs B树**；聚簇/非聚簇/**回表/覆盖索引**；**最左前缀 + 索引失效四场景**；索引创建原则；SQL 优化经验；超大分页；ACID/隔离级别/**MVCC 版本链+ReadView**；主从复制；分库分表垂直vs水平 |
| `05-数据库与分布式事务/MyBatis执行流程与缓存_面试专题.docx` | **执行流程**（SqlSessionFactory→SqlSession→Executor→MappedStatement）；Mapper 动态代理；**延迟加载 + CGLIB 原理**；**一级(Session)/二级(Namespace)缓存**、何时清空 |
| `05-数据库与分布式事务/本项目实战_MySQL索引落地.docx` 🟢项目落地 | 真实索引清单（idx_user_id/uk_username…）；**唯一索引=幂等兜底**；回表/覆盖在项目怎么体现；MP 分页插件；隔离级别默认 RR；**分库分表垂直有/水平没做（如实答）**；SkyWalking 定位慢查询 |
| `05-数据库与分布式事务/本项目实战_MyBatis-Plus落地.docx` 🟢项目落地 | 用 MP 非原生（怎么讲圆）；BaseMapper/IService 白嫖 CRUD；**@TableLogic 逻辑删除**；PaginationInnerInterceptor；**二级缓存不用改走 Redis（取舍理由）** |
| `05-数据库与分布式事务/Seata_AT模式分布式事务深挖.docx` 第 0 章 | ACID 逐拆；@Transactional 本质；刚性 vs 柔性；**隔离级别四级**；**redo/undo 两本账**（三层写入链路、三档刷盘、redo+binlog 两阶段提交）；**MVCC**（版本链三隐藏列、ReadView 四条可见性规则、快照读 vs 当前读、RC/RR 差异、幻读为何 MVCC 治不了） |
| `09-SpringCloud/微服务基础知识点阶段v8---32.docx` 十八 | MyBatis-Plus：实体注解、Mapper 无实现类原理、selectById 流程、逻辑删除 SQL 重写 |
| `09-SpringCloud/微服务E阶段后_32-笔记.md` 第 40 章 | MP 在微服务里的角色分工、分页插件何时补、自动填充 |
| `10-安全鉴权/第80章_C端商城+越权安全专题.docx` 80.4/80.5 | MP 条件分页；**MP null 不更新导致地址默认互斥 bug** + 三层修复 |

## 五、Redis 与缓存

| 文件 | 核心知识点 |
|---|---|
| `04-Redis/Redisson分布式锁源码剖析.docx` | **五大数据类型 + 底层编码**；Redisson lock() 全链路 Lua、可重入原理、**看门狗续期**、unlock、订阅+信号量等锁、tryLock 对比 |
| `04-Redis/Redis持久化知识点.docx` | RDB 快照 + fork/COW；AOF 三种刷盘 + 重写；混合持久化；选型 |
| `04-Redis/Redis内存管理与高可用.docx` | 惰性/定期删除；**八种淘汰策略**；LRU 缺陷与 LFU（概率递增+时间衰减）；**主从 → 哨兵 → 集群**三级递进、16384 槽、MOVED/ASK、hash tag |
| `04-Redis/Redis缓存三大问题与击穿锁实战.docx` | SET NX EX 为什么必须一条命令；击穿互斥锁两种写法（SETNX 手写 / Redisson）；**空值缓存 vs 布隆过滤器** |
| `04-Redis/Redis Lua脚本专题_原子性利器.docx` | Lua 的原子性层面；EVAL/EVALSHA；**KEYS 与 ARGV 为什么必须分开**；秒杀预扣库存脚本逐行；解锁为何必须 Lua |
| `09-SpringCloud/微服务E阶段后_32-笔记.md` 第 42/43 章 | RedisConfig 序列化器；Redis Set 做收藏 |

## 六、分布式一致性 · 幂等

| 文件 | 核心知识点 |
|---|---|
| `05-数据库与分布式事务/Seata_AT模式分布式事务深挖.docx` 1~8 章 | AT 一阶段偷偷做的 6 件事；undo_log 表解剖；二阶段 commit/rollback；**全局锁与脏写惨案**；项目落地全链路；TransactionTemplate 源码；**afterCommit 钩子**（跨服务调用的正确时机）；实测抓拍两阶段 |
| `05-数据库与分布式事务/数据一致性专题_五种手段.docx` | 本地事务 → CAS 条件更新 → Seata → MQ 最终一致 → Cache Aside；**缓存一致性三步演进**：延迟双删 → MQ 异步删 → Canal 读 binlog；结尾选型决策表 |
| `05-数据库与分布式事务/接口幂等性专题_三层防线与CAS状态机.docx` | 防重 ≠ 幂等；建单幂等三层防线（Redis 前置查 / 分布式锁 / **唯一索引兜底**）；改状态用 CAS 条件更新 |
| `03-并发与锁/_原锁与并发目录导航_已过时.md` | ⚠️ 老「锁与并发」目录的导航图，该目录已拆进 03/04/05，此文件仅留档，路径已失效 |

## 七、消息队列

| 文件 | 核心知识点 |
|---|---|
| `06-消息队列/RabbitMQ基础与实战.docx` | 解耦/异步/削峰；AMQP + Channel；**四种交换机**；可靠性三环节（Confirm / 持久化 / 手动 ACK）；**DLX + TTL 延迟队列**；重复消费五种幂等方案；消息堆积泄洪三步；**8 个面试速记**（含幽灵消息） |
| `06-消息队列/Kafka基础与实战.docx` | 分区/副本/**ISR·HW·LEO**；ack 机制；幂等与事务；消费者组与 offset、Rebalance；为什么快；**RabbitMQ vs Kafka 选型** + 本项目迁移思考（延迟关单换不动） |
| `09-SpringCloud/微服务E阶段后_32-笔记.md` Ch47~51、54 | 项目真实 MQ 基建三组队列、TTL+DLX 关单、消费者幂等、三个踩坑 |

## 八、搜索引擎 ES

| 文件 | 核心知识点 |
|---|---|
| `07-搜索ES/G9-自主ES搜索模块笔记.docx` | **倒排索引**；分词器（中文）；**text vs keyword**；索引映射 ProductDocument；Feign 灌数据；Repository；**search 方法**（bool/multi_match/term/range 组合）；**8.x 官方客户端源码剖析**（BoolQuery.Builder / NativeQuery / SearchHits）；踩坑记录 |
| `09-SpringCloud/微服务E阶段后_32-笔记.md` 第 75 章 | 同一模块的建设流水账版（与上面重叠，看一份即可） |

## 九、Spring 框架原理

| 文件 | 核心知识点 |
|---|---|
| `08-SpringBoot与HTTP/Spring三大件_IoC-AOP-事务-MVC-Boot_面试专题.docx` | **单例 Bean 线程安全**；**Bean 生命周期**九步（AOP 代理在后置处理器生成）；**循环依赖三级缓存**流程 + 构造循环依赖用 @Lazy；**AOP**（动态代理、项目记日志）；**声明式事务实现 + 事务失效三场景**（吞异常/检查异常没配 rollbackFor/非 public）；**SpringMVC 执行流程九步**（DispatcherServlet）；**SpringBoot 自动配置原理**（@EnableAutoConfiguration+@Import+条件注解）；常见注解速查 |
| `08-SpringBoot与HTTP/本项目实战_Spring落地.docx` 🟢项目落地 | AOP=全局异常 @RestControllerAdvice（没写业务切面，如实）；**事务：统一 rollbackFor=Exception**；**订单用 TransactionTemplate**（提交后发 MQ + 锁在事务外）；**自动配置：common-security 自研 starter**（imports+@AutoConfiguration+条件注解，踩过 @Import NoClassDefFoundError 坑）；循环依赖没踩过 |
| `09-SpringCloud/微服务基础知识点阶段v8---32.docx` 23~29 | **IoC** 容器与 Bean 生命周期；**AOP + 代理**（@ExceptionHandler 怎么无侵入生效）；**自动装配三件套**；**动态代理统一论**（MyBatis Mapper / Feign / @Transactional 是同一招） |
| `09-SpringCloud/微服务基础知识点阶段v8---32.docx` 21.3 / 21.4 | Spring MVC 三角色；**WebFlux + Reactor**（Mono/Flux、异步非阻塞、Netty vs Tomcat） |
| `08-SpringBoot与HTTP/Chapter78_OAUTH登录mvc和resttemplate.docx` 三 | **请求进来后端如何承接**：Tomcat Coyote/Catalina、RequestFacade 门面、**body 是懒读的**、InputStream 一次性坑、Filter/Interceptor/Controller 三层、ArgumentResolver、HttpMessageConverter、字符编码坑 |
| `08-SpringBoot与HTTP/Swagger接口文档引入完整步骤.md` | Knife4j 公共模块抽取、@ComponentScan 扫描范围、网关聚合文档、放行文档资源 |

## 十、HTTP · 网络 · 出站调用

| 文件 | 核心知识点 |
|---|---|
| `08-SpringBoot与HTTP/TCP与计算机网络_面试专题.docx` | 四层模型/封装；**TCP vs UDP**；**三次握手**（为什么不是两次）；**四次挥手 + TIME_WAIT 2MSL**；可靠传输（序号/确认/重传/**滑动窗口**/流量控制）；**拥塞控制**（慢启动/拥塞避免/快重传快恢复）；**粘包拆包**（字节流无边界，长度字段解）；和 HTTP/Netty/部署的关系 |
| `08-SpringBoot与HTTP/HTTP.docx` | URL 结构；请求/响应报文四段；方法与状态码；常见头；Content-Type；Cookie/Session/Token；HTTP 版本演进；HTTPS=HTTP+TLS；**HandlerInterceptor 三方法 + 洋葱模型**；Filter vs Interceptor |
| `08-SpringBoot与HTTP/Chapter78_OAUTH登录mvc和resttemplate.docx` 后半 | **RestTemplate 分层**；Spring HttpEntity vs Apache HttpEntity；HttpClient 连接池配置对象；getForObject/postForObject/exchange；Form 表单 MultiValueMap；Content-Type vs Accept；OAuth 换 token 实战；**JsonNode get() vs path() 陷阱**；**入站 vs 出站镜像对照** |
| `09-SpringCloud/微服务基础知识点阶段v8---32.docx` 27/28 | URI 五段；Tomcat BIO 线程模型 vs Netty 事件循环 |

## 十一、微服务治理（SCA 全家桶）

| 文件 | 核心知识点 |
|---|---|
| `09-SpringCloud/微服务基础知识点阶段v8---32.docx` 一~二十一 | **版本矩阵**；Maven 多模块四层继承、**dependencyManagement vs dependencies / BOM**、scope 与 optional；jakarta 迁移；bootstrap starter、AutoConfiguration.imports；**统一响应 Result\<T\>**；全局异常处理；**SecurityContextHolder + TTL 用户透传三件套**；OpenFeign（动态代理、@FeignClient 参数、契约镜像）；**Gateway D 阶段全流程 + YAML 四大死规则 + 三大踩坑** |
| `09-SpringCloud/微服务E阶段后_32-笔记.md` 32~34 章 | **Nacos 注册中心**（lb:// 改造、Feign 去 url 硬编码）；**Nacos 配置中心**（bootstrap.yml、@RefreshScope 动态刷新、配置优先级）；**Sentinel**（4 核心概念、Slot 源码级、@SentinelResource、熔断闭环、网关流控、系统保护、**规则持久化到 Nacos**） |
| `09-SpringCloud/SpringGateway与Sentinel限流全解.docx` | 雪崩与四板斧；**四大限流算法手写**（固定窗口/滑动窗口/漏桶/令牌桶）；Gateway 原生 RequestRateLimiter 三参数 + Redis Lua 原理 + 局限；**Sentinel 七大规则**；GatewayFlowRule；自定义 429；为什么最终选 Sentinel |
| `09-SpringCloud/微服务E阶段后_32-笔记.md` 64 章 | Feign Fallback 降级、feign.sentinel.enabled 开关 |
| `09-SpringCloud/总笔记里面77自主common-security_抽取.docx` | common-security 四大组件（JwtUtil / SecurityContextHolder / HeaderInterceptor 进站 / FeignAuthInterceptor 出站）；**@ConditionalOnClass + @Import 不能混用**、provided scope 不传递两大坑 |

## 十二、安全与鉴权

| 文件 | 核心知识点 |
|---|---|
| `10-安全鉴权/过程和知识点.docx` | **认证 vs 授权**；Servlet 版 vs 响应式版；**三层安全改造全流程**：auth 服务（UserDetails/AuthenticationManager/OAuth2 GitHub）→ 网关（**前缀树白名单 PathTrie**、JwtConverter、ReactiveAuthenticationManager、**RBAC 授权 Manager**、身份透传）→ 业务服务（HeaderAuthenticationFilter 纵深防御）；附通用引入流程 |
| `10-安全鉴权/网关错误返回和其他链错误返回和全局错误返回统一体.docx` | 前置：Reactor / ServerHttpResponse / HttpMessageWriter / Gateway 过滤器顺序 / ErrorWebExceptionHandler；**filter 铁律：放行=调 chain，短路=自己写 response**；五个出口（安全链 401/403、限流 429、全局兜底 404/503、业务链）统一成 Result\<T\>；**裸 200 空响应坑** |
| `10-安全鉴权/第80章_C端商城+越权安全专题.docx` | RBAC 角色体系；**垂直越权 vs 水平越权**；鉴权链路两道关；网关白名单 method+path 双维度；**前缀锁定的坑**；归属校验模板；消除攻击面 > 加固攻击面 |
| `10-安全鉴权/第81章_SEC-2上线前安全整改.docx` | **默认拒绝 + internal 黑名单**；文件上传**魔数验真**；订单状态原子更新；上下架过滤 + ES 联动；禁用账号双路拦截 |
| `10-安全鉴权/第83章_服务端强制失效_登出与禁用即时生效.docx` | JWT 为什么难注销；**token 黑名单**；禁用即时生效；网关统一校验 |
| `09-SpringCloud/微服务基础知识点阶段v8---32.docx` 31 | **JWT 三段式 + HMAC-SHA256 验签**；JJWT 0.12+ API；**BCrypt 加盐慢哈希**（为什么不用 MD5） |
| `11-支付与退款/退款审批改造_两段式状态机与越权防护.docx` 知识点4 | 网关鉴权分层导致的**真实越权漏洞**（/admin/refund vs /refund/admin） |

## 十三、业务模块实战（项目落地）

| 主题 | 文件 |
|---|---|
| **支付** | `11-支付与退款/支付宝支付服务_全流程详解.docx`（三条主线、**回调四道防线**、按 channel 分入口、状态机）<br>`11-支付与退款/支付主动查询兜底改造说明_alipay_trade_query.docx`（push vs pull、applyPaid 复用）<br>`11-支付与退款/第82章_支付宝支付服务完整实现.docx`（逐文件全量源码） |
| **退款** | `11-支付与退款/退款服务_全流程详解.docx`（两段式审批、三端点两角色）<br>`11-支付与退款/退款审批改造_两段式状态机与越权防护.docx`（CAS 状态机、钱货一致性）<br>`11-支付与退款/退款审批改造_全量代码.docx`（11 处改动全代码） |
| **秒杀** | `13-性能压测/秒杀瓶颈优化-改造实录.docx`（地址快照下沉、入口零 DB 预热）<br>`09-SpringCloud/微服务E阶段后_32-笔记.md` Ch57~61（Lua 抢资格 + MQ 异步落库 + 三态轮询） |
| **订单** | `09-SpringCloud/微服务E阶段后_32-笔记.md` Ch52~56（createOrder 9 步、锁为什么在事务外、MQ 为什么在提交后发、状态机 5 态 3 路径） |
| **物流/签收** | `09-SpringCloud/微服务E阶段后_32-笔记.md` 67~72（**FSM 有限状态机**、@Scheduled、Seata AOP 包异常坑） |
| **优惠券/评价/购物车/收藏** | `09-SpringCloud/微服务E阶段后_32-笔记.md` 43、44、73、74 章 |
| **文件上传** | `12-存储与文件上传/MinIO对象存储_完整学习笔记.docx`（bucket/object/key、**MultipartFile 内部原理**、multipart 协议、8KB 中转不占堆） |
| **C 端商城骨架** | `10-安全鉴权/第80章_C端商城+越权安全专题.docx`（DTO/VO 分层、@Valid 校验、字段白名单落库） |

## 十四、设计模式与工程思想

| 文件 | 核心知识点 |
|---|---|
| `01-Java语言与设计模式/设计模式扫盲_工厂_策略_责任链.docx` | 简单工厂/工厂方法/抽象工厂；策略模式解 if-else 膨胀；**策略+工厂黄金搭档**；责任链纯链 vs 不纯链；**三者辨析**（工厂 vs 策略、策略 vs 责任链）；JDK/框架里的实例 |
| `09-SpringCloud/微服务E阶段后_32-笔记.md` 68 章 | FSM 有限状态机的工程实现（4 步前置校验） |
| `09-SpringCloud/微服务E阶段后_32-笔记.md` 70 章 | **MQ 延迟队列 vs @Scheduled 选型法则**（30 分钟关单 vs 7 天签收） |

## 十五、工程化 · 运维 · 部署

| 文件 | 核心知识点 |
|---|---|
| `14-部署运维/Git实战操作详解.docx` | 心智模型；status/add/commit 主循环；diff/log；**.gitignore 规则 + 已跟踪文件加 ignore 无效的大坑**；**敏感信息三件套**（忽略+模板+环境变量）；restore/reset/revert/reflog 后悔药 |
| `14-部署运维/Docker基础_镜像容器compose.docx` | 镜像/容器/仓库；**分层存储**；docker run 参数；数据卷；**容器网络（最大的坑）**；Dockerfile 指令 + 三对易混 + 多阶段构建；compose |
| `14-部署运维/Linux基础_命令权限进程排查.docx` | 文件树；ls -l 逐字段；**rwx 权限与数字表示**；日志与管道；进程与端口；nohup vs systemd；**两道防火墙墙**；打包传输；排查三板斧 |
| `14-部署运维/本项目上线部署方案_单机Composeall容器化.docx` | 目标架构 + Nginx 反代；为什么不上 K8s；**localhost → 容器名（最容易翻车）**；启动顺序；上线 Checklist；**密钥绝不打进镜像** |
| `08-SpringBoot与HTTP/Swagger接口文档引入完整步骤.md` | 全套依赖 + 从零引入五步 + 网关聚合 + 常见坑 |

## 十六、性能与压测

| 文件 | 核心知识点 |
|---|---|
| `13-性能压测/压测面试剧本.md` | 压测方法论（怎么压、数据怎么造）；商品详情 10000 QPS 的来源；ES vs MySQL 27 倍；秒杀零超卖怎么证明；**压出来的真 Bug：ES 全量同步 55MB bulk 被 429 拒收** |
| `13-性能压测/秒杀瓶颈优化-改造实录.docx` | 从压测数据长出的两层优化 + 三轮复测数据 |

## 十七、AI

| 文件 | 核心知识点 |
|---|---|
| `15-AI/主流AI_Agent开发扫盲.docx` | 五大组件（LLM/Planning/Tools/Memory/Action）；**推理范式 CoT / ReAct / Plan-and-Execute / Reflexion**；记忆机制与两个必踩问题；**Function Calling 与 MCP**；RAG；框架对比；多智能体 |

## 十八、面试专用

| 文件 | 核心知识点 |
|---|---|
| `00-面试/mini-mall项目面试报告_汇报逐字稿与八股追问链.docx` | 汇报五条铁律；一句话/30 秒/2 分钟三档开场白；**九大主题追问链**（秒杀、分布式锁与事务边界、缓存、MQ、Seata、鉴权、微服务治理、支付退款、AI 客服）；灵魂拷问题；**真实数字弹药库**；考前一页速记 |
| `13-性能压测/压测面试剧本.md` | 压测数字的追问链 |

---

## 附：本次重排说明（2026-08-19）

原来根目录散着 6 份、`锁与并发/` 一个目录塞了 11 份（Redis 全家桶 + Seata + 一致性 + 幂等 + 线程池，名不副实），文件夹名也乱。已按上面的**目录地图**全量重排：

1. **`锁与并发/` 拆三家**：并发/线程池 → `03-并发与锁/`；Redis 五份 → `04-Redis/`；Seata/一致性/幂等 → `05-数据库与分布式事务/`。
2. **根目录 6 份散文件各回各家**：Java 集合、设计模式 → 01；MinIO → 12；第 80/81/83 章 → 10；第 82 章 → 11；common-security 抽取 → 09。
3. **备份统一进各文件夹的 `_backup/`**：面试 1 份、springsecurity 3 份、部署 3 份。
4. **删掉两个空壳目录**：`mybatis和mybatis-plus/`、`网关错误返回…统一体/`（内容本来就在 springsecurity 下）。
5. **老「锁与并发」目录的 README** 已改名 `03-并发与锁/_原锁与并发目录导航_已过时.md` 留档，内容路径已失效，本索引取而代之。

**仍待人工处理（未动）**：
- **内容重复三对**：第82章 ↔ `11-支付与退款/支付宝支付服务_全流程详解.docx`；`07-搜索ES/G9` ↔ `09-SpringCloud/微服务E阶段后_32-笔记.md` 第75章；`09-SpringCloud/总笔记里面77…` ↔ E阶段md 第77章。想留一份主、一份存档的话告诉我。
- **`05-数据库与分布式事务/Seata_AT模式…docx` 第 0.11 节样式错乱**：约 84 段被误设成 Heading 2，Word 导航窗格会被撑爆，需要批量改回正文样式。
