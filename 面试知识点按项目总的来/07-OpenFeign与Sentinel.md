# 07 OpenFeign 远程调用 与 Sentinel 限流熔断

> **配套教科书 docx（系统原理，配这份项目速记一起看）**
> - `../散笔记/限流/SpringGateway与Sentinel限流全解.docx` —— 限流四算法(手写Java) / Gateway原生RequestRateLimiter / Sentinel架构(Slot责任链+LeapArray) / 七大规则详解 / 网关流控三大踩坑
>
> 本文件是**结合项目的面试速记**（知识点1-5 落项目代码）；docx 是**从算法到原理的教程**。下面知识点6-9 是把 docx 里 07 没覆盖的理论深度补进来。

## 知识点 1：OpenFeign 声明式调用

**【面试怎么问】** 服务间怎么调用？Feign 的原理？

**【项目代码】** `mini-mall-order/.../client/ProductFeignClient.java`（接口声明）+ 使用处：

```java
@FeignClient(name = "mini-mall-product",                    // Nacos 里的服务名
             fallback = ProductFeignClientFallback.class)   // 降级实现
public interface ProductFeignClient {
    @GetMapping("/product/{id}")
    Result<Map<String, Object>> getById(@PathVariable("id") Long id);

    @PostMapping("/product/internal/deduct-stock")
    Result<Integer> deductStock(@RequestParam Long id, @RequestParam Integer qty);
}

// 业务里像调本地方法一样用:
Result<Map<String, Object>> pResp = productFeignClient.getById(ci.getProductId());
```

**【讲解】**
- 原理：接口 + 注解 → Feign 动态代理生成实现 → 按服务名查 Nacos → LoadBalancer 选实例 → 发 HTTP。开发者面对的是 Java 接口，HTTP 细节全部被藏掉。
- 服务间接口走 `/internal/` 路径前缀——网关对含 internal 的路径一律 403（见 02），**内部接口只能 Feign 直连调，外网打不到**。
- Feign 请求会被 `FeignAuthInterceptor` 拦截自动带上 X-User-Id（身份透传），Seata 场景还自动透传 XID，SkyWalking 自动透传 trace 上下文——**拦截器链是微服务"上下文接力"的统一机制**。

**【一分钟回答】** Feign 用接口+注解声明远程调用，动态代理把方法调用翻译成 HTTP：服务名到 Nacos 解析实例，LoadBalancer 负载均衡。我们所有服务间调用都走 Feign，内部接口用 /internal 前缀配合网关封禁实现"仅内网可调"。

---

## 知识点 2：Fallback 降级的差异化设计（不是所有接口降级方式都一样）

**【面试怎么问】** 依赖的服务挂了怎么办？降级返回什么？

**【项目代码】** `mini-mall-order/.../fallback/ProductFeignClientFallback.java`

```java
@Component
public class ProductFeignClientFallback implements ProductFeignClient {
    @Override
    public Result<Map<String, Object>> getById(Long id) {
        return Result.error(503, "商品服务暂不可用");      // 查询: 返错误码, 上游看code走兜底
    }
    @Override
    public Result<Integer> deductStock(Long id, Integer qty) {
        return Result.error(503, "库存服务暂不可用,请稍后再试"); // 扣库存: 必须失败! 不能假装成功
    }
    @Override
    public Result<Integer> restoreStock(Long id, Integer qty) {
        // ⚠ 只 log 不返 error: cancel 流程已把订单关了, 不能因还库存失败把取消流程炸掉
        // 生产应记"待补偿库存"表, 后台 job 重试
        log.warn("[Feign-Fallback] restoreStock 降级(库存未还)");
        return Result.success(0);
    }
}
```

**【讲解】**
- 亮点是**同一个 Client 里三个方法降级策略不同**，按业务语义定：
  - `getById` 挡路型：返回错误让主流程失败（没有商品信息没法下单）；
  - `deductStock` 绝不能假装成功：否则超卖；
  - `restoreStock` 假装成功 + 记日志：取消订单的主流程不应被"还库存失败"阻断，库存可以事后补偿。
- 另一个实例：product 调 review 拿评分聚合，fallback 返回 null 时**跳过本次刷新**而不是把评分清零——降级值选错会造成数据破坏。
- 面试金句：**降级不是"返回一个不报错的值"，而是"这个操作失败时业务上正确的行为是什么"**。

**【一分钟回答】** 每个 FeignClient 配 fallback 类，但降级策略按方法语义差异化：读接口返回 503 让上游走兜底；扣减类写接口必须失败防超卖；补偿类写接口静默吞掉并记日志，不阻塞主流程、留待异步补偿。降级值的选择本质是业务决策。

---

## 知识点 3：Sentinel 网关限流

**【面试怎么问】** 限流做在哪一层？规则怎么配？

**【项目代码】** 依赖 `spring-cloud-alibaba-sentinel-gateway`（自动把**每条路由识别为资源，资源名=路由ID**），规则存 Nacos：

```json
[
  {"resource":"ai-route",      "grade":1, "count":5,   "intervalSec":1, "burst":0},
  {"resource":"auth-route",    "grade":1, "count":20,  "intervalSec":1, "burst":0},
  {"resource":"payment-route", "grade":1, "count":50,  "intervalSec":1, "burst":0},
  {"resource":"seckill-route", "grade":1, "count":100, "intervalSec":1, "burst":50},
  {"resource":"search-route",  "grade":1, "count":30,  "intervalSec":1, "burst":0}
]
```

**【讲解】**
- 阈值是**按业务成本定的**，不是拍脑袋：ai-route 最严（5 QPS，每次调用烧 DeepSeek 的钱）；auth 20 QPS 防爆破；seckill 给 100+50 突发（洪峰是它的常态，burst 允许短时超出）。
- 限流位置在网关 = 恶意流量在最外层被挡掉，不消耗下游任何资源。
- 被限流的请求返回统一 JSON：`429 {"code":429,"message":"网关限流: 请求太频繁..."}`，由自定义 WebExceptionHandler 输出（这里有个大坑，见 11 调试实战）。
- 为什么不用 Gateway 自带的 RequestRateLimiter 过滤器：它只有 Redis 令牌桶一种算法、按 route 配死在 yml、没有控制台观测；Sentinel 有滑动窗口/流控效果可选、规则可热更新、有 dashboard 实时监控，且和业务服务的熔断降级同一套体系。

**【一分钟回答】** 限流放网关最外层，Sentinel gateway adapter 把每条路由自动注册为资源，按路由配 QPS：AI 接口 5（成本贵）、登录 20（防爆破）、秒杀 100+突发 50。被限流返回 429 JSON。选 Sentinel 而非内置 RequestRateLimiter 是为了热更新规则、控制台观测和与业务侧熔断统一技术栈。

---

## 知识点 4：Sentinel 规则持久化到 Nacos

**【面试怎么问】** Sentinel 控制台配的规则重启就丢，怎么办？

**【项目代码】** 网关 yml（推送模式的数据源配置）：

```yaml
spring.cloud.sentinel:
  datasource:
    gw-flow:
      nacos:
        server-addr: 127.0.0.1:8848
        data-id: mini-mall-gateway-gw-flow-rules
        group-id: SENTINEL_GROUP          # 和业务配置(DEFAULT_GROUP)分组隔离
        data-type: json
        rule-type: gw-flow                # ⭐ 网关规则必须 gw-flow, 写 flow 反序列化失败
```

业务服务（user）用的是代码注册方式 `SentinelNacosConfig.java`：

```java
ReadableDataSource<String, List<FlowRule>> flowDataSource = new NacosDataSource<>(
        NACOS_SERVER, GROUP, FLOW_DATA_ID,
        source -> JSON.parseObject(source, new TypeReference<List<FlowRule>>() {}));
// 挂到规则中心: 启动拉一次 + 订阅变更, Nacos 一改秒级推给服务, 不用重启
FlowRuleManager.register2Property(flowDataSource.getProperty());
```

**【讲解】**
- Sentinel 规则默认只在**内存**里，服务或控制台重启全丢。接 Nacos 数据源后：启动时拉取 + 长连接订阅变更，改 Nacos 配置秒级生效（实测改 3→30 QPS 不重启立即生效）。
- **数据流是单向的**：Nacos → 服务。在 Sentinel 控制台上改的规则不会写回 Nacos，重启就被 Nacos 的值覆盖——所以规则的"唯一真相"是 Nacos，控制台只当监控看板用。
- 一个实测坑：网关规则的 `rule-type` 必须是 `gw-flow`（GatewayFlowRule 类型），写成 `flow` 会反序列化失败静默不生效。

**【一分钟回答】** 用 sentinel-datasource-nacos 把规则存 Nacos：服务启动拉取并订阅变更，改规则秒级推送免重启。注意数据流单向（Nacos→服务），控制台改的不回写，所以运维约定规则只改 Nacos；网关规则类型必须 gw-flow。

---

## 知识点 5：业务侧熔断降级（@SentinelResource）

**【面试怎么问】** 除了网关限流，服务内部怎么做熔断降级？blockHandler 和 fallback 什么区别？

**【项目代码】** `AuthController.login()`：

```java
@PostMapping("/login")
@SentinelResource(value = "authLoginResource",
        blockHandler = "loginBlock",      // 被限流/熔断规则拦住 → 走这里
        fallback = "loginFallback")       // 业务代码抛异常 → 走这里
public Result<AuthResponse> login(@Valid @RequestBody UserLoginDTO dto) { ... }

public Result<AuthResponse> loginBlock(UserLoginDTO dto, BlockException ex) {
    return Result.error(429, "登录请求太频繁, 请稍后再试");
}
public Result<AuthResponse> loginFallback(UserLoginDTO dto, Throwable ex) {
    if (ex instanceof RuntimeException re) throw re;   // 业务异常透传给全局异常处理器
    throw new RuntimeException(ex);
}
```

**【讲解】**
- 两个 handler 的分工是必考题：**blockHandler 接"规则拦截"**（BlockException：限流/熔断/热点），**fallback 接"业务异常"**。方法签名要求：和原方法同参数同返回值，blockHandler 末尾多个 BlockException 参数。
- 这里 fallback 故意把异常再抛出去——**不想让 Sentinel 吞掉业务异常**（比如"密码错误"必须原样给到全局异常处理器变成友好提示），fallback 只是个透传通道。
- 熔断（DegradeRule）规则也在 Nacos：异常率/慢调用比例超阈值时断路器打开，快速失败一段时间再半开试探——保护的是**调用方自己**不被慢依赖拖死。

**【一分钟回答】** @SentinelResource 双通道：blockHandler 处理限流熔断规则触发（BlockException），fallback 处理业务异常。我们登录接口限流后返回 429 友好提示，而 fallback 选择把业务异常透传给全局异常处理器，避免 Sentinel 吞掉"密码错误"这类正常业务反馈。熔断规则同样持久化在 Nacos。

---

## 知识点 6：限流四大经典算法（理论硬通货，详解见 docx）

**【面试怎么问】** 常见的限流算法有哪些？各自优缺点？令牌桶和漏桶区别？

**【四种算法】**

| 算法 | 模型 | 能否突发 | 输出 | 代表 |
|------|------|---------|------|------|
| 固定窗口计数器 | 每窗口计数，超阈值拒 | 能(有临界问题) | 差 | 简单计数 |
| 滑动窗口计数器 | 统计"最近1秒"随时间滑动 | 能 | 较好 | **Sentinel(LeapArray)** |
| 漏桶 Leaky Bucket | 固定速率漏水，满则溢 | **不能(强行匀速)** | 最好 | **Sentinel 排队等待** |
| 令牌桶 Token Bucket | 匀速放令牌，取到才放行 | **能(桶容量内)** | 好 | **Gateway / Guava** |

**【讲解】**
- **固定窗口的临界问题**：两个相邻窗口交界处（如各 100ms）可能挤入 2 倍阈值请求都不被拦——滑动窗口把统计范围改成"当前往前推 1 秒"来解决。
- **令牌桶 vs 漏桶（必考）**：漏桶限制"**出去**的速率"（绝对匀速，突发也被压平）；令牌桶限制"**进来**的速率"但桶能攒令牌，所以**容忍突发**。所以要平滑保护下游用漏桶，要应对正常突发用令牌桶。
- **和 Sentinel 流控效果对应**：快速失败=滑动窗口、排队等待=漏桶、Warm Up=令牌桶变体（阈值从 threshold/coldFactor 逐步爬升）。
- docx 里每种算法都有手写 Java 实现（`tryAcquire()`），面试要求现场写令牌桶时照着那个写。

**【一分钟回答】** 四种：固定窗口简单但有临界突刺；滑动窗口解决临界、Sentinel 底层用它；漏桶固定速率漏水强行匀速、对应 Sentinel 排队等待；令牌桶匀速发令牌、桶内容忍突发、Gateway 和 Guava 用它。核心区别是漏桶限出速率、令牌桶限入速率但容忍突发。

---

## 知识点 7：Sentinel 工作原理 —— Slot 责任链

**【面试怎么问】** Sentinel 内部是怎么工作的？一个请求进来经过哪些环节？

**【原理】** 每次访问资源 = 申请一个 Entry，请求顺着一条 **Slot 责任链** 走，每个 Slot 干一件事：

```
请求 → Entry → [ Slot 责任链 ]
  NodeSelectorSlot   构建调用链路树(簇点链路可视化就是它)
  ClusterBuilderSlot 构建集群节点，汇总统计维度
  StatisticSlot      ⭐核心：统计 QPS/RT/线程数/异常(滑动窗口 LeapArray 在这)
  ───── 上面"统计"，下面"按规则判断" ─────
  AuthoritySlot      授权(黑白名单)
  SystemSlot         系统自适应保护
  FlowSlot           流控 → 超了抛 FlowException
  DegradeSlot        熔断 → 断路抛 DegradeException
  ParamFlowSlot      热点参数 → 抛 ParamFlowException
任一 Slot 判拦截 → 抛对应 BlockException，后面不再走
```

**【讲解】**
- 一句话记：**StatisticSlot 负责"记录发生了什么"，后面几个 Slot 负责"按规则决定拦不拦"**。
- **滑动窗口 LeapArray**：StatisticSlot 把 1 秒切成若干小格子（环形数组复用），每格统计自己那段，当前 QPS = 最近 1 秒覆盖格子之和——既解决临界问题又内存恒定。
- 为什么各种规则抛不同异常，都继承 `BlockException`：这就是项目 `SentinelGatewayConfig` 里"认整个 BlockException 家族（含 ParamFlowException）"的原因——网关流控底层转成参数流控，抛的是 ParamFlowException。

**【一分钟回答】** Sentinel 靠 Slot 责任链工作：请求进来经 StatisticSlot 用滑动窗口(LeapArray)统计 QPS/RT/异常，再依次过 Authority/System/Flow/Degrade/ParamFlow 各 Slot 按规则判断，任一拦截就抛对应 BlockException。统计与判断分离是它的核心设计。

---

## 知识点 8：熔断降级状态机 + 三种策略（补知识点5的深度）

**【面试怎么问】** Sentinel 熔断的三种策略？断路器状态怎么流转？

**【三种降级策略】**

| 策略 | grade | 触发条件 |
|------|-------|---------|
| 慢调用比例 | 0 | 慢调用(RT>阈值)占比超设定比例 |
| 异常比例 | 1 | 异常请求占比超阈值 |
| 异常数 | 2 | 统计时长内异常个数超阈值 |

**【四个通用参数】**
```
count            阈值(慢调用RT毫秒 / 比例0~1 / 异常个数)
timeWindow       熔断时长(秒)，断路多久后进半开
minRequestAmount 最小请求数，太少不触发(避免偶发误判)
statIntervalMs   统计时长(毫秒)
```

**【状态机（必画）】**
```
        达到熔断条件
  CLOSED ──────────► OPEN
  (正常放行)      (熔断时长内直接拒绝，不打下游)
    ▲                 │ timeWindow 到
    │探测成功          ▼
    └──────────── HALF_OPEN(放一个探测请求)
       探测失败→回OPEN
```

**【讲解】**
- 半开(HALF_OPEN)是关键：熔断时长到了不直接恢复，而是先放**一个探测请求**试水——成功才回 CLOSED，失败重新 OPEN，避免下游还没好就把全部流量放回去二次打死。
- `minRequestAmount` 的意义：QPS 很低时（比如就 2 个请求 1 个异常=50%）不该触发熔断，设最小请求数过滤偶发抖动。
- 熔断保护的是**调用方自己**：下游慢时快速失败，不让调用方线程都堆在等待下游上被拖死（回到限流三要素里的"线程数"维度）。

**【一分钟回答】** 三策略：慢调用比例、异常比例、异常数，都配阈值+熔断时长+最小请求数+统计时长。状态机 CLOSED→(达标)→OPEN→(熔断时长到)→HALF_OPEN→(探测成功)→CLOSED。半开只放一个探测请求防止下游没恢复就被二次打死。熔断保护的是调用方自己不被慢依赖拖死。

---

## 知识点 9：Sentinel 其他三大规则（热点/系统/授权）

**【面试怎么问】** 除了限流和熔断，Sentinel 还能做什么？

**【三大规则】**

| 规则 | 作用 | 场景 |
|------|------|------|
| 热点参数限流 ParamFlowRule | 按某个**参数的具体值**分别限流 | 按商品ID/活动ID限，爆款单独限不挤占别人 |
| 系统自适应保护 SystemRule | **应用级**兜底，只对入口资源生效 | LOAD/CPU/RT/线程数/入口QPS 任一超标就收紧 |
| 授权规则 AuthorityRule | 按来源 origin 黑白名单 | 只允许/禁止特定来源访问资源 |

**【讲解】**
- **热点参数限流**：普通限流是"整个接口限 1000 QPS"；热点限流是"按 activityId 每个值各限 1000 QPS"，用 LRU 统计热点值 + 令牌桶。还支持**参数例外项**（给 VIP 活动 ID 单独放宽）。项目网关的按 IP/header 限流底层就是它，所以抛 ParamFlowException。
- **系统自适应保护**：不用给每个接口配规则，用整机指标（Linux load、CPU、平均 RT、入口总 QPS、入口线程数）做最后一道自适应闸门，只对入口(EntryType=IN)生效。
- **授权规则**：需实现 `RequestOriginParser` 从请求（如某 header）解析出 origin，再配黑/白名单。

**【一分钟回答】** 还有三大规则：热点参数限流按参数值粒度限（爆款商品单独限、支持参数例外项）；系统自适应保护用整机 LOAD/CPU/RT/QPS 做应用级兜底、只管入口；授权规则按请求来源 origin 做黑白名单。这三个加上限流熔断，Sentinel 覆盖了流量治理的全场景。
