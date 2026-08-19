# mini-mall-cloud 接口文档

> 微服务电商项目 —— 后端 `mini-mall-cloud`，配套前端 `mini-mall-cloud-web`（C 端商城）与 `mini-mall-cloud-admin`（管理后台）。
> 本文档由源码（各服务 `Controller`）扫描整理，路径均为**网关视角**（前端实际请求的路径）。

---

## 〇、在线接口文档（Swagger / Knife4j）—— 推荐

本项目已接入 **Knife4j（OpenAPI 3）**，接口文档由代码自动生成，可在线调试，**优先看这个**（本 Markdown 作为整体索引与离线速查）。

| 入口 | 地址 | 说明 |
|---|---|---|
| **统一聚合入口** | http://localhost:9080/doc.html | 网关聚合，下拉切换查看**所有微服务**的接口，改一次接口文档自动更新 |
| 单服务文档 | http://localhost:{服务端口}/doc.html | 只看某个服务（端口见 1.5） |

**调试带 token**：打开 `doc.html` → 右上角"Authorize/文档管理 → 全局参数"填 `Authorization = Bearer <你的JWT>` → 即可在线发起需登录的请求。

**启动验证步骤**：
1. 先起中间件（Nacos / MySQL / Redis 等，见项目 compose）。
2. 启动 `mini-mall-gateway` 及任意业务服务（如 user、product）。
3. 浏览器打开 http://localhost:9080/doc.html ，在左上角下拉框应能看到已启动服务的分组。

> ⚠️ 聚合用 `discover` 模式（从 Nacos 自动发现），所以**只有已注册到 Nacos 且已启动的服务**才会出现在下拉里。生产环境应在网关 `knife4j.gateway.enabled` 关闭或加访问保护。

---

## 一、通用约定

### 1.1 请求入口
| 项 | 值 | 说明 |
|---|---|---|
| 网关地址 | `http://localhost:9080` | Spring Cloud Gateway，所有请求的唯一入口 |
| 前端 baseURL | `/api` | Vite 开发代理把 `/api` 转发到网关 `9080`（见两个前端 `vite.config.ts`） |
| 认证头 | `Authorization: Bearer <JWT>` | 前端 `http.ts` 请求拦截器自动注入 |

> 网关按**路径前缀**把请求路由到对应微服务（见 1.5），前端无需关心背后是哪个服务、哪个端口。

### 1.2 统一响应体 `Result<T>`
所有接口返回同一结构：
```json
{ "code": 200, "message": "success", "data": {} }
```
| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | `200` = 成功；非 200 = 业务错误（前端拦截器弹提示） |
| `message` | string | 提示文案 / 错误原因 |
| `data` | 泛型 | 真正的业务数据；前端拦截器成功时直接返回 `data` |

### 1.3 常见状态码
| code / HTTP | 含义 | 触发场景 |
|---|---|---|
| `200` | 成功 | 正常 |
| `400` | 参数校验失败 | `@Valid` DTO 校验不通过（如手机号格式错） |
| `401` | 未登录 / token 失效 | 网关校验 JWT 失败；前端跳登录页 |
| `403` | 无权限 | 访问 `/admin/**` 但 `role≠1`；越权访问他人资源 |
| `429` | 限流 | Sentinel 触发（如登录过于频繁） |
| `5xx` | 服务器错误 | 前端统一提示"当前访问人数较多" |

### 1.4 鉴权分层（网关 `AuthGlobalFilter` 统一控制）
| 层级 | 规则 | 典型接口 |
|---|---|---|
| **公开**（白名单） | 无需 token | 登录/注册、商品列表与详情、分类、评价查询、搜索、可领券列表、支付回调 |
| **需登录**（C 端写） | 必须带 JWT，网关注入 `X-User-Id` | 购物车、下单、收藏、我的券、地址、支付、退款申请、AI 客服 |
| **管理员** | 必须带 JWT 且 `role=1`，否则 403 | 所有 `/admin/**` 前缀接口 |

### 1.5 网关路由前缀 → 微服务映射
| 路径前缀 | 目标服务 | 内部端口 |
|---|---|---|
| `/auth/**` | mini-mall-auth | — |
| `/user/**`、`/coupon/**`、`/admin/user/**`、`/admin/role/**`、`/admin/permission/**` | mini-mall-user | 9001 |
| `/product/**`、`/category/**`、`/favorite/**`、`/admin/product/**` | mini-mall-product | 9002 |
| `/cart/**`、`/order/**`、`/seckill/**`、`/admin/order/**` | mini-mall-order | 9003 |
| `/review/**` | mini-mall-review | 9004 |
| `/search/**` | mini-mall-search | 9005 |
| `/pay/**`、`/refund/**`、`/admin/refund/**` | mini-mall-payment | 9008 |
| `/ai/**` | mini-mall-ai | 9009 |
| `/file/**` | mini-mall-file | — |

---

## 二、认证服务（auth）

> 前缀 `/auth`，登录/注册/发码在网关**白名单**（免 token）。登录成功统一返回 `AuthResponse`：
> ```json
> { "token": "eyJ...JWT", "user": { "id": 1, "username": "...", "role": 0, "...": "..." } }
> ```

| 方法 | 路径 | 鉴权 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|---|
| POST | `/auth/login` | 公开 | 账号密码登录 | `{ username, password }` | `AuthResponse` |
| POST | `/auth/register` | 公开 | 注册（注册即登录） | `{ username, password, phone?, nickname? }` | `AuthResponse` |
| POST | `/auth/logout` | 需登录 | 登出（当前 token 加入 Redis 黑名单立即失效） | Header `Authorization` | — |
| POST | `/auth/email/code` | 公开 | 发送邮箱验证码 | `{ email }` | — |
| POST | `/auth/email/login` | 公开 | 邮箱验证码登录 | `{ email, code }`（code 为 6 位数字） | `AuthResponse` |
| GET | `/auth/oauth/github/login` | 公开 | 获取 GitHub 授权跳转 URL | Query `redirect?`（站内路径） | `{ url }` |
| GET | `/auth/oauth/github/callback` | 公开 | GitHub 回调换取登录态 | Query `code`、`state?` | `AuthResponse` 或 302 回前端 |

**注册字段校验**：`username` 3~20 位必填；`password` 6~50 位必填；`phone` 选填（中国大陆手机号正则）；`nickname` 选填 ≤20 字。

---

## 三、用户服务（user）

### 3.1 用户信息 `/user`
| 方法 | 路径 | 鉴权 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|---|
| GET | `/user/me` | 需登录 | 当前登录用户资料 | — | `UserProfileVO` |
| PUT | `/user/me` | 需登录 | 修改本人资料 | `{ nickname, phone, email, avatar }` | — |
| GET | `/user/{id}` | 需登录 | 按 id 查用户（密码字段被清空） | Path `id` | `User` |
| GET | `/user/{userId}/with-product/{productId}` | 需登录 | Feign 跨服务演示（用户+商品） | Path `userId`、`productId` | `{ user, product }` |

### 3.2 收货地址 `/user/address`（全部需登录，含越权校验）
| 方法 | 路径 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|
| GET | `/user/address` | 我的地址列表（默认地址置顶） | — | `List<Address>` |
| GET | `/user/address/{id}` | 地址详情 | Path `id` | `Address` |
| POST | `/user/address` | 新增地址（强制绑定当前用户） | `Address`（收货人/电话/省市区/详细地址…） | `Address` |
| PUT | `/user/address/{id}` | 修改地址 | Path `id` + `Address` | `Address` |
| DELETE | `/user/address/{id}` | 删除地址（逻辑删除） | Path `id` | — |
| PUT | `/user/address/default` | 设为默认地址 | Query `addressId` | — |

### 3.3 优惠券 `/coupon`
| 方法 | 路径 | 鉴权 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|---|
| POST | `/coupon` | 管理 | 创建券模板 | `Coupon` | 券 id |
| GET | `/coupon/available` | 公开 | 当前可领的券 | — | `List<Coupon>` |
| POST | `/coupon/{couponId}/receive` | 需登录 | 领券 | Path `couponId` | — |
| GET | `/coupon/mine` | 需登录 | 我的券 | — | `List<UserCouponVO>` |
| PUT | `/coupon/internal/use` | 内部 Feign | 下单用券（order 服务调） | `UseCouponDTO` | 抵扣金额 |
| PUT | `/coupon/internal/refund/{ucId}` | 内部 Feign | 退券 | Path `ucId` | — |

### 3.4 后台用户管理 `/admin/user`（管理员）
| 方法 | 路径 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|
| GET | `/admin/user/page` | 分页 + 条件查询 | Query `page,size,keyword?,status?,role?` | `IPage<User>` |
| PUT | `/admin/user/{id}/status` | 启用/禁用账号（禁用即时生效） | `{ status: 0\|1 }` | — |
| PUT | `/admin/user/{id}/password` | 重置密码 | `{ password }`（≥6 位） | — |
| GET | `/admin/user/stats` | 看板统计 | — | `{ totalUsers, todayNewUsers, oauthUsers }` |

### 3.5 后台角色与权限管理 `/admin/role` `/admin/permission`（管理员，G9 动态 RBAC）

> 动态 RBAC：`用户 ──(sys_user_role)── 角色 ──(sys_role_permission)── 权限(URL)`，三层均可后台配置。
> 配角色/配权限改的是关系表，**改完刷新 Redis**，网关读 Redis 动态判权、即时生效、无需重启。`sys_*` 表归 user 服务管理。

**角色管理 `/admin/role`**
| 方法 | 路径 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|
| GET | `/admin/role/page` | 角色分页 / 列表 | Query `page,size,keyword?` | `IPage<SysRole>` |
| POST | `/admin/role` | 新建角色 | `{ roleCode, roleName, description }` | 角色 id |
| PUT | `/admin/role/{id}` | 修改角色 | Path `id` + `{ roleName, description, status }` | — |
| DELETE | `/admin/role/{id}` | 删除角色（逻辑删） | Path `id` | — |
| GET | `/admin/role/{id}/permissions` | 查角色已配权限 | Path `id` | `List<Long>`（权限 id） |
| PUT | `/admin/role/{id}/permissions` | 给角色配权限（勾选，改完刷新 Redis） | 请求体直接是数组 `[permId...]` | — |
| GET | `/admin/role/user/{userId}` | 查用户已配角色（回显勾选框） | Path `userId` | `List<Long>`（角色 id） |
| PUT | `/admin/role/user/{userId}` | 给用户配角色（改完刷新该用户 Redis 钥匙串） | 请求体直接是数组 `[roleId...]` | — |

**权限管理 `/admin/permission`**
| 方法 | 路径 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|
| GET | `/admin/permission/list` | 全部权限列表（前端勾选用） | — | `List<SysPermission>` |

---

## 四、商品服务（product）

### 4.1 商品 `/product`
| 方法 | 路径 | 鉴权 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|---|
| GET | `/product` | 公开 | 商品分页列表（可按分类/关键词/分页查询） | Query 分页与筛选参数 | `IPage<Product>` |
| GET | `/product/{id}` | 公开 | 商品详情 | Path `id` | `Product` |
| GET | `/product/hot-search` | 公开 | 热门搜索词 | — | `List<Map>` |
| POST | `/product` | 内部/遗留 | 新增商品（后台请用 `/admin/product`） | `Product` | `Product` |
| PUT | `/product/{id}` | 内部/遗留 | 更新商品 | Path `id` + `Product` | `Product` |
| DELETE | `/product/{id}` | 内部/遗留 | 删除商品 | Path `id` | — |
| PUT | `/product/{id}/stock/deduct` | 内部 Feign | 扣库存 | Query `qty` | 剩余库存 |
| PUT | `/product/{id}/stock/restore` | 内部 Feign | 回补库存 | Query `qty` | 剩余库存 |
| PUT | `/product/{id}/internal/refresh-rating` | 内部 Feign | 刷新商品评分（review 服务调） | Path `id` | — |
| GET | `/product/internal/all` | 内部 Feign | 全量商品（search 同步 ES 用） | — | `List<Product>` |

### 4.2 商品分类 `/category`
| 方法 | 路径 | 鉴权 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|---|
| GET | `/category/list` | 公开 | 全部分类 | — | `List<Category>` |
| GET | `/category/{id}` | 公开 | 分类详情 | Path `id` | `Category` |
| POST | `/category` | 管理 | 新增分类 | `Category` | `Category` |
| PUT | `/category/{id}` | 管理 | 修改分类 | Path `id` + `Category` | `Category` |
| DELETE | `/category/{id}` | 管理 | 删除分类 | Path `id` | — |

### 4.3 收藏 `/favorite`（全部需登录）
| 方法 | 路径 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|
| POST | `/favorite/{productId}` | 收藏商品 | Path `productId` | — |
| DELETE | `/favorite/{productId}` | 取消收藏 | Path `productId` | — |
| GET | `/favorite/my` | 我的收藏 | — | `List<Product>` |
| GET | `/favorite/{productId}/exists` | 是否已收藏 | Path `productId` | `Boolean` |

### 4.4 后台商品管理 `/admin/product`（管理员）
| 方法 | 路径 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|
| GET | `/admin/product/page` | 分页 + 条件查询 | Query `AdminProductPageDTO` | `IPage<Product>` |
| POST | `/admin/product` | 新增商品 | `AdminProductSaveDTO`（name,categoryId,price,stock,…） | 商品 id |
| PUT | `/admin/product/{id}` | 编辑商品 | Path `id` + `AdminProductSaveDTO` | — |
| PUT | `/admin/product/{id}/status` | 上/下架 | `{ status: 0\|1 }` | — |
| DELETE | `/admin/product/{id}` | 删除商品 | Path `id` | — |
| GET | `/admin/product/stats` | 商品看板统计 | — | `Map` |

**AdminProductSaveDTO 字段**：`name`（必填≤100）、`categoryId`（必填）、`price`（必填>0）、`stock`（必填≥0）、`description?`、`detail?`、`coverImage?`、`status`（默认 0 下架）。

---

## 五、订单服务（order）

### 5.1 购物车 `/cart`（全部需登录）
| 方法 | 路径 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|
| GET | `/cart` | 我的购物车 | — | `List<CartItemVO>` |
| POST | `/cart` | 加入购物车 | `{ productId, quantity }` | — |
| PUT | `/cart/{id}` | 修改数量 | Path `id` + `{ quantity }` | — |
| DELETE | `/cart/{id}` | 删除购物车项 | Path `id` | — |

### 5.2 订单 `/order`（全部需登录）
| 方法 | 路径 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|
| POST | `/order` | 创建订单 | `{ addressId, cartItemIds:[], remark?, userCouponId? }` | `{ orderId, … }` |
| GET | `/order/my` | 我的订单列表 | — | `List<OrderListVO>` |
| GET | `/order/{orderId}` | 订单详情 | Path `orderId` | `OrderDetailVO` |
| PUT | `/order/{orderId}/cancel` | 取消订单 | Path `orderId` | — |
| POST | `/order/{orderId}/pay` | 订单支付（内部标记） | Path `orderId` | — |
| PUT | `/order/{orderId}/sign` | 确认签收 | Path `orderId` | — |

### 5.3 秒杀 `/seckill`
| 方法 | 路径 | 鉴权 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|---|
| POST | `/seckill/activity` | 管理 | 发布秒杀活动 | `{ productId, seckillPrice, stock, startTime, endTime }` | 活动 id |
| GET | `/seckill/activities` | 公开 | 秒杀活动列表 | — | `List<SeckillActivityVO>` |
| POST | `/seckill/preheat/{activityId}` | 管理 | 活动预热（库存进 Redis） | Path `activityId` | — |
| POST | `/seckill/{activityId}` | 需登录 | 抢购下单 | Path `activityId` | 排队/结果标识 |
| GET | `/seckill/result/{activityId}` | 需登录 | 查询抢购结果 | Path `activityId` | `Map` |
| POST | `/seckill/pay/{orderNo}` | 需登录 | 支付秒杀订单 | Path `orderNo` | — |

### 5.4 后台订单管理 `/admin/order`（管理员）
| 方法 | 路径 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|
| GET | `/admin/order/page` | 分页 + 条件查询 | Query `AdminOrderPageDTO` | `IPage<Orders>` |
| GET | `/admin/order/{id}` | 订单详情 | Path `id` | `OrderDetailVO` |
| PUT | `/admin/order/{id}/ship` | 发货 | Path `id` + 物流信息 | — |
| PUT | `/admin/order/{id}/close` | 关闭订单 | Path `id` | — |
| GET | `/admin/order/stats` | 订单看板统计 | — | `OrderStatsVO` |

---

## 六、评价服务（review）`/review`
| 方法 | 路径 | 鉴权 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|---|
| POST | `/review` | 需登录 | 发表评价 | 评价内容（productId/orderId/评分/内容…） | 评价 id |
| GET | `/review/product/{productId}` | 公开 | 某商品的评价列表 | Path `productId` | `List<ReviewVO>` |
| GET | `/review/user` | 需登录 | 我的评价 | — | `List<ReviewVO>` |
| GET | `/review/internal/stats/{productId}` | 内部 Feign | 商品评价统计（product 服务调） | Path `productId` | `ReviewStatsVO` |

---

## 七、搜索服务（search）`/search`
| 方法 | 路径 | 鉴权 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|---|
| GET | `/search/product` | 公开 | 商品搜索（ES） | Query `ProductSearchRequest`（keyword、分页、排序） | `PageResultVO<ProductSearchVO>` |
| POST | `/search/sync` | 管理/内部 | 全量同步商品到 ES | — | 同步条数 |
| POST | `/search/sync/{productId}` | 内部 | 同步单个商品 | Path `productId` | — |
| DELETE | `/search/{productId}` | 内部 | 从 ES 删除商品 | Path `productId` | — |

---

## 八、文件服务（file）`/file`
| 方法 | 路径 | 鉴权 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|---|
| POST | `/file/upload` | 需登录 | 上传文件到 MinIO（头像/商品图/凭证） | `multipart/form-data`，字段 `file` | `{ url, … }` |

---

## 九、支付服务（payment）

### 9.1 支付 `/pay`
| 方法 | 路径 | 鉴权 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|---|
| POST | `/pay/create` | 需登录 | 创建支付单（金额服务端查订单，防篡改） | `{ orderId, channel? }` | 支付跳转 URL / 表单 |
| GET | `/pay/status/{orderId}` | 需登录 | 查询支付状态 | Path `orderId` | `PayStatusVO` |
| POST | `/pay/notify/{channel}` | 公开（回调） | 支付渠道异步回调（验签+幂等） | Path `channel` + 渠道回调参数 | 渠道要求的响应 |

### 9.2 退款 `/refund`
| 方法 | 路径 | 鉴权 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|---|
| POST | `/refund/apply` | 需登录 | 申请退款（V1 全额，金额以支付单为准） | `{ orderId, reason }` | `Boolean` |

### 9.3 后台退款审批 `/admin/refund`（管理员）
| 方法 | 路径 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|
| GET | `/admin/refund/list` | 待审批退款列表 | — | `List<Refund>` |
| POST | `/admin/refund/approve/{refundId}` | 通过退款 | Path `refundId` | `Boolean` |
| POST | `/admin/refund/reject/{refundId}` | 驳回退款 | Path `refundId` | `Boolean` |

---

## 十、AI 客服服务（ai）`/ai`
| 方法 | 路径 | 鉴权 | 说明 | 请求体 / 参数 | 返回 `data` |
|---|---|---|---|---|---|
| POST | `/ai/chat` | 需登录 | 与 AI 客服对话（RAG 知识库 + 商品查询工具 + 多轮记忆，以 `X-User-Id` 隔离记忆） | `{ message }` | AI 回复文本 |

---

## 十一、两个前端各自使用的接口（对照）

| 前端 | `src/api/*.ts` 模块 | 覆盖的接口域 |
|---|---|---|
| **mini-mall-cloud-web**（C 端） | auth, user, address, cart, order, product, category, coupon, favorite, review, seckill, payment, file, ai | 面向买家：浏览/搜索、购物车、下单支付、退款申请、评价、收藏、优惠券、AI 客服 |
| **mini-mall-cloud-admin**（后台） | auth, user, product, category, order, refund, file, stats | 面向管理员：`/admin/**` 管理接口 + 登录 + 看板统计（`stats.ts` 聚合各服务 `/stats`） |

---

## 十二、调试 / 演示端点（非业务，前端勿调用）

以下为学习与压测阶段的验证接口，**不属于正式业务接口**：

| 路径 | 所在服务 | 用途 |
|---|---|---|
| `/hello`、`/hello/boom`、`/hello/bug` | user / order | 连通性与异常处理演示 |
| `/order/redis/**` | order | Redis 读写测试 |
| `/seata-test/**` | order | Seata 分布式事务测试 |
| `/product/flaky` | product | Sentinel 熔断降级演示 |
| `/user/internal/**`、`/order/internal/**` 等 | 各服务 | 服务间 Feign 内部调用，非对外接口 |

---

## 十三、项目模块与包功能

> 看接口前先看这张"地图"：知道每个接口背后的代码在哪个模块、哪个包，出问题才知道去哪找。

### 13.1 公共模块 `mini-mall-common`（各服务共用，不单独部署）

| 子模块 | 包路径 | 关键组件 | 功能 |
|---|---|---|---|
| **common-core** | `com.minimall.common.core` | `Result`、`BusinessException`、`GlobalExceptionHandler`(`@RestControllerAdvice`)、`SecurityContextHolder`、`HeaderInterceptor` | **统一响应体 + 全局异常兜底 + 上下文透传**。业务抛 `BusinessException` 自动转成 `Result{code,message}`；`HeaderInterceptor` 把网关注入的 `X-User-Id` 头塞进 `ThreadLocal`，Controller 用 `SecurityContextHolder.getUserId()` 取 |
| **common-redis** | `com.minimall.common.redis` | `RedisConfig`(`@Configuration`)、`RedisService`(`@Service`) | Redis 序列化配置 + 封装好的缓存操作工具 |
| **common-security** | `com.minimall.common.security` | `JwtUtil`(`@Component`)、`SecurityAutoConfiguration`(`@AutoConfiguration`)、`FeignAuthInterceptor` | **JWT 签发/解析** + Feign 调用时透传登录态。用 `@AutoConfiguration`（Spring Boot 标准自动装配，比 `@ComponentScan` 更规范） |
| **common-swagger** | `com.minimall.common.swagger` | `SwaggerConfig`(`@Configuration`) | 统一 Knife4j 文档配置（标题 + JWT Bearer 鉴权方案），业务服务引入即生效 |

> **公共 Bean 怎么被各服务加载**：业务服务启动类写 `@ComponentScan("com.minimall")`，把扫描范围从自己包上提到 `com.minimall`，于是 core/redis/swagger 里带注解的组件都被扫到（security 走 `@AutoConfiguration` 不依赖扫描）。`Result`/`BusinessException` 这类普通 POJO 不进容器，直接 import 使用。

### 13.2 业务服务（每个独立部署，注册到 Nacos）

| 服务 | 内部端口 | 负责的接口前缀 | 功能概述 |
|---|---|---|---|
| **mini-mall-gateway** | 9080 | 全部（唯一入口） | 路由转发 + JWT 鉴权（注入 `X-User-Id`）+ Sentinel 限流 + Knife4j 文档聚合 |
| **mini-mall-auth** | — | `/auth` | 登录/注册、邮箱验证码、GitHub OAuth、登出黑名单（不直连 DB，Feign 调 user） |
| **mini-mall-user** | 9001 | `/user` `/coupon` `/admin/user` | 用户资料、收货地址、优惠券、后台用户管理 |
| **mini-mall-product** | 9002 | `/product` `/category` `/favorite` `/admin/product` | 商品、分类、收藏、后台商品；含布隆过滤器 + Redis 缓存防击穿 |
| **mini-mall-order** | 9003 | `/cart` `/order` `/seckill` `/admin/order` | 购物车、订单、秒杀（Lua+MQ 异步落单）、后台订单；Redisson 分布式锁 |
| **mini-mall-review** | 9004 | `/review` | 商品评价 + 评分统计（Feign 回写 product 评分） |
| **mini-mall-search** | 9005 | `/search` | Elasticsearch 商品搜索；消费 MQ 同步商品变更到 ES |
| **mini-mall-file** | — | `/file` | MinIO 对象存储（头像/商品图/凭证上传） |
| **mini-mall-payment** | 9008 | `/pay` `/refund` `/admin/refund` | 支付宝支付、对账兜底、两段式退款审批 |
| **mini-mall-ai** | 9009 | `/ai` | AI 客服 Agent（LangChain4j + DeepSeek + RAG 知识库 + 商品查询工具 + 多轮记忆） |

---

## 十四、接口与文档相关注解速查

> 读源码时对着这张表，就能看懂每个接口是怎么"声明"出来的。

### 14.1 Web 层注解（Spring MVC，决定接口长什么样）

| 注解 | 用在哪 | 作用 | 本项目例子 |
|---|---|---|---|
| `@RestController` | 类 | 声明这是个返回 JSON 的控制器 | `ProductController` |
| `@RequestMapping("/product")` | 类 | 该类所有接口的**路径前缀** | 前缀 `/product` |
| `@GetMapping` `@PostMapping` `@PutMapping` `@DeleteMapping` | 方法 | 绑定 HTTP 方法 + 子路径 | `@GetMapping("/{id}")` |
| `@PathVariable` | 参数 | 取**路径**里的变量 | `/{id}` → `@PathVariable Long id` |
| `@RequestParam` | 参数 | 取 **URL 查询参数** `?qty=5` | `@RequestParam Integer qty` |
| `@RequestBody` | 参数 | 取 **JSON 请求体**，反序列化成 DTO | `@RequestBody CreateOrderDTO dto` |
| `@RequestHeader` | 参数 | 取**请求头** | `@RequestHeader("X-User-Id")` |
| `@Valid` / `@Validated` | 参数/类 | 触发 DTO 上的校验注解（不过就返 400） | 注册 `@Valid @RequestBody UserRegisterDTO` |

配合 `@Valid` 的**校验注解**（写在 DTO 字段上）：`@NotBlank`、`@NotNull`、`@Size(min,max)`、`@Pattern(regexp)`、`@Email`、`@Min`、`@DecimalMin`。

### 14.2 Swagger / Knife4j 文档注解（决定文档页显示什么）

| 注解 | 用在哪 | 作用 | 例子 |
|---|---|---|---|
| `@Tag(name, description)` | Controller 类 | 文档左侧的**分组名** | `@Tag(name="商品", …)` |
| `@Operation(summary, description)` | 方法 | 接口列表里的**标题/说明** | `@Operation(summary="商品分页搜索")` |
| `@Parameter(description)` | 参数 | 参数说明 | 标注 `id` 是什么 |
| `@Schema(description)` | DTO 字段 | 字段说明（在文档的"实体类"里显示） | 标注 `price` 含义 |
| `@ApiResponse` | 方法 | 描述某个返回码的含义 | 200/400 说明 |

> 这些注解**不写也能生成文档**（springdoc 会用方法名/字段名兜底），写了只是让文档有中文说明。目前仅 `ProductController` 做了示范。

### 14.3 Spring 装配 / 微服务注解（决定组件怎么被加载）

| 注解 | 作用 | 本项目哪里用 |
|---|---|---|
| `@ComponentScan("com.minimall")` | 把扫描范围上提，加载 common 公共组件 | 9 个业务服务启动类 |
| `@Configuration` + `@Bean` | 声明配置类、手动注册 Bean | `SwaggerConfig`、`RedisConfig` |
| `@Component` / `@Service` | 声明普通组件/服务 Bean | `JwtUtil`、`RedisService` |
| `@RestControllerAdvice` | 全局异常处理（跨所有 Controller） | `GlobalExceptionHandler` |
| `@AutoConfiguration` | Spring Boot 标准自动装配（比扫描更规范） | `SecurityAutoConfiguration` |
| `@FeignClient` | 声明式服务间调用客户端 | 各服务的 `*FeignClient` |
| `@MapperScan` | 扫描 MyBatis Mapper 接口 | 各服务启动类 |

---

_文档生成方式：扫描各服务 `@RestController` 的映射注解与方法签名整理而成。若后端接口有增改，请同步更新本文件。_
