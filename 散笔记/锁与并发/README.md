# 锁与并发 · 笔记导航

> 本目录 5 份 docx 覆盖：单机并发 → 分布式锁 → 锁的实战应用 → 锁所依赖的 Redis 本身。
> 所有实验代码都在本项目里，亲手跑过、复现过 bug（文件位置见文末）。

---

## 一、5 份文件是什么、什么关系

**两条线：前 3 份讲「怎么锁」，后 2 份讲「锁放的那个 Redis 怎么不挂」。**

```
              锁与并发 知识体系
                     │
     ┌───────────────┴────────────────┐
【锁这条线】                     【Redis 本身这条线】
     │                                │
① 并发编程专题 ── 单机的锁          ④ 持久化 ──── 数据别丢(RDB/AOF)
     │    (synchronized/CAS/volatile)  │
② Redisson源码 ── 跨机器的锁        ⑤ 内存管理与高可用 ── 内存别爆+服务别挂
     │    (互斥/看门狗/Lua)            (过期/淘汰/主从/哨兵/集群)
③ 缓存三大问题 ── 锁的一次实战              │
     │    (击穿 → SETNX 手写互斥锁)         │
⑥ Seata深挖 ──── 分布式事务(管"要么都",     │
     │    内藏 TC 行级全局锁,和锁彻底讲圆)   │
     └────────────┬───────────────────────┘
                  ▼
       交点：锁存在 Redis 里，
       「锁靠不靠谱」取决于「Redis 挂不挂」
       (主从切换丢锁 → 两人同时持锁 → RedLock 争议)
```

| # | 文件 | 一句话定位 |
|---|------|-----------|
| ① | 并发编程专题_线程与锁.docx | 单 JVM 内的并发：三大问题(原子/可见/有序)全部有原理+亲手复现的实验 |
| ② | Redisson分布式锁源码剖析.docx | 跨 JVM 的互斥：Redis 5 大类型打底 + Redisson 源码(Lua/看门狗/可重入) |
| ③ | Redis缓存三大问题与击穿锁实战.docx | 穿透/击穿/雪崩 + 在 product 服务真实落地(SETNX 手写击穿锁) |
| ④ | Redis持久化知识点.docx | Redis 重启数据还在：RDB/AOF/混合 + 本机实测 |
| ⑤ | Redis内存管理与高可用.docx | Redis 一直活着：过期删除/淘汰 LRU-LFU/主从/哨兵/集群 |
| ⑥ | Seata_AT模式分布式事务深挖.docx | 分布式事务：ACID/本地事务地基 → AT 两阶段/undo_log/全局锁 → createOrder 落地 + 实测抓拍 |
| ⑦ | 线程池专题_ThreadPoolExecutor深入解析.docx | ①的续篇：从「单线程同步」到「多线程管理复用」。七大参数/任务调度流程/拒绝策略/工作队列/生命周期/线程数估算 |

---

## 二、推荐阅读顺序

**系统学习（第一遍）：① → ② → ③ → ④ → ⑤**
先单机锁，再分布式锁，再看锁怎么用在缓存上，最后补 Redis 自身的可靠性。

**面试冲刺（复习）：**
- 并发八股 → ① 的第 7 章(volatile/DCL) + 第 4 章(三把武器) + 第 6 章口诀
- Redis 八股 → ② 首章(5 大类型) + ④ + ⑤
- 项目深挖("你项目怎么解决超卖/缓存击穿") → ① 第 5 章 + ③ 全部 + ② 源码部分

---

## 三、按问题反查（想查什么 → 去哪）

| 我想查… | 去哪份 · 哪部分 |
|---------|----------------|
| 超卖是怎么发生的 | ① 第 5 章（Lab1 复现） |
| synchronized / ReentrantLock / CAS 怎么选 | ① 第 4 章 + 4.4 选型表 |
| AQS、state、公平锁 | ① 4.2（ReentrantLock 源码） |
| volatile 为什么不保证原子性 | ① 7.5 |
| 改了变量别的线程看不见（死循环） | ① 7.2~7.4（Lab5） |
| DCL 单例为什么要 volatile | ① 7.9 |
| happens-before / 内存屏障 / MESI | ① 7.6~7.7 |
| 线程池七大参数分别是什么 | ⑦ 三 |
| 任务提交后核心/队列/最大线程的调度顺序 | ⑦ 四（先核心→队列→临时→拒绝） |
| 四种拒绝策略 / CallerRunsPolicy 作用 | ⑦ 五 |
| 为什么禁用 Executors 工厂方法（阿里规约） | ⑦ 七 |
| execute 和 submit 区别 / 异常被吞 | ⑦ 九 |
| CPU 密集 vs IO 密集 线程数怎么设 | ⑦ 十 |
| 优雅关闭线程池 shutdown vs shutdownNow | ⑦ 八、十一 |
| String/Hash/List/Set/ZSet 底层结构 | ② Part1（首章） |
| Redisson 看门狗多久续一次、怎么实现 | ② 源码部分（renewExpiration，每 lease/3=10s） |
| 加锁/解锁的 Lua 脚本逐行 | ② 源码部分（tryLockInnerAsync / unlockInnerAsync） |
| 为什么解锁前要 hexists 验身 | ② 源码部分 + ③ 的"误删锁"坑 |
| 缓存穿透/击穿/雪崩 区别与解法 | ③ 前半 |
| SETNX、SET NX EX、setIfAbsent 是什么 | ③ 基础铺垫章 |
| 手写击穿锁完整代码（holdlock 防误删） | ③ 实战部分 ↔ 代码见 ProductServiceImpl.loadWithMutex |
| RDB 的 fork / 写时复制 COW | ④ 2.3 |
| AOF 三种刷盘 / 重写 | ④ 3.2~3.3 |
| 生产该开什么持久化 | ④ 五~六（混合持久化） |
| 过期 key 什么时候真被删 | ⑤ 一（惰性+定期） |
| 内存满了删谁（8 种淘汰策略） | ⑤ 二 |
| LRU 和 LFU 差在哪（冷数据假装热） | ⑤ 三 |
| 主从复制 / PSYNC 全量增量 | ⑤ 四 |
| 哨兵怎么选主（quorum/Raft） | ⑤ 五 |
| 集群 16384 槽 / MOVED vs ASK | ⑤ 六 |
| 主从切换导致锁丢失（RedLock） | ⑤ 四五 + ② 结尾讨论 |
| ACID 底层怎么实现 / @Transactional 失效场景 | ⑥ 第 0 章 |
| 事务隔离级别 / 大厂为什么改用 RC | ⑥ 0.8 |
| innodb_flush_log_at_trx_commit 三档 / LSN 怎么读 | ⑥ 0.9.1 |
| 长事务危害 / History list length / 抓长事务 SQL | ⑥ 0.9.2 |
| redo vs binlog / 两阶段提交(和 Seata 同思想) | ⑥ 0.9.3 |
| 宕机恢复四场景实验(Docker kill 模拟断电) | ⑥ 0.10 |
| MVCC 版本链 / ReadView 4 规则+数字例题 / RC·RR 差异 | ⑥ 0.11.1~0.11.2 |
| 幻读亲手复现 / 间隙锁·临键锁 / data_locks 看锁 | ⑥ 0.11.3 |
| 为什么用 TransactionTemplate 不用 @Transactional | ⑥ 5.5.1 |
| 分布式事务方案对比（XA/TCC/SAGA/消息表/AT） | ⑥ 0.5~0.6 |
| AT 一阶段 6 件事 / undo_log 前后镜像 | ⑥ 第 1~2 章 |
| 二阶段回滚为什么先"验药" / 防悬挂 | ⑥ 第 3 章 |
| 脏写惨案 / Seata 全局锁 vs Redisson | ⑥ 第 4 章 |
| 分布式锁 vs 分布式事务 vs 全局锁 讲圆 | ⑥ 6.1~6.3 |
| TransactionTemplate 源码 / setRollbackOnly | ⑥ 5.5、5.7 |
| 幽灵消息成因与解法 / 丢消息兜底 | ⑥ 5.6 |

---

## 四、易混概念（吃过亏的，先记住）

- **原子性 ≠ 互斥**：原子性 = 操作不被插队（单机靠 CAS/锁）；互斥 = 同一时刻只放一个人干活。**分布式锁保证的是"跨机器互斥"**，Redisson 的 Lua 脚本自身的原子性是 Redis 单线程执行保证的——两个层面。
- **Seata 不是分布式锁**：Seata 是分布式**事务**（@GlobalTransactional/AT/undo_log），管"多库改动要么全成要么全回滚"；分布式锁管"排队"。
- **volatile 不保证原子性**：`volatile int i; i++` 照样丢更新，计数用 AtomicInteger。
- **④⑤ 不是锁的知识**：它们是 Redis 自身的可靠性，是锁的"地基"。
- **文件大小 ≠ 内容多少**：docx 是 zip 包，python-docx 重存后变小是压缩率差异，判断内容看全文对比。

---

## 五、配套实验代码（都能跑）

> 全部 Lab 的**完整代码 + 实测输出 + 踩坑记录**已内嵌进 ① 的对应知识点位置：
> Lab2 → 4.3 末尾｜Lab1/3/4 → 第 5 章｜Lab5 → 7.2。下表是可运行的源文件位置。

| 实验 | 文件 |
|------|------|
| Lab1 超卖复现（原子性） | mini-mall-order/src/test/.../concurrency/StockRaceConditionTest.java |
| 线程工具类 8 连 demo | 同目录 ThreadToolboxTest.java |
| Lab CAS 无锁扣库存 | 同目录 StockAtomicTest.java |
| 两把锁模拟多实例失效 | 同目录 DistributedLockDemoTest.java |
| Redisson 分布式锁守恒 | 同目录 RedissonLockDemoTest.java |
| Lab5 可见性死循环（volatile） | 同目录 VisibilityDemoTest.java |
| 击穿锁真实落地 | mini-mall-product/src/main/.../service/impl/ProductServiceImpl.java 的 loadWithMutex |

> 跑测试用 JDK 21（D:\jdk-21.0.11），Redisson 用 3.27.2（3.36 有 StackOverflowError bug）。

---

*最后更新：2026-07-19（新增⑦线程池专题_ThreadPoolExecutor深入解析，作为①的续篇）*
