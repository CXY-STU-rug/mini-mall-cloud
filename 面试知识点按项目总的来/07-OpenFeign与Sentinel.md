# 07 OpenFeign 远程调用 与 Sentinel 限流熔断

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
