# 12 HTTP 协议与 RESTful 设计（结合项目）

> 面试中 HTTP 不是孤立考八股，而是考"你怎么用 HTTP 语义设计 API、怎么用状态码表达结果、怎么在网关层做安全控制"。以下全部紧扣 mini-mall 项目代码。

---

## 知识点 1：RESTful API 设计——URI 表示资源，HTTP 方法表示操作

**【面试怎么问】** 你们接口是怎么设计的？RESTful 是什么？

**【项目代码】** 网关路由表 `mini-mall-gateway/src/main/resources/application.yml`：

```yaml
routes:
  - id: user-route
    uri: lb://mini-mall-user
    predicates:
      - Path=/user/**
  - id: product-route
    uri: lb://mini-mall-product
    predicates:
      - Path=/product/**
  - id: order-route
    uri: lb://mini-mall-order
    predicates:
      - Path=/order/**
```

Controller 层 `mini-mall-auth/src/main/java/.../AuthController.java`：

```java
@PostMapping("/login")      // 创建登录会话（虽然无状态，但语义是"创建"）
@PostMapping("/register")   // 创建用户资源
@PostMapping("/logout")     // 销毁会话状态（写进黑名单）
```

**【讲解】**
- RESTful 核心：**URI 是名词（资源），HTTP Method 是动词（操作）**。`/user/1` 是"用户 1 号"这个资源，`GET` 是查它，`PUT` 是改它，`DELETE` 是删它。
- 项目里的实践：
  - `GET /product/1` → 查商品（只读，网关白名单放行，免 token）
  - `POST /product` → 创建商品（写操作，网关默认拒绝，必须 admin）
  - `POST /order` → 下单（C 端白名单写操作，本人 token 即可）
  - `DELETE /cart/1` → 删购物车项（自己的资源，C 端写白名单）
- 反例要避免：`/user/getUserById`、`/product/deleteProduct` 这种把动作写在 URL 里的，不是 RESTful。

**【一分钟回答】** 我们按 RESTful 设计：URI 表示资源（/user、/product、/order），HTTP 方法表示操作（GET 查、POST 建、PUT 改、DELETE 删）。网关权限控制也利用了 method 维度，比如 `GET /product/**` 放行给游客浏览，但 `POST /product` 必须管理员。

---

## 知识点 2：HTTP 状态码在项目中的精确使用

**【面试怎么问】** 401 和 403 有什么区别？你们怎么用的？

**【项目代码】** `mini-mall-gateway/.../filter/AuthGlobalFilter.java`：

```java
// 401 Unauthorized: 身份未认证（没 token / token 无效 / token 过期 / 在黑名单）
private Mono<Void> unauthorized(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);  // 401
    return exchange.getResponse().setComplete();
}

// 403 Forbidden: 身份已认证，但没有权限访问
if (isInternal(path)) {
    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);      // 403
    return exchange.getResponse().setComplete();
}
// 被禁用户
if (Boolean.TRUE.equals(disabled)) {
    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);      // 403
    return exchange.getResponse().setComplete();
}
// 非管理员访问 admin 接口
if (needAdmin(path, method) && (r == null || r.intValue() != 1)) {
    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);      // 403
    return exchange.getResponse().setComplete();
}
```

**【讲解】**
- **401 Unauthorized**："我不知道你是谁"——没传 token、token 格式不对、验签失败、过期了、被拉黑了。总之是**身份认证失败**。
- **403 Forbidden**："我知道你是谁，但你不配"——token 有效，但你是普通用户却访问 `/admin/**`、或被管理员禁用、或试图访问内部接口。这是**授权（权限）失败**。
- **429 Too Many Requests**：`AuthController` 里 Sentinel 限流触发，`loginBlock` 返回 429；邮箱验证码 60 秒内重发也返回 429。
- **400 Bad Request**：参数校验失败（`@Valid` 触发）、文件魔数检测不通过。
- **200 / 500**：业务成功返 200，但 `GlobalExceptionHandler` 会把业务异常（如"库存不足"）包装成 200 响应体（`Result.error()`），真正的 500 只留给未预期的系统异常。

**【一分钟回答】** 401 是认证失败（没 token 或 token 无效），403 是授权失败（有 token 但权限不够）。项目中内部接口一律 403，被禁用户 403，管理员接口非 admin 访问 403；登录限流 429，参数错误 400。业务异常走 Result 包装体而不是裸 500。

---

## 知识点 3：HTTP Header 的实战运用——从标准头到自定义透传头

**【面试怎么问】** 你们微服务之间怎么传递用户身份？traceId 怎么做的？

**【项目代码】** 三条链路，全是 Header 透传：

```java
// ① 客户端 → 网关: Authorization: Bearer {JWT}
String authHeader = request.getHeaders().getFirst("Authorization");
final String jwt = authHeader.substring(7);   // 去掉 "Bearer " 前缀

// ② 网关 → 下游服务: 验完 JWT 后把身份塞自定义 header
ServerHttpRequest mutated = request.mutate()
        .header("X-User-Id", String.valueOf(uid))
        .header("X-User-Role", String.valueOf(r))
        .header("X-Trace-Id", traceId)        // 来自 RequestLogFilter
        .build();

// ③ 下游服务 → 再下游(Feign): FeignAuthInterceptor 续传
public void apply(RequestTemplate template) {
    Long uid = SecurityContextHolder.getUserId();
    if (uid != 0L) template.header("X-User-Id", String.valueOf(uid));
}
```

**【讲解】**
- `Authorization: Bearer <token>` 是 OAuth2/JWT 的**标准写法**，RFC 6750 规定。不能自己发明 `X-Token` 之类的名字。
- `X-User-Id`、`X-User-Role` 是**自定义头**，X- 前缀表示非标准（虽然现在 RFC 6648 不建议用 X- 了，但业内约定俗成）。它们只在**服务间传递**，浏览器前端看不到。
- `X-Trace-Id` 用于**全链路追踪**：网关生成 8 位短 UUID，塞 header 透传所有服务，日志里 `[abc12345]` 一搜就能串起整条调用链。
- **为什么不用 Cookie/Session？** 微服务多实例，Session 存在哪台机器上？JWT 无状态 + Header 透传，任意实例都能处理。

**【一分钟回答】** 客户端带 Authorization: Bearer JWT 到网关，网关验签后把 userId/role 写入 X-User-Id/X-User-Role 自定义 header 转发给下游；下游 MVC 拦截器读 header 存 ThreadLocal；Feign 调用时再写回 header。TraceId 也是同机制，8 位短 UUID 全链路透传，日志可串联。

---

## 知识点 4：HTTP 无状态性与 Cookie/Session vs Token

**【面试怎么问】** 为什么用 JWT 不用 Session？HTTP 不是无状态的吗？

**【讲解】**
- HTTP 协议本质是**无状态**：服务器默认不记得"上一个请求是不是你发的"。Session 是在服务器内存/Redis 里存一份"状态"，靠 Cookie 里的 `JSESSIONID` 关联。
- **Session 在单体里好用，在微服务里是灾难**：
  - 实例 A 存了 Session，负载均衡打到实例 B 就丢了（除非共享 Redis Session，但引入了耦合）。
  - 网关是纯转发，不可能去查每个服务的 Session 存储。
- **JWT 把"状态"放到客户端**：服务器只验签名，不存会话，天然适合水平扩展。mini-mall 的网关只做验签，不依赖任何会话存储（除了黑名单，那是额外加的，不是核心认证机制）。
- 代价就是 JWT 无法主动失效，项目用 Redis 黑名单补（见 02-认证授权）。

**【一分钟回答】** HTTP 无状态，Session 是有状态的解决方案但依赖服务端存储，微服务多实例下要么共享 Redis 要么粘滞会话，都不够干净。JWT 把认证信息放客户端，服务端只验签不存状态，天然水平扩展。项目里网关统一验 JWT，下游无感知。

---

## 知识点 5：HTTP 方法的安全性与幂等性——网关权限控制的设计依据

**【面试怎么问】** GET 和 POST 有什么区别？幂等性是什么？

**【项目代码】** 网关 `AuthGlobalFilter` 中对 method 的精细控制：

```java
// GET /product/** 在白名单 → 免 token（游客浏览）
new WhitelistRule("/product", Set.of(HttpMethod.GET))

// 但 POST /product（创建商品）不在白名单 → 默认拒绝 → 必须 admin
private boolean needAdmin(String path, HttpMethod method) {
    boolean isWrite = method == HttpMethod.POST
            || method == HttpMethod.PUT
            || method == HttpMethod.DELETE
            || method == HttpMethod.PATCH;
    // ...
}
```

**【讲解】**
- **安全性**：GET/HEAD 不应该有副作用，只读。所以网关把 `GET /product` 放行给未登录用户浏览。
- **幂等性**：同样的请求执行一次和多次结果一样。
  - `GET`：幂等且安全
  - `PUT`：幂等（覆盖更新，多次执行结果一样）
  - `DELETE`：幂等（删一次和删多次都是没了）
  - `POST`：**不幂等**（创建资源，每次执行都新增一条）
  - `PATCH`：视实现而定，项目里慎用
- 这个语义直接影响**重试策略**：Feign 默认只对 GET 请求做重试（因为幂等），POST 请求失败不重试（怕重复创建订单）。如果业务上订单创建接口做了幂等（用唯一请求号），可以配置 Feign 对特定 POST 也重试。

**【一分钟回答】** GET 安全且幂等，所以网关把商品浏览 GET 放行给游客；POST 不幂等，默认拒绝且需管理员。幂等性影响重试策略：Feign 默认 GET 失败可重试，POST 不重试（除非接口做了幂等，如订单用请求号去重）。

---

## 知识点 6：HTTP 持久连接与 Keep-Alive——微服务间通信的效率

**【面试怎么问】** 微服务之间调用频繁，HTTP 连接怎么优化？

**【讲解】**
- HTTP/1.0 默认短连接：每请求一次 TCP 三次握手 + 数据传输 + 四次挥手， overhead 巨大。
- HTTP/1.1 默认**持久连接（Keep-Alive）**：一个 TCP 连接上可以发多个 HTTP 请求，复用连接。
- 项目中的 Feign/RestTemplate 底层是 Apache HttpClient/OkHttp，默认有**连接池**：
  - 连接池大小、单路由最大连接数、连接超时、读取超时、连接存活时间都是可配项。
  - 没配好的话，高并发下会出现"连接池耗尽"或"大量 TIME_WAIT"的问题。
- 网关基于 Netty，也是长连接模型：Netty 的 Channel 复用，比短连接效率高很多。

**【一分钟回答】** HTTP/1.1 默认 Keep-Alive 持久连接，Feign 底层连接池复用 TCP 连接，避免每个请求都三次握手。网关用 Netty 也是基于长连接。实际部署时要配连接池大小和超时，防高并发下连接耗尽或 TIME_WAIT 堆积。

---

## 知识点 7：HTTPS/TLS——项目中的安全传输层

**【面试怎么问】** 登录密码传输安全吗？HTTPS 了解吗？

**【讲解】**
- 项目开发环境是 HTTP，但**生产环境必须 HTTPS**：
  - 否则 `Authorization: Bearer xxx` 这个 header 在公网上裸奔，token 随便被抓。
  - 登录时的密码明文（虽然最终是 BCrypt 比对，但传输过程是明文）也暴露。
- HTTPS = HTTP + TLS/SSL，核心三件事：
  1. **加密**：对称加密传输数据，防窃听
  2. **完整性**：MAC（消息认证码）防篡改
  3. **身份认证**：服务器证书（可能还有客户端证书）防冒充
- 支付宝回调的 RSA2 验签（见 08-支付），本质也是"防篡改"思想的落地——即使走 HTTP，回调内容里的签名保证"这确实是支付宝发的"。

**【一分钟回答】** 开发环境 HTTP，生产必须 HTTPS，否则 token 和密码在公网裸奔。HTTPS 通过 TLS 提供加密、完整性校验和身份认证三层保护。支付宝回调即使允许 HTTP，也靠 RSA2 签名防篡改，是同样的安全思想。

---

## 知识点 8：HTTP 缓存头——商品列表/详情页的缓存策略

**【面试怎么问】** 商品数据怎么缓存？HTTP 缓存头用过吗？

**【讲解】**
- 项目中 Redis 做了数据缓存（见 03-Redis），但 HTTP 层缓存也可以配合：
  - `Cache-Control: max-age=3600`：告诉浏览器这个商品详情页 1 小时内不用再来问服务器
  - `ETag` + `If-None-Match 304`：服务器给商品数据算个哈希作 ETag，下次浏览器带上来，没变就返 304 不返 body
  - `Last-Modified` + `If-Modified-Since`：类似，但时间精度不够时不如 ETag
- 注意：**动态接口不要瞎加缓存**。`/order` 这种跟用户相关的，必须 `Cache-Control: no-store`。
- 静态资源（商品图片在 MinIO/CDN）才是缓存大头，配好 `max-age=31536000`（一年），文件名带 hash（如 `img.abc123.jpg`），更新即换 URL。

**【一分钟回答】** 商品详情页可用 HTTP 缓存配合 Redis：静态资源配强缓存 max-age 一年+文件名 hash，动态数据用 ETag 协商缓存。但用户相关的订单/购物车接口必须 no-store，不能缓存。

---

## 知识点 9：Content-Type 与报文体协商

**【面试怎么问】** 你们接口返回什么格式？Content-Type 怎么定的？

**【项目代码】** `OAuthController.java` 里 GitHub 换 token：

```java
HttpHeaders headers = new HttpHeaders();
headers.set("Accept", "application/json");   // 告诉 GitHub 我要 JSON
```

`AuthController.java` 的接口：

```java
@RestController  // 默认 @ResponseBody，返回 JSON，Content-Type: application/json
public class AuthController { ... }
```

**【讲解】**
- `Content-Type: application/json`：项目前后端交互、服务间 Feign 调用全部 JSON。
- `Accept: application/json`：请求方声明"我要 JSON"，服务端如果只能返 XML 就报 406 Not Acceptable。
- 文件上传场景（`multipart/form-data`）：头像/评价图片上传用 `MultipartFile`，MinIO 服务端再校验魔数。
- 支付宝回调：`application/x-www-form-urlencoded`，所以后端用 `@RequestParam` 或 `HttpServletRequest.getParameter()` 接收，不是 `@RequestBody`。

**【一分钟回答】** 项目全部 JSON（application/json），Feign 调用、REST 接口、GitHub API 交互都是。文件上传走 multipart/form-data，支付宝回调是 x-www-form-urlencoded，这两种场景后端接收方式不一样。

---

## 知识点 10：URI 设计安全——路径参数、查询参数、请求体的选择

**【面试怎么问】** 什么时候用路径参数，什么时候用查询参数？

**【项目代码】**

```java
// 路径参数: 资源的唯一标识
@GetMapping("/product/{id}")      // /product/123

// 查询参数: 过滤、搜索、分页
@GetMapping("/search/product")    // /search/product?keyword=手机&page=1&size=20

// 请求体: 创建/更新资源的复杂数据
@PostMapping("/order")
public Result<Order> createOrder(@RequestBody CreateOrderDTO dto)
```

**【讲解】**
- **路径参数（Path Variable）**：表示"资源定位"的一部分，RESTful 核心。`/product/123` 比 `/product?id=123` 更语义化。
- **查询参数（Query Param）**：表示"条件、过滤、排序、分页"。搜索接口 `keyword`、`page`、`size` 全是查询参数，因为它们是检索条件不是资源 ID。
- **请求体（Request Body）**：POST/PUT 的复杂数据。不能用 `@RequestBody` 接 GET 请求（虽然 Spring 技术上允许，但违反 HTTP 语义，某些代理/缓存会丢弃 body）。
- 安全注意：路径参数和查询参数都会进日志（URL 是明文记录的），**敏感信息（密码、token）绝不能放 URL**，只能放 header 或 body。

**【一分钟回答】** 路径参数定位资源（/product/123），查询参数做过滤分页（?page=1&size=20），请求体传复杂对象。敏感信息绝不出现在 URL 里，因为 URL 会进 access log。密码和 token 只放 header 或 body。
