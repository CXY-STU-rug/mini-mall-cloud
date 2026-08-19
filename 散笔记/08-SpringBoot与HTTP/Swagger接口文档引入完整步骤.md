# mini-mall-cloud Swagger(Knife4j) 接口文档 —— 全部依赖 / 代码 / 引入步骤

> 目标：把这套"代码即文档"的 Swagger 从零在一个 Spring Cloud 微服务项目里搭起来。
> 本项目用的是 **Knife4j**（国产增强 UI，底层就是 springdoc-openapi + OpenAPI 3），
> 最终效果：`http://localhost:9080/doc.html` 一个入口下拉切换看所有微服务接口，还能带 JWT 在线调试。

---

## 一、先搞懂：这套东西由哪几块组成

```
概念关系：
  OpenAPI 3   ── 一套"描述 HTTP 接口"的标准(JSON)，接口有哪些、参数、返回都写在里面
      │
  springdoc   ── 扫描你的 @RestController，自动生成上面那份 OpenAPI JSON( /v3/api-docs )
      │
  Knife4j     ── 在 springdoc 之上套了个更好用的中文 UI( /doc.html )，并提供"网关聚合"能力
```

```
本项目的物理结构：
  父 pom (版本锁 knife4j.version=4.5.0)
    │
    ├── mini-mall-common/
    │      └── mini-mall-common-swagger/     ← ★公共模块：写一次 SwaggerConfig，所有服务共用
    │             ├── pom.xml                (引 knife4j-openapi3 starter)
    │             └── SwaggerConfig.java     (OpenAPI Bean：标题 + JWT Bearer 鉴权方案)
    │
    ├── mini-mall-user / product / order ...  ← 9 个业务服务
    │      每个都做两件事：
    │        ① pom 引 mini-mall-common-swagger
    │        ② 启动类 @ComponentScan("com.minimall")  ← 让上面的 SwaggerConfig 被扫到
    │      → 于是每个服务自己的 http://服务端口/doc.html 就能用了
    │
    └── mini-mall-gateway/                    ← 网关：不引 common-swagger！
           走单独的 knife4j-gateway-starter，把 9 个服务的文档"聚合"到 9080/doc.html
```

**一句话记住分工**：
- 业务服务 = **生产**文档（各自扫自己的 Controller 生成 `/v3/api-docs`）
- 网关 = **聚合**文档（把大家的汇总到一个下拉框）

---

## 二、涉及的全部依赖清单

| 依赖 | 版本来源 | 用在哪 | 作用 |
|---|---|---|---|
| `knife4j-openapi3-jakarta-spring-boot-starter` | `${knife4j.version}` = 4.5.0 | common-swagger 模块 | 生成文档 + `/doc.html` UI（含 springdoc） |
| `knife4j-gateway-spring-boot-starter` | `${knife4j.version}` = 4.5.0 | 网关 | 聚合各服务文档到一个入口 |
| `mini-mall-common-swagger` | `${minimall.version}` | 各业务服务 | 本项目自建的公共封装，引它就自动带上第 1 个 + SwaggerConfig |

> 注意：`swagger-annotations`（`@Tag`/`@Operation` 那些注解）不用单独引，
> knife4j-openapi3 starter 里已经**传递依赖**带进来了。

---

## 三、从零引入的完整步骤

> 下面每一步都标了【文件路径】和【为什么】。照着做一遍，一套微服务的 Swagger 就通了。

### 步骤 0：父 pom 声明版本号

📄 `mini-mall-cloud/pom.xml` → `<properties>` 段

```xml
<properties>
    ...
    <knife4j.version>4.5.0</knife4j.version>   <!-- 统一在这里定版本，全项目引用 -->
    ...
</properties>
```

**为什么**：微服务几十个模块，版本必须"一处定义、处处引用"，否则各模块版本不一致会出诡异 bug。

---

### 步骤 1：父 pom 锁定依赖版本（dependencyManagement）

📄 `mini-mall-cloud/pom.xml` → `<dependencyManagement><dependencies>` 段

```xml
<!-- ① 业务服务生成文档用的 starter -->
<dependency>
    <groupId>com.github.xiaoymin</groupId>
    <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
    <version>${knife4j.version}</version>
</dependency>

<!-- ② 网关聚合专用: 让 9080/doc.html 汇总所有微服务的文档 (只有网关引它) -->
<dependency>
    <groupId>com.github.xiaoymin</groupId>
    <artifactId>knife4j-gateway-spring-boot-starter</artifactId>
    <version>${knife4j.version}</version>
</dependency>

<!-- ③ 本项目自建的公共 swagger 封装 -->
<dependency>
    <groupId>com.minimall</groupId>
    <artifactId>mini-mall-common-swagger</artifactId>
    <version>${minimall.version}</version>
</dependency>
```

**为什么**：`dependencyManagement` 只"锁版本、不引入"。子模块真正引用时**不用写 version**，保证全项目一致。

---

### 步骤 2：建公共模块 mini-mall-common-swagger

这是整套方案的核心 —— **配置写一次，所有服务共用**。

#### 2.1 在 common 聚合层登记子模块

📄 `mini-mall-cloud/mini-mall-common/pom.xml`

```xml
<modules>
    <module>mini-mall-common-core</module>
    <module>mini-mall-common-redis</module>
    <module>mini-mall-common-security</module>
    <module>mini-mall-common-swagger</module>   <!-- ← 登记进来才会被编译 -->
</modules>
```

#### 2.2 公共模块自己的 pom

📄 `mini-mall-cloud/mini-mall-common/mini-mall-common-swagger/pom.xml`

```xml
<parent>
    <groupId>com.minimall</groupId>
    <artifactId>mini-mall-common</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</parent>
<artifactId>mini-mall-common-swagger</artifactId>

<dependencies>
    <!-- 只引这一个，version 由父 pom 锁 -->
    <dependency>
        <groupId>com.github.xiaoymin</groupId>
        <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
    </dependency>

    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

#### 2.3 公共配置类（重点看注释）

📄 `.../mini-mall-common-swagger/src/main/java/com/minimall/common/swagger/config/SwaggerConfig.java`

```java
package com.minimall.common.swagger.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 公共 Swagger / Knife4j 配置(所有业务服务统一使用)。
 * 业务服务只要 pom 引 mini-mall-common-swagger + 启动类 @ComponentScan("com.minimall")
 * 就会自动扫到这个配置，不用各服务重复写。
 * 启动后访问: http://127.0.0.1:{port}/doc.html
 */
@Configuration
public class SwaggerConfig {

    // 取当前服务的 spring.application.name 当文档标题，这样每个服务标题不同
    @Value("${spring.application.name:mini-mall-cloud}")
    private String appName;

    @Bean
    public OpenAPI miniMallOpenAPI() {
        // ① 文档头部信息(标题/描述/版本/联系人)
        Info info = new Info()
                .title(appName + " API 文档")
                .description("mini-mall-cloud 微服务电商 API (基于 Knife4j + OpenAPI 3)")
                .version("0.0.1-SNAPSHOT")
                .contact(new Contact().name("...").url("..."))
                .license(new License().name("MIT"));

        // ② 定义 JWT Bearer 鉴权方案(HTTP type + bearer scheme + JWT format)
        //    有了它，doc.html 右上角就能填 token，在线调试需登录的接口
        SecurityScheme jwtScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT token(登录后拿)");

        // ③ 组装: info + 注册鉴权方案(名字叫 "Bearer") + 全局默认要求鉴权
        return new OpenAPI()
                .info(info)
                .components(new Components().addSecuritySchemes("Bearer", jwtScheme))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"));
    }
}
```

**为什么要有这个类**：不写它文档也能生成，但没有标题、没有 JWT 输入框。写了它，全项目文档统一风格 + 能带 token 调试。

---

### 步骤 3：每个业务服务接入（两处改动）

以 product 为例，其余 user/order/review/search/auth/file/payment/ai 完全一样。

#### 3.1 pom 引公共模块

📄 `mini-mall-product/pom.xml`

```xml
<!-- ⭐ 公共 Swagger / Knife4j (OpenAPI Bean + JWT Bearer 鉴权方案) -->
<dependency>
    <groupId>com.minimall</groupId>
    <artifactId>mini-mall-common-swagger</artifactId>   <!-- 不写 version, 父 pom 已锁 -->
</dependency>
```

#### 3.2 启动类扫描范围扩到 com.minimall

📄 `mini-mall-product/.../MiniMallProductApplication.java`

```java
@SpringBootApplication
@ComponentScan("com.minimall")          // ★关键！默认只扫 com.minimall.product，
                                        //   扩到 com.minimall 才能扫到
                                        //   com.minimall.common.swagger 里的 SwaggerConfig
@MapperScan("com.minimall.product.mapper")
public class MiniMallProductApplication { ... }
```

**为什么是关键**：`@SpringBootApplication` 默认只扫描**启动类所在包及子包**（`com.minimall.product`）。
`SwaggerConfig` 在 `com.minimall.common.swagger` 包，不在其下。
所以必须把扫描范围**上提**到 `com.minimall`，两个包才都被覆盖。
> 本项目 9 个业务服务全部都写了 `@ComponentScan("com.minimall")`，顺带也把 common-core 的全局异常处理器一起扫进来了。

做完 3.1 + 3.2，**单个服务的文档就能用了**：启动 product，访问 `http://localhost:9002/doc.html`。

---

### 步骤 4：网关聚合（让一个入口看全部）★本次新增的部分

网关**不引** `common-swagger`（它是 WebFlux，扫不了也不该扫 MVC 的 Config）。
它引一个专门的聚合 starter，通过 Nacos 找到所有服务的 `/v3/api-docs` 汇总起来。

#### 4.1 网关 pom 引聚合 starter

📄 `mini-mall-gateway/pom.xml`

```xml
<!-- ⭐ Knife4j 网关聚合 (WebFlux 版):
     业务服务引的是 common-swagger(各自生成文档)，
     网关只引这个聚合 starter: 从 Nacos 发现各服务, 汇总到 9080/doc.html 一个入口 -->
<dependency>
    <groupId>com.github.xiaoymin</groupId>
    <artifactId>knife4j-gateway-spring-boot-starter</artifactId>
</dependency>
```

#### 4.2 网关配置聚合方式

📄 `mini-mall-gateway/src/main/resources/application.yml`

```yaml
knife4j:
  gateway:
    enabled: true
    strategy: discover          # discover=从 Nacos 自动发现服务(推荐)；另有 manual 手动列
    discover:
      enabled: true
      version: openapi3         # 各服务用的是 OpenAPI3
      excluded-services:        # 网关自己没业务接口，排除掉免得多一个空分组
        - mini-mall-gateway
```

**为什么用 discover**：它自动发现 Nacos 里已注册的服务，**以后新增微服务不用改这里**，自动出现在下拉框。
（manual 模式要手写每个服务的名字和路径，维护麻烦。）

#### 4.3 ★网关鉴权放行文档资源（最容易漏的一步）

📄 `mini-mall-gateway/.../filter/AuthGlobalFilter.java` → 白名单 `WHITE_LIST`

```java
// ⭐ Knife4j 聚合文档资源: 浏览器访问 9080/doc.html 时会拉这些静态资源与聚合的 api-docs,
//    它们没有 JWT, 必须放行, 否则文档页 401 打不开。
new WhitelistRule("/doc.html", null),
new WhitelistRule("/webjars", null),
new WhitelistRule("/v3/api-docs", null),
new WhitelistRule("/swagger-resources", null),
new WhitelistRule("/favicon.ico", null)
```

**为什么必须放行**：网关对所有请求做 JWT 校验，而打开文档页时浏览器要请求 `/doc.html`、`/v3/api-docs/...` 等一堆资源，这些请求没有 token。不放行 → 全被 401 拦下 → 文档页空白打不开。

---

### 步骤 5（可选）：给接口加中文说明

不加也能生成文档，但显示的是英文方法名。加了 `@Tag` / `@Operation` 就有中文描述。

📄 任意 Controller，例如 `ProductController.java`

```java
import io.swagger.v3.oas.annotations.Operation;   // 描述"单个接口"
import io.swagger.v3.oas.annotations.tags.Tag;    // 给"一组接口(整个Controller)"起名

@Tag(name = "商品", description = "商品浏览：分页搜索、详情、热搜")  // ← 类上：这一组接口的名字
@RestController
@RequestMapping("/product")
public class ProductController {

    @Operation(summary = "商品分页搜索", description = "按分类/关键词/价格区间分页查询")  // ← 方法上
    @GetMapping
    public Result<IPage<Product>> list(...) { ... }
}
```

**对应关系**：`@Tag` → doc.html 左侧分组名；`@Operation.summary` → 接口列表里那行标题。

---

## 四、验证步骤

```
1. 编译（不用启动，先确认依赖和代码没问题）
   在项目根目录执行：
   mvn -pl mini-mall-gateway -am compile -DskipTests
   看到 BUILD SUCCESS 即可

2. 起中间件（Nacos 必须有，聚合靠它发现服务）
   在 mini-mall 目录：docker compose --profile cloud up -d

3. 启动网关 + 任意业务服务（如 product）

4. 浏览器打开统一入口：
   http://localhost:9080/doc.html
   → 左上角下拉框能切换看到已启动服务的接口分组

   单服务文档(不经网关)：
   http://localhost:9002/doc.html   (product 端口)
```

**带 token 调试**：doc.html 右上角"Authorize / 文档管理→全局参数" → 填
`Authorization = Bearer <你的JWT>` → 之后所有请求自动带上。

---

## 五、常见坑（踩过的）

| 现象 | 原因 | 解决 |
|---|---|---|
| 服务的 `/doc.html` 打不开 / 接口是空的 | 启动类没把扫描范围扩到 `com.minimall` | `@ComponentScan("com.minimall")` |
| 经网关 `9080/doc.html` 打开是空白 / 401 | 网关白名单没放行文档资源 | 步骤 4.3 加白名单 |
| 网关下拉框里看不到某服务 | discover 只显示**已注册到 Nacos 且已启动**的服务 | 把那个服务起起来 |
| 网关引了 common-swagger 后启动报错 | 网关是 WebFlux，common-swagger 是 MVC 版 | 网关**不引** common-swagger，只引 gateway-starter |
| 版本冲突 | 各处 version 写得不一致 | 统一用父 pom `${knife4j.version}` |

**安全提醒**：`/v3/api-docs` 放行后，任何人都能看到接口结构。
**生产环境**应把网关 `knife4j.gateway.enabled` 设为 `false`，或对文档路径加访问保护（如内网限制 / Basic Auth）。

---

## 六、访问地址速查

| 用途 | 地址 |
|---|---|
| 聚合入口（看全部服务） | http://localhost:9080/doc.html |
| 单服务文档 | http://localhost:{服务端口}/doc.html |
| 原始 OpenAPI JSON | http://localhost:{服务端口}/v3/api-docs |

服务端口：user 9001 / product 9002 / order 9003 / review 9004 / search 9005 / payment 9008 / ai 9009 / 网关 9080。

---

## 七、涉及文件总清单（改动/新增一眼看全）

| 文件 | 改动内容 |
|---|---|
| `pom.xml`(父) | properties 定 `knife4j.version`；dependencyManagement 锁 3 个依赖 |
| `mini-mall-common/pom.xml` | modules 登记 `mini-mall-common-swagger` |
| `mini-mall-common-swagger/pom.xml` | 引 knife4j-openapi3 starter |
| `mini-mall-common-swagger/.../SwaggerConfig.java` | OpenAPI Bean（标题 + JWT 方案） |
| 各业务服务 `pom.xml`(×9) | 引 `mini-mall-common-swagger` |
| 各业务服务 `*Application.java`(×9) | `@ComponentScan("com.minimall")` |
| `mini-mall-gateway/pom.xml` | 引 `knife4j-gateway-spring-boot-starter` |
| `mini-mall-gateway/.../application.yml` | 加 `knife4j.gateway`(discover) |
| `mini-mall-gateway/.../AuthGlobalFilter.java` | 白名单放行文档资源 |
| 任意 Controller（可选） | `@Tag` / `@Operation` 中文注解 |

## 八、项目模块与包功能（这套配置落在哪些模块）

> Swagger 不是孤立的，它跟着 common 公共模块的加载机制走。先看这张模块地图。

### 8.1 公共模块 `mini-mall-common`（各服务共用，不单独部署）

| 子模块 | 包路径 | 关键组件 | 功能 |
|---|---|---|---|
| **common-core** | `com.minimall.common.core` | `Result`、`BusinessException`、`GlobalExceptionHandler`(`@RestControllerAdvice`)、`SecurityContextHolder`、`HeaderInterceptor` | 统一响应体 + 全局异常兜底 + 上下文透传（把网关注入的 `X-User-Id` 塞进 ThreadLocal） |
| **common-redis** | `com.minimall.common.redis` | `RedisConfig`(`@Configuration`)、`RedisService`(`@Service`) | Redis 序列化配置 + 缓存操作工具 |
| **common-security** | `com.minimall.common.security` | `JwtUtil`(`@Component`)、`SecurityAutoConfiguration`(`@AutoConfiguration`)、`FeignAuthInterceptor` | JWT 签发/解析 + Feign 透传登录态。用 `@AutoConfiguration` 装配 |
| **common-swagger** | `com.minimall.common.swagger` | `SwaggerConfig`(`@Configuration`) | ★本笔记主角：统一 Knife4j 文档配置（标题 + JWT 鉴权方案） |

### 8.2 ★关键：`@ComponentScan("com.minimall")` 到底扫什么

步骤 3.2 里各服务都写了这行，它是 `SwaggerConfig`（以及 core/redis 组件）能被加载的原因。但它**不是"扫描整个 com.minimall 世界的所有代码"**，准确机制是：

```
@ComponentScan("com.minimall") 实际扫的 =
        包名以 com.minimall 开头      （你写的过滤条件）
                  ∩（交集）
        classpath 上真实存在的 .class （由 pom 依赖 + 自身代码决定）
```

以 product 服务为例，它的 classpath 上有一堆 jar，但只有**包名匹配**的才被扫：

```
com.minimall.product.*        ← 自己的代码       ✅ 扫
com.minimall.common.core.*    ← common 依赖       ✅ 扫
com.minimall.common.swagger.* ← common 依赖       ✅ 扫（SwaggerConfig 在这）
──────────────────────────────────────────────
com.baomidou.mybatisplus.*    ← MyBatis-Plus 依赖  ❌ 包名不匹配，不扫
org.springframework.*         ← Spring 依赖        ❌ 不扫
com.alibaba.nacos.*           ← Nacos 依赖         ❌ 不扫
```

**三个结论**：
1. 扫的是"**本服务 classpath 里** + **包名属于 com.minimall**"的类——不是所有代码，也不是所有依赖。
2. product 的 classpath 里根本没有 order/user 的类（微服务独立打包、互不依赖），所以**扫不到别的服务**，不存在"越扫越慢"。
3. 第三方依赖（MyBatis-Plus/Nacos）虽然也在 classpath 上，但包名不匹配被挡在外面，它们靠自己的 `@AutoConfiguration` 装配。所以包名**不能写太宽**——写成 `@ComponentScan("com")` 才会把第三方全纳入，真的变慢。

> 引申：`common-security` 就没靠 ComponentScan，而是用 `@AutoConfiguration` +
> `META-INF/spring/...imports` 文件装配（Spring Boot Starter 的标准做法，更规范、不依赖扫描范围）。
> swagger/redis 理论上也能改成这种方式，那样连 `@ComponentScan` 都不用扩范围了。

### 8.3 网关为什么不引 common-swagger

网关是 **WebFlux**（响应式），而 `SwaggerConfig`/common 里不少是 **MVC**（Servlet）风格的配置，扫进去会冲突。所以网关：
- 启动类的 `@ComponentScan` 用 `excludeFilters` **排除**掉这些 MVC 配置；
- 不引 `common-swagger`，改引 `knife4j-gateway-starter`，只做**聚合**不做生成。

---

## 九、接口与文档相关注解速查

> 加完这套 Swagger 后，读 Controller 源码时对着这张表，就能看懂接口是怎么"声明"出来的。

### 9.1 Swagger / Knife4j 文档注解（决定文档页显示什么）

| 注解 | 用在哪 | 作用 |
|---|---|---|
| `@Tag(name, description)` | Controller 类 | 文档左侧的**分组名** |
| `@Operation(summary, description)` | 方法 | 接口列表里的**标题/说明** |
| `@Parameter(description)` | 参数 | 参数说明 |
| `@Schema(description)` | DTO 字段 | 字段说明（文档"实体类"里显示） |
| `@ApiResponse` | 方法 | 描述某个返回码的含义 |

> 不写也能生成文档（springdoc 用方法名/字段名兜底），写了只是加中文说明。这些注解来自 `swagger-annotations`，由 knife4j-openapi3 starter 传递引入，**不用单独加依赖**。

### 9.2 Web 层注解（决定接口长什么样）

| 注解 | 作用 |
|---|---|
| `@RestController` | 声明返回 JSON 的控制器 |
| `@RequestMapping("/product")` | 类级**路径前缀** |
| `@GetMapping` `@PostMapping` `@PutMapping` `@DeleteMapping` | 绑定 HTTP 方法 + 子路径 |
| `@PathVariable` | 取路径变量 `/{id}` |
| `@RequestParam` | 取查询参数 `?qty=5` |
| `@RequestBody` | 取 JSON 请求体 → DTO |
| `@RequestHeader` | 取请求头（如 `X-User-Id`） |
| `@Valid` / `@Validated` | 触发 DTO 校验（不过返 400） |

### 9.3 Spring 装配注解（决定组件怎么被加载）

| 注解 | 作用 | 本项目哪里用 |
|---|---|---|
| `@ComponentScan("com.minimall")` | 按包名过滤 classpath，加载 common 公共组件（见 8.2） | 9 个业务服务启动类 |
| `@Configuration` + `@Bean` | 声明配置类、手动注册 Bean | `SwaggerConfig`、`RedisConfig` |
| `@Component` / `@Service` | 声明普通组件/服务 Bean | `JwtUtil`、`RedisService` |
| `@RestControllerAdvice` | 全局异常处理 | `GlobalExceptionHandler` |
| `@AutoConfiguration` | Spring Boot 标准自动装配（比扫描更规范） | `SecurityAutoConfiguration` |
| `@FeignClient` | 声明式服务间调用 | 各服务 `*FeignClient` |
| `@MapperScan` | 扫描 MyBatis Mapper 接口 | 各服务启动类 |

---

_本笔记基于 mini-mall-cloud 现有代码整理（knife4j 4.5.0 / Spring Boot 3.3.5 / Spring Cloud 2023.0.3）。_
