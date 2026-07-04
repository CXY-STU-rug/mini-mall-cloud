# mini-mall-cloud C 端商城完整规划

> 这份文档是「下次新 session 接着干」的入口。读完它任何新 Claude session 都能从 WEB.0 开始一路干到 WEB.6 完整 MVP,无需再问决策。
>
> 更新日期:2026-06-26 · 决策版

---

## 一、定好的方向(用户决策)

| 项 | 决策 |
|---|---|
| 视觉风格 | **电商主流红**(京东/天猫风)— 主色 `#E1251B`,辅 `#FF6700`(秒杀橙),Element Plus 的 primary 覆盖到电商红 |
| 购物车策略 | **走后端 Redis** — 复用 G3.4 已有的 `/cart/**` 接口,前端不存 |
| 项目位置 | `mini-mall-cloud-web/`(新建,与 `mini-mall-cloud-admin` 平级) |
| 技术栈 | Vue3 + Vite + TypeScript + Pinia + Vue Router 4 + Element Plus + axios |
| 一口气推进范围 | **WEB.0~WEB.6 MVP 7 项一气呵成**(下次 session 主目标) |
| 后续 | WEB.7~9(完善)+ WEB.10~11(质量)分二次 session |

---

## 二、电商红配色规范(下次直接抄)

```css
:root {
  /* 主色 */
  --primary: #E1251B;        /* 京东红 / 主按钮 / 高亮 */
  --primary-dark: #C0392B;   /* 按钮悬停 */
  --primary-light: #FFF1F0;  /* 主色浅底,卡片背景 */

  /* 辅助 */
  --accent: #FF6700;         /* 秒杀橙 / 限时倒计时 / 价格标签 */
  --accent-bg: #FFF7E6;
  --warning: #FAAD14;        /* 限时 / 倒计时 */

  /* 中性 */
  --text-primary: #1F1F1F;
  --text-regular: #595959;
  --text-secondary: #8C8C8C;
  --text-placeholder: #BFBFBF;

  /* 背景 */
  --bg-page: #F5F5F5;        /* 页面整体浅灰底 */
  --bg-white: #FFFFFF;       /* 卡片白底 */
  --bg-header: #FFFFFF;      /* 顶部白 */

  /* 边框 */
  --border: #EEEEEE;
  --border-light: #F0F0F0;

  /* 价格 */
  --price: #E1251B;          /* 价格永远红色 */
  --price-original: #999;    /* 划线原价 */
}
```

**Element Plus 覆盖**(在 `main.ts` 或全局 css):
```css
.el-button--primary {
  --el-button-bg-color: #E1251B;
  --el-button-border-color: #E1251B;
  --el-button-hover-bg-color: #C0392B;
  --el-button-hover-border-color: #C0392B;
}
```

**字体**:沿用 admin 那套 system-ui,但页面整体放宽一档(`font-size: 14px` 而不是 12px,京东风字号偏大)。

---

## 三、整体路由结构(预先定死)

```
/                       首页(轮播 + 分类 + 推荐商品 + 秒杀入口)
/login                  登录
/register               注册
/category/:id           分类页(分类下商品列表)
/search?keyword=xxx     搜索结果页
/product/:id            商品详情页

/cart                   购物车
/checkout               下单确认(选地址 / 选优惠券 / 提交订单)
/pay/:orderNo           支付页(模拟支付,确认按钮)

/user                   个人中心(布局,左侧菜单 + 右侧 RouterView)
  /user/profile         我的资料(头像 + 昵称 + 手机邮箱)
  /user/orders          我的订单(列表 + 详情)
  /user/orders/:id      订单详情
  /user/address         收货地址管理
  /user/coupons         我的优惠券
  /user/favorites       我的收藏
  /user/reviews         我的评价
```

---

## 四、分阶段任务清单

### WEB.0 — 项目脚手架(下次 session 第一步)

**目标**:`npm create vite@latest mini-mall-cloud-web -- --template vue-ts` 跑通后,把 admin 那套基础配置抄过来。

**要做的事**:
1. `npm create vite` 建项目
2. 装依赖:
   ```bash
   npm i element-plus @element-plus/icons-vue pinia vue-router axios
   npm i -D @types/node unplugin-auto-import unplugin-vue-components
   ```
3. `vite.config.ts`:
   - `@` 别名指向 src
   - server proxy:`/api → http://localhost:9080`(网关)
   - port 5174(跟 admin 的 5173 错开)
4. `src/style.css` 引入电商红 CSS 变量
5. `src/main.ts` 注册 Pinia / Router / Element Plus + 全局主题色覆盖
6. `src/api/http.ts`:**直接抄 admin 那一份**,改下 baseURL 不动
7. `src/stores/user.ts`:**直接抄 admin 那一份**(token / userInfo / login / logout)
8. `src/router/index.ts`:先建空骨架 + 路由守卫(未登录 + 受保护路由跳 /login)

**完成标志**:`npm run dev` 启动,浏览器进 `localhost:5174` 显示空白页(没报错就行)。

---

### WEB.1 — 公共布局(Header / Footer + 路由守卫)

**目标**:做一个京东风的顶栏 + 底栏,所有页面共用。

**要做的事**:
1. `src/layouts/MainLayout.vue`(布局根组件)
   ```
   ┌─ TopBar (顶部 28px 高,白底): 左「欢迎来到 mini-mall」 右「登录/注册 | 我的订单 | 客服」
   ├─ Header (中间 80px 高): 左 logo, 中间搜索框, 右购物车图标 + 数量
   ├─ NavMenu (40px 高, 红底): 一排分类入口横滚
   ├─ <RouterView />  ← 内容
   └─ Footer (灰底, 友情链接 + 备案信息)
   ```
2. `src/layouts/UserLayout.vue`(个人中心布局,左侧菜单 + 右侧)
3. `src/components/CartIcon.vue`:右上角购物车图标 + 红色徽章数字
4. 全局购物车计数 store(`stores/cart.ts`,只存数量,具体明细按需拉接口)
5. 路由守卫:`/user/*`、`/cart`、`/checkout`、`/pay/*` 必须登录

**接口依赖**:
- `GET /cart/count`(后端要确认有,没有的话 ADMIN 阶段就该加;先看 order 服务 CartController 接口)

---

### WEB.2 — 登录 / 注册

**目标**:用户能用 admin 同款 JWT 登录,只是限制是不让 admin 进 C 端(role=1 进 C 端没意义)。

**要做的事**:
1. `views/Login.vue`:用户名 + 密码 + 登录按钮(京东风,黑底 + 红主按钮,居中卡片)
2. `views/Register.vue`:用户名(校验唯一)+ 手机 + 密码 + 确认密码
3. `api/auth.ts`:抄 admin 的,加一个 `register()`
4. 登录后 token 写 Pinia + localStorage,跳回原来想去的页面(用 query 参数记 `?redirect=/cart`)

**接口依赖**(已有,不动):
- `POST /auth/login`
- `POST /user/register`(待确认接口名,看 user 服务)

---

### WEB.3 — 商品首页 + 搜索 + 分类(MVP 核心 1)

**目标**:用户进站能浏览。

**要做的事**:
1. `views/Home.vue`:
   - 顶部 banner(简单 el-carousel,3 张占位图)
   - 「精选分类」一排 8 个分类图标
   - 「热销商品」商品卡片网格(4 列)— 调 `/product/page` 不带筛选拿前 12 条
2. `views/Category.vue`(路径 `/category/:id`):
   - 左侧分类侧栏(树状,el-tree 或 el-menu)
   - 右侧商品网格,筛选 categoryId
3. `views/Search.vue`(路径 `/search`):
   - 复用 Category 的网格组件
   - 调 search 服务 `/search/product?keyword=xxx`(已有 ES 索引)
4. `components/ProductCard.vue`:
   - 商品卡 = 封面图 + 商品名(2 行截断)+ 价格(红色大字 + 划线原价)+ 销量
   - 点击进详情
5. `api/product.ts` / `api/category.ts`:抄 admin 那一份,改下接口路径(去掉 `/admin/` 前缀,用公开的 `/product/page` 等)

**接口依赖**(已有,不动):
- `GET /product/page?page=1&size=12&categoryId=xxx&keyword=xxx`
- `GET /category/list`
- `GET /search/product?keyword=xxx`(search 服务)

---

### WEB.4 — 商品详情页(MVP 核心 2)

**目标**:用户能看清商品再决定加购。

**要做的事**:
1. `views/Product.vue`(路径 `/product/:id`):
   - 左侧:大图区(封面图 + 缩略图切换,如果只有 1 张就只显示封面)
   - 右侧:
     - 商品名(大字)
     - 价格区(红色超大字)
     - 库存(数字)+ 销量
     - 数量选择器(`el-input-number` 配数量)
     - 「加入购物车」(红色按钮)+「立即购买」(橙色按钮)
   - 下方:
     - tab:商品详情(html 渲染商品 description 字段)/ 评价区(分页拉评价列表)
2. `api/product.ts` 加 `getProduct(id)`
3. 加购按钮:调 `/cart/add` 接口,提示成功后右上角购物车数字 +1
4. 立即购买:加购后直接跳 `/checkout?productIds=xxx&qty=n`

**接口依赖**:
- `GET /product/{id}`(已有)
- `POST /cart/add` { productId, quantity }(已有,G3.4)
- `GET /review/product/{productId}?page=1&size=10`(已有,G7)

---

### WEB.5 — 购物车(MVP 核心 3)

**目标**:用户管理已加购的商品,确认数量,准备下单。

**要做的事**:
1. `views/Cart.vue`(路径 `/cart`):
   - 列表:复选框 | 商品图 | 商品名 | 单价 | 数量 +/- | 小计 | 删除
   - 底部:全选 + 已选数量 + 合计 + 结算按钮(红色)
2. `api/cart.ts`:
   - `getCart()` → 列表
   - `updateQuantity(productId, qty)` → 改数量
   - `removeItem(productId)`
   - `clearCart()`
3. 选中状态在前端 ref 保存(后端 cart 不存「选中」状态),提交结算时把选中的 productIds 传给 checkout 页

**接口依赖**(已有 G3.4):
- `GET /cart/list`
- `PUT /cart/update`
- `DELETE /cart/remove/{productId}`

---

### WEB.6 — 下单 + 收货地址(MVP 核心 4)

**目标**:用户能从购物车走完整条「确认订单 → 提交 → 模拟支付」流程。

**要做的事**:
1. `views/Checkout.vue`(路径 `/checkout`):
   - 顶部:收货地址区(默认地址 + 「更换地址」按钮 → 弹窗选)
   - 中部:商品清单(从购物车选中的,不可编辑)
   - 下部:
     - 备注输入
     - 优惠券选择(可选,先支持「不使用」+ 「自动选最优」)
     - 合计金额
     - 「提交订单」红色大按钮
2. `views/Pay.vue`(路径 `/pay/:orderNo`):
   - 显示订单号 + 金额
   - 选择支付方式(单选:微信 / 支付宝,**都是 mock**)
   - 「确认支付」按钮 → 调 mock 支付接口 → 跳订单详情
3. **收货地址管理**(可以放 WEB.8 也可以在这里做最简版):
   - 弹窗:列出已有地址(单选)
   - 「+ 新增地址」打开表单(收货人 + 手机 + 省市区 + 详细地址)
4. `api/address.ts` / `api/order.ts`:
   - `listAddress()` / `createAddress()` / `setDefault(id)` / `deleteAddress(id)`
   - `createOrder({ items, addressId, couponId, remark })`
   - `payOrder(orderNo, method)`(mock)

**接口依赖**:
- 待确认:user 服务有没有 `/user/address/**`(看 G3.x)— **如果没有要先补这个接口,这是 WEB.6 的前置**
- `POST /order/create`(已有,G3.7)
- mock 支付:让 order 服务加一个 `POST /order/pay/{orderNo}` 接口,直接改状态为已付款(不接真支付)

---

## 五、MVP 完成后的(WEB.7~11)

### WEB.7 我的订单

- `/user/orders`:列表 + 状态 tab(全部 / 待付款 / 待发货 / 待收货 / 已完成)
- 每行:订单号 + 商品摘要 + 总价 + 状态 + 操作(待付款 → 去支付 / 取消;待收货 → 确认收货)
- `/user/orders/:id`:订单详情 + 物流模拟 + 评价入口

### WEB.8 个人中心

- 头像上传(**复用 FILE 阶段 /file/upload**,bizType=avatar)
- 我的资料 / 收货地址 / 我的优惠券 / 我的收藏

### WEB.9 评价

- 已完成订单 → 「写评价」入口
- 评价页:打分(星)+ 文字 + 图片(再次复用 /file/upload,bizType=review)
- 商品详情页的评价 tab 显示

### WEB.10 秒杀

- 首页秒杀入口卡片(显示当前活动 + 倒计时)
- `/seckill/list`:秒杀活动列表 + 进度条
- 接 G3.8 现有秒杀接口

### WEB.11 收藏 + 优惠券

- 商品详情 / 列表卡上加「♡」收藏按钮
- 用户中心查看收藏列表
- 首页 / 详情显示可领券,「领取」按钮

---

## 六、接口缺口预审(已审,2026-06-26)

**结论:MVP(WEB.0~6)后端接口几乎全齐**,只有 1 个真缺口 + 1 个待 WEB.6 时确认 + 1 个绕过。

| 缺口 | 严重度 | 说明 | 怎么补 |
|---|---|---|---|
| 注册不收手机号/昵称 | ⚠️ **真缺口** | UserRegisterDTO 只有 username + password,AuthController.register 也只 set 这俩。但 WEB.2 表单要「用户名 + 手机 + 密码」 | UserRegisterDTO 加 phone(可选 nickname);AuthController.register() 里 newUser.setPhone(dto.getPhone()) |
| 地址「设默认」 | 🔶 待确认 | AddressController 只有标准 CRUD,没 setDefault。需确认 Address 实体有没有 isDefault、新增时能否设默认且清掉旧默认 | WEB.6 做地址时一起看,现在不阻塞 |
| `/cart/count` | ✅ 已绕过 | 没有这个接口 | 用 GET /cart 列表长度替代,前端拿 list.length |

**已确认存在的接口(可直接用):**

- ✅ `POST /auth/login` 登录
- ✅ `POST /auth/register`(走 auth 服务,不是 user 服务)
- ✅ `GET /user/info` 当前用户(读 X-User-Id)
- ✅ 待头像:看 WEB.8 时再确认 user 服务有没有更新头像的字段接口
- ✅ `/user/address/**` 收货地址 CRUD(WEB.6 用得着,但 setDefault 单独看)
- ✅ `/product/page` 商品分页
- ✅ `/product/{id}` 商品详情
- ✅ `/category/list` 分类
- ✅ `/search/product?keyword=` 搜索(ES)
- ✅ `/cart/**` 购物车 CRUD
- ✅ `POST /order/create` 下单
- ✅ `POST /order/{orderId}/pay` mock 支付(注释明确写「标记付款,本地模拟」)
- ✅ `/review/product/{id}` 评价列表
- ✅ `/favorite/**` 收藏(G3.5)
- ✅ `/coupon/**` 优惠券(G8)

**下次 session 开始的执行顺序调整:**

1. **WEB.0 前**:先去 user 服务改 UserRegisterDTO + AuthController.register,把 phone (nickname 可选) 加上 → 重启 auth + user → 用 curl 验证新字段生效。这是唯一的接口缺口,5 分钟搞定。
2. **WEB.6 时**:看 AddressEntity 有没有 isDefault 字段,有 → 直接用;没 → 加字段 + setDefault 接口(简单)。

如果再次预审(项目改过),命令:

```bash
grep -rn "@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping" \
  mini-mall-cloud/mini-mall-{user,product,order,review,search}/src/main/java \
  --include="*Controller.java" | grep -v "Admin"
```

---

## 七、下次 session 启动模板

复制下面整段发给新 Claude:

> 接着做 mini-mall-cloud C 端商城。规划文档在 `mini-mall-cloud/docs/C端商城_完整规划.md` 已写死所有决策(电商红 / 后端 Redis 购物车 / Vite 5174 / 项目位置 mini-mall-cloud-web/)。读完文档从 **WEB.0 脚手架** 开始,一口气干到 WEB.6 MVP 完成,中途遇到接口缺口先去 G 阶段补一个最小可用接口,不阻塞 C 端开发。每完成一个 WEB.X 给我看效果再继续下一个。
