# mini-mall-cloud E 阶段：Nacos 注册中心

> 接续 v8 (1~31 章) 的下一章节，承接 D 阶段网关后的服务发现能力。
> 完成日期：2026-06-19  |  与 v8.docx 配套阅读

---

## 32.0  E 阶段全景

### 32.0.1  E 阶段一句话目标

D 阶段建好了网关，但路由是写死的：

```yaml
uri: http://localhost:9001   # ← 硬编码 IP + 端口
```

E 阶段引入 **Nacos 注册中心**，让所有服务**自己报告"我在哪"**，gateway 和 Feign 通过服务名拿到地址，**告别硬编码**。

### 32.0.2  加 Nacos 前 vs 后（架构图）

**加 Nacos 之前**（D 阶段终态）：

```
gateway/application.yml:
  routes:
    - uri: http://localhost:9001   ← 写死
    - uri: http://localhost:9002   ← 写死

user/ProductFeignClient.java:
  @FeignClient(url="http://localhost:9002")   ← 写死

3 个痛:
  ① 服务要换端口 → 改 N 个文件
  ② user 想扩成多实例 → 没法配
  ③ user 挂了 → gateway 还往 9001 转 → 502 雪崩
```

**加 Nacos 之后**（E 阶段终态）：

```
                      ┌──────────────────────────┐
                      │      Nacos Server        │
                      │      (Docker, :8848)     │
                      │                          │
                      │  服务注册表:              │
                      │    mini-mall-user        │
                      │      → 192.168.x.x:9001  │
                      │    mini-mall-product     │
                      │      → 192.168.x.x:9002  │
                      │    mini-mall-gateway     │
                      │      → 192.168.x.x:9080  │
                      └──────┬───────────────────┘
                             ↑ 心跳 / 注册 / 订阅
              ┌──────────────┼──────────────┐
              │              │              │
          gateway        user-svc      product-svc
          uri:           @FeignClient   (被 Feign 调)
          lb://...       (name only)
```

### 32.0.3  E 阶段路线图（5 小步）

| 步骤 | 做什么 | 工作量 |
|---|---|---|
| **E0** | 装 + 启 Nacos Server（Docker 一行命令）| 10 分钟 |
| **E1** | 3 服务（user/product/gateway）加 nacos-discovery 依赖 + yml 配置 | 15 分钟 |
| **E2** | Gateway 路由 `uri:` 从 `http://` 改 `lb://` | 5 分钟 |
| **E3** | Feign 客户端去掉 `url=` 硬编码 | 5 分钟 |
| **E4** | 联调验证 | 10 分钟 |

---

## 32.1  基础：服务发现要解决什么问题

### 32.1.1  痛点：动态 IP + 多实例 + 故障

```
单体时代:
  一个 jar 跑在一台机器, IP+端口 写在配置文件就够了

微服务时代:
  N 个服务 × M 个实例 = N×M 个 IP+端口
  实例可能漂移(K8s 重调度)、扩缩容(凌晨自动加机器)、故障下线
  → 静态配置完全跟不上
```

### 32.1.2  服务发现三件套

| 角色 | 干啥 | 项目里谁来扮 |
|---|---|---|
| **服务提供者** | 启动时向注册中心报告自己 | user / product |
| **服务消费者** | 调用前问注册中心 "对方在哪" | gateway / user(调 product) |
| **注册中心** | 维护服务列表 + 心跳检测 + 推送变化 | Nacos |

### 32.1.3  注册中心选型对照

| 注册中心 | 出身 | 特点 |
|---|---|---|
| **Eureka** | Netflix | Spring Cloud 老牌，2.x 进入维护模式 |
| **Consul** | HashiCorp | 配置中心+服务发现，Go 写的 |
| **ZooKeeper** | Apache | CP 模型，Dubbo 常配 |
| **Nacos** | Alibaba | AP+CP 可切，服务发现+配置中心一体，行业首选 |

**项目选 Nacos**：跟 Spring Cloud Alibaba 完美集成，国内生态最广。

---

## 32.2  Nacos 是什么

### 32.2.1  Nacos 的 3 个核心能力

| 能力 | 干啥 | E 阶段用 |
|---|---|---|
| **服务发现** | 维护"哪个服务在哪些 IP:端口"的注册表 | ✅ 重点 |
| **配置中心** | 把 yml 放云端，改了实时推给所有实例 | F 阶段可选 |
| **DNS / 路由控制** | 高级流量切分（金丝雀/A-B）| 暂不用 |

### 32.2.2  Nacos 内部结构（简化版）

```
┌─────────────── Nacos Server (单机模式) ────────────────┐
│                                                         │
│  ① HTTP / gRPC 接口层                                  │
│     :8848  HTTP (API + 控制台 UI)                       │
│     :9848  gRPC (Nacos 2.x 必需, 长连接推送服务变化)    │
│                                                         │
│  ② 服务注册表 (内存中的 Map)                            │
│     {                                                   │
│       "mini-mall-user" : [{ip:192.168.x.x, port:9001}], │
│       "mini-mall-product" : [{ip:..., port:9002}],      │
│       "mini-mall-gateway" : [{ip:..., port:9080}]       │
│     }                                                   │
│                                                         │
│  ③ 心跳检测                                             │
│     每个实例每 5 秒发心跳, 15 秒没心跳标记不健康,        │
│     30 秒没心跳从注册表剔除                              │
│                                                         │
│  ④ 持久化层                                             │
│     standalone 模式: 内置 Derby DB                      │
│     生产: 外接 MySQL                                    │
└─────────────────────────────────────────────────────────┘
```

---

## 32.3  E0：装 + 启 Nacos（Docker 方案）

### 32.3.1  Docker 命令逐项拆解

```bash
docker run -d --name minimall-nacos \
  -p 8848:8848 \
  -p 9848:9848 \
  -e MODE=standalone \
  nacos/nacos-server:v2.3.2
```

| 参数 | 含义 |
|---|---|
| `run` | 创建并启动容器 |
| `-d` | detach 后台跑，不占当前终端 |
| `--name minimall-nacos` | 容器名，方便后续 stop/rm（避免跟旧的 demo-nacos 冲突）|
| `-p 8848:8848` | 容器内 8848 (HTTP+控制台) 映射到本机 8848 |
| `-p 9848:9848` | 容器 9848 (gRPC) → 本机 9848，Nacos 2.x 必需 |
| `-e MODE=standalone` | 单机模式，不开集群、不要外接 MySQL，学习够用 |
| `nacos/nacos-server:v2.3.2` | 镜像:版本，确保跟 SCA 客户端兼容 |

### 32.3.2  启动验证

```powershell
# 等容器内部初始化 ~30 秒，看日志
docker logs minimall-nacos | Select-String "Nacos started"
# → "Nacos started successfully in stand alone mode. use embedded storage"
```

控制台：http://localhost:8848/nacos （账号 `nacos` / `nacos`）

⚠️ **standalone 模式用内置 Derby**，重启容器配置/服务列表会丢。生产必须外接 MySQL。

---

## 32.4  E1：3 服务接入 Nacos

### 32.4.1  E1 文件清单（4 处改动）

```
mini-mall-cloud/
├── pom.xml                                          ← 父 pom 加 nacos-discovery
├── mini-mall-user/src/main/resources/application.yml      ← 加 nacos.discovery 配置
├── mini-mall-product/src/main/resources/application.yml   ← 同上
└── mini-mall-gateway/src/main/resources/application.yml   ← 同上
```

### 32.4.2  父 pom 改动

📁 `mini-mall-cloud/pom.xml` 父 `<dependencies>` 节点：

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-bootstrap</artifactId>
    </dependency>

    <!-- ⭐ E 阶段新增：Nacos 服务发现
         所有子模块自动继承, 但只有【有 main 方法 + 启动 + 配 server-addr】
         的服务才真正注册(user/product/gateway 三个);
         common-core 没 main 方法, 永远不会启动 → 自然不会注册 -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>
</dependencies>
```

**关键认知：为什么 common-core 不会注册？**

```
注册的【触发条件】要同时满足 3 个:
  ① 有 main 方法 + SpringApplication.run() 启动
  ② classpath 上有 nacos-discovery 依赖
  ③ application.yml 配了 server-addr + application.name

common-core:
  - packaging=jar 库模块
  - 没 main 方法
  - 永远不会被 java -jar 启动
  → 即使有依赖也无人触发注册 ✓ 安全
```

### 32.4.3  user-service yml 改动

📁 `mini-mall-user/src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: mini-mall-user        # ← 服务名, Nacos 用这个识别

  # ═════════════════════════════════════════════════════════
  # E 阶段新增：Nacos 服务发现
  # 启动时本服务会自动向 localhost:8848 注册自己(IP+端口+服务名)
  # ═════════════════════════════════════════════════════════
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848

  datasource:
    # ... 原有 MySQL 配置不动
```

### 32.4.4  product-service yml 改动

📁 `mini-mall-product/src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: mini-mall-product

  # E 阶段新增：Nacos 服务发现
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848

  datasource:
    # ...
```

### 32.4.5  gateway yml 改动（注意嵌套）

📁 `mini-mall-gateway/src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: mini-mall-gateway

  cloud:
    # E 阶段新增：Nacos 服务发现(跟 gateway 同级, 都是 cloud 的子节点)
    nacos:
      discovery:
        server-addr: localhost:8848

    gateway:        # ← 原有 routes 配置不动
      routes:
        - id: user-route
          # ...
```

⚠️ **YAML 缩进死规则再次提醒**：`nacos:` 跟 `gateway:` 同级（都是 `cloud` 的子节点），缩进 4 空格。

### 32.4.6  E1 底层原理：服务怎么自动注册？

Spring Boot 自动装配机制（v8 第 25 章讲过的）的完美应用：

```
启动流程:
  ① 引入 spring-cloud-starter-alibaba-nacos-discovery 依赖
       ↓
  ② 这个 jar 包含 META-INF/spring/
       org.springframework.boot.autoconfigure.AutoConfiguration.imports
       内容是几个 @Configuration 类全限定名
       ↓
  ③ Boot 启动时, @EnableAutoConfiguration 把这些类注册进容器
       关键类: NacosServiceRegistryAutoConfiguration
       ↓
  ④ 容器初始化完毕, 发布 ApplicationStartedEvent
       ↓
  ⑤ AbstractAutoServiceRegistration 监听这个事件,
     调 NacosServiceRegistry.register(实例信息)
       ↓
  ⑥ NacosServiceRegistry 向 Nacos Server 发 HTTP/gRPC:
       POST /nacos/v2/ns/instance
       body: { serviceName: "mini-mall-user", ip: "192.168.x.x", port: 9001 }
       ↓
  ⑦ 启动一个心跳线程, 每 5 秒发一次 PUT /nacos/v2/ns/instance/beat
       ↓
  ⑧ 服务可被发现
```

### 32.4.7  E1 验证

```powershell
# 直接查 Nacos API
curl.exe -s "http://localhost:8848/nacos/v1/ns/catalog/services?pageNo=1&pageSize=20&namespaceId=public"
```

期望响应：

```json
{
  "count": 3,
  "serviceList": [
    {"name":"mini-mall-product", "healthyInstanceCount":1},
    {"name":"mini-mall-gateway", "healthyInstanceCount":1},
    {"name":"mini-mall-user",    "healthyInstanceCount":1}
  ]
}
```

3 条全部 `healthyInstanceCount: 1` = ✅ 注册成功。

---

## 32.5  E2：Gateway 路由 lb:// 改造

### 32.5.1  lb:// 是什么

**`lb://`** 是 Spring Cloud Gateway 内置的特殊 scheme（协议头），意思是：

> "去找 LoadBalancer，让它从注册中心挑一个 `<服务名>` 的健康实例，然后转发"

```
uri: http://localhost:9001   ← 直连指定 host:port
uri: lb://mini-mall-user      ← 走 LoadBalancer + 注册中心查实例
```

### 32.5.2  E2 改动（只改 yml）

📁 `mini-mall-gateway/src/main/resources/application.yml`：

```yaml
routes:
  - id: user-route
    # E2: 从 http://硬编码 → lb://服务名
    # lb = LoadBalancer, 它会去 Nacos 查 mini-mall-user 的健康实例列表,
    # 然后按算法(默认轮询)挑一个转发
    uri: lb://mini-mall-user
    predicates:
      - Path=/user/**

  - id: product-route
    uri: lb://mini-mall-product
    predicates:
      - Path=/product/**
```

### 32.5.3  lb:// 底层执行流程

Gateway 内部有一个 `ReactiveLoadBalancerClientFilter`（自带的 GlobalFilter），它干这件事：

```java
// 简化版伪代码
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    // 1. 拿到路由的 uri
    URI uri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);

    // 2. 看 scheme 是不是 lb://
    if (!"lb".equals(uri.getScheme())) {
        return chain.filter(exchange);    // 不是 lb://, 直接转
    }

    // 3. 从 uri 取服务名 (mini-mall-user)
    String serviceName = uri.getHost();

    // 4. 让 LoadBalancer 挑一个实例
    return loadBalancer.choose(serviceName)
            .flatMap(instance -> {
                // 5. 拼出最终 URL: http://192.168.x.x:9001/user/me
                URI newUri = reconstructURI(instance, uri);
                exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, newUri);
                return chain.filter(exchange);
            });
}
```

### 32.5.4  Spring Cloud LoadBalancer 默认算法

| 算法 | 描述 | 适用 |
|---|---|---|
| **Round Robin（默认）** | 轮询，1→2→3→1→2→3 | 实例性能相同 |
| **Random** | 随机选 | 简单兜底 |
| **Weighted** | 按权重选 | 灰度发布 |
| **NacosLoadBalancer**（SCA 提供） | 按 Nacos 集群优先 | 多机房部署 |

要换算法，配置：

```yaml
spring:
  cloud:
    loadbalancer:
      ribbon:
        enabled: false        # 关掉旧 Ribbon (SC 2020 之前的算法)
```

### 32.5.5  E2 验证

```powershell
$token = "eyJhbGc..."
curl.exe -s -H "Authorization: Bearer $token" http://localhost:9080/product/1
# 期望: {"code":200,"data":{"name":"小米 14 Pro",...}}
```

链路证明：

```
curl → gateway:9080
       └─ AuthGlobalFilter: 验签通过, X-User-Id=1
       └─ ReactiveLoadBalancerClientFilter:
              uri = lb://mini-mall-product
              → 问 Nacos: "mini-mall-product 在哪?"
              → Nacos 返: 192.168.x.x:9002 (健康)
              → 拼最终 url: http://192.168.x.x:9002/product/1
       └─ NettyRoutingFilter: HTTP 转发
       → product:9002 → 拿数据 → 回传
```

---

## 32.6  E3：Feign 去掉 url 硬编码

### 32.6.1  E3 改动

📁 `mini-mall-user/src/main/java/com/minimall/user/client/ProductFeignClient.java`：

**改动前**：

```java
@FeignClient(name = "mini-mall-product", url = "http://localhost:9002")
public interface ProductFeignClient {
    @GetMapping("/product/{id}")
    Result<Map<String, Object>> getById(@PathVariable("id") Long id);
}
```

**改动后**：

```java
/**
 * Product 服务的 Feign 客户端
 *
 *   ③ E3 之后 url 已删除
 *      Feign 看到没有 url, 就走 LoadBalancer + Nacos 服务发现:
 *        - 启动时订阅 mini-mall-product 服务的实例列表
 *        - 每次调用挑一个健康实例转发(默认轮询)
 *        - product 挂了 Nacos 自动剔除, Feign 不会再选它
 *      这是 Feign + Nacos 的标准用法, 完全无感知地切换到了服务发现
 */
@FeignClient(name = "mini-mall-product")          // ← 删了 url= 参数
public interface ProductFeignClient {
    @GetMapping("/product/{id}")
    Result<Map<String, Object>> getById(@PathVariable("id") Long id);
}
```

### 32.6.2  Feign + Nacos 协作底层

```
启动时:
  ① @EnableFeignClients 扫描到 ProductFeignClient 接口
  ② 看到没有 url=, 把它注册为【负载均衡 Feign 客户端】
  ③ Spring Cloud LoadBalancer 准备好选择器, name=mini-mall-product
  ④ 后台启动一个 ServiceInstanceListSupplier 订阅 Nacos
     → Nacos 推送 mini-mall-product 实例列表变化时, 本地缓存自动更新

调用时:
  productFeignClient.getById(1L)
       ↓
  Feign 代理拦截
       ↓
  ① 拿到方法的 @GetMapping("/product/{id}")
  ② 拼 path: /product/1
  ③ 没 url, 问 LoadBalancer: "mini-mall-product 选一个"
       LoadBalancer 从本地缓存的实例列表挑一个 (轮询)
       → 拿到 192.168.x.x:9002
  ④ 拼出最终 url: http://192.168.x.x:9002/product/1
  ⑤ 用 HttpClient 发 HTTP
  ⑥ 收到 JSON → Jackson 反序列化 → 返业务代码
```

### 32.6.3  E3 验证

```powershell
$token = "eyJhbGc..."
curl.exe -s -H "Authorization: Bearer $token" \
  http://localhost:9080/user/1/with-product/1
```

期望响应（**整条链路验证**）：

```json
{
  "code": 200,
  "data": {
    "user":    {"id":1, "username":"alice", ...},
    "product": {"id":1, "name":"小米 14 Pro", ...}
  }
}
```

完整调用链路：

```
浏览器/curl
   ↓
gateway:9080 (Netty, lb://mini-mall-user)
   ↓ Nacos 查 → 192.168.x.x:9001
user-service:9001 (Tomcat)
   ↓ userMapper.selectById(1) → alice
   ↓ productFeignClient.getById(1)
        ↓ Nacos 查 mini-mall-product → 192.168.x.x:9002
product-service:9002
   ↓ productMapper.selectById(1) → 小米 14 Pro
   ↑ JSON 返回
user-service 拼装 {user, product}
   ↑ JSON 返回
gateway 透传
   ↑
浏览器拿到完整数据
```

---

## 32.7  E 阶段三大坑

### 坑 ①：mvn -q 静默成功导致以为失败

**现象**：

```powershell
mvn -pl mini-mall-gateway clean package -DskipTests -q
# 完全无输出
```

**原因**：`-q` (quiet) 模式只在出错时打日志，**成功就什么也不显示**。

**修复**：
```powershell
# 检查 jar 的时间戳确认构建成功
Get-ChildItem "mini-mall-gateway/target/*.jar" | Select Name, LastWriteTime
```

**教训**：CI 环境用 `-q` 没问题，开发期用 `-B`（batch mode，仍然有输出）：
```powershell
mvn -B clean package
```

### 坑 ②：Nacos 注册的 IP 不是 127.0.0.1

**现象**：

```json
{"hosts":[{"ip":"192.168.32.1","port":9001,"healthy":true}]}
```

明明在本机跑，Nacos 却把 IP 标为 `192.168.32.1`（VMware/Hyper-V 虚拟网卡）。

**原因**：Nacos 客户端通过 `InetAddress.getLocalHost()` 拿本机 IP，Windows 下会优先选第一块"非环回"网卡，恰好是虚拟网卡。

**本机内调用没问题**（同机器走哪个 IP 都能通），**但跨机器会出问题**（其他机器 ping 不通 192.168.32.x）。

**修复方案 A**：yml 显式指定 IP

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        ip: 192.168.1.100      # 真实物理网卡 IP
```

**修复方案 B**：用环境变量

```yaml
        ip: ${HOST_IP:127.0.0.1}    # 启动时 export HOST_IP=...
```

**教训**：生产环境一定要显式控制 `discovery.ip`，**别让 Nacos 自己猜**。

### 坑 ③：Boot 3 + SCA 版本不对齐启动崩

**现象**：启动 user-service 报：

```
NoSuchMethodError: org.springframework.cloud.client.discovery.ReactiveDiscoveryClient.xxx
```

**原因**：Spring Cloud / Spring Cloud Alibaba 版本错配。

**严格对照表**（v7 第一章讲过的）：

| Spring Boot | Spring Cloud | Spring Cloud Alibaba |
|---|---|---|
| 3.3.x | 2023.0.x | **2023.0.1.2** |
| 3.2.x | 2023.0.x | 2023.0.1.0 |
| 3.1.x | 2022.0.x | 2022.0.0.0 |

**项目用的版本**（mini-mall-cloud/pom.xml properties 段）：

```xml
<spring-cloud.version>2023.0.3</spring-cloud.version>
<spring-cloud-alibaba.version>2023.0.1.2</spring-cloud-alibaba.version>
```

**教训**：每次升 Boot 必查官方版本对照表，别凭感觉填。

---

## 32.8  E 阶段成果对比

### 32.8.1  D 阶段 vs E 阶段能力对比

| 能力 | D 阶段后 | E 阶段后 |
|---|---|---|
| Gateway 路由 | yml 写死 IP+端口 | `lb://服务名` Nacos 自动 |
| Feign 调用 | url 硬编码 | name 走 Nacos |
| 端口改动 | 改 3 个文件 | 改 1 个 yml + 重启 |
| 扩容（多实例） | 不支持 | Nacos 自动 LB |
| 故障感知 | gateway 一直转 | 心跳超时自动剔除 |
| 多机房 | 没法做 | NacosLoadBalancer 支持 |

### 32.8.2  目录结构变化

```
mini-mall-cloud/
├── pom.xml                              ← ✏️ 父 dependencies 加 nacos-discovery
│
├── mini-mall-user/
│   ├── pom.xml                          ← 无变化(继承父)
│   └── src/main/
│       ├── java/com/minimall/user/
│       │   └── client/
│       │       └── ProductFeignClient.java   ← ✏️ 删 url=
│       └── resources/
│           └── application.yml          ← ✏️ 加 cloud.nacos.discovery
│
├── mini-mall-product/
│   └── src/main/resources/
│       └── application.yml              ← ✏️ 加 cloud.nacos.discovery
│
└── mini-mall-gateway/
    └── src/main/resources/
        └── application.yml              ← ✏️ 加 nacos + routes 改 lb://
```

---

## 32.9  附录 A：实测命令速查

### 32.9.1  Nacos 管理

```bash
# 启 Nacos
docker run -d --name minimall-nacos \
  -p 8848:8848 -p 9848:9848 \
  -e MODE=standalone \
  nacos/nacos-server:v2.3.2

# 看日志
docker logs minimall-nacos --tail 50

# 停 / 启 / 删
docker stop  minimall-nacos
docker start minimall-nacos
docker rm -f minimall-nacos     # 彻底删, 数据消失
```

### 32.9.2  Nacos API 验证

```bash
# 列出所有服务
curl "http://localhost:8848/nacos/v1/ns/catalog/services?pageNo=1&pageSize=20&namespaceId=public"

# 看某个服务的实例列表
curl "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=mini-mall-user"

# 控制台 UI
# http://localhost:8848/nacos  (账号: nacos / nacos)
```

### 32.9.3  3 jar 启动顺序（E 阶段后）

```powershell
# 0. Nacos 先在跑
docker start minimall-nacos

# 1. user-service
& "D:\jdk-21.0.11\bin\java.exe" -jar `
  "...\mini-mall-user\target\mini-mall-user-0.0.1-SNAPSHOT.jar"

# 2. product-service
& "D:\jdk-21.0.11\bin\java.exe" -jar `
  "...\mini-mall-product\target\mini-mall-product-0.0.1-SNAPSHOT.jar"

# 3. gateway (必须最后)
& "D:\jdk-21.0.11\bin\java.exe" -jar `
  "...\mini-mall-gateway\target\mini-mall-gateway-0.0.1-SNAPSHOT.jar"
```

### 32.9.4  E2E 测试

```powershell
# 拿 token
$body = '{"username":"alice","password":"123456"}'
Set-Content "$env:TEMP\login.json" $body -Encoding ascii -NoNewline

$resp = curl.exe -s -X POST -H "Content-Type: application/json" `
  --data-binary "@$env:TEMP\login.json" `
  http://localhost:9080/user/login | ConvertFrom-Json
$token = $resp.data

# 跨服务调用
curl.exe -s -H "Authorization: Bearer $token" `
  http://localhost:9080/user/1/with-product/1
```

---

# 第三十三章：F1 阶段 Nacos 配置中心

> 接续 E 阶段（32 章），同一个 Nacos Server 复用其【第二个能力】：配置中心。
> 完成日期：2026-06-19  |  F 阶段第一站

---

## 33.0  F 阶段全景 + F1 目标

### 33.0.1  F 阶段定位

E 阶段解决了"在哪"（服务发现），F 阶段解决"怎么治"（治理）。

| 方向 | 内容 | 优先级 |
|---|---|---|
| **F1 配置中心** | Nacos 同时管配置，yml 放云端 + 动态刷新 | ★★★ |
| F2 Sentinel 限流 | 接口级流量控制 + 熔断 | ★★ |
| F3 链路追踪 | SkyWalking / Zipkin | ★★ |
| F4 分布式事务 | Seata（订单 + 扣库存）| ★ |

### 33.0.2  F1 一句话目标

> 把 yml 里【容易变 + 敏感】的配置搬到 Nacos 控制台，服务**启动时**去 Nacos 拉，**改了实时推送**到所有实例（无需重启）。

---

## 33.1  F1 痛点（E 阶段后留下的）

```
mini-mall-user/application.yml:
  spring:
    datasource:
      password: 123456                       ← 明文密码, GitHub 一推就被扫
  jwt:
    secret: my-mini-mall-super-secret-key-...← 明文密钥
    expiration: 604800000                    ← 改一次 token 有效期, 重打 jar
```

**3 个具体痛**：

| 痛 | 现状 |
|---|---|
| ① 密码泄露风险 | yml 进 git → GitHub bot 自动扫到 → 公开数据库 |
| ② 改配置要重打 jar | 改一个 password → mvn clean package → 重启 → 业务中断 |
| ③ 多环境难管 | dev / test / prod 密码不同 → 3 份 yml 副本 → 混 |

---

## 33.2  Nacos 配置中心是什么

回忆 32.2 章：Nacos 有 3 个核心能力，**E 阶段只用了服务发现**，F1 启用第 2 个：

| 能力 | E 阶段 | F1 |
|---|---|---|
| 服务发现 | ✅ | 继续用 |
| **配置中心** | ❌ | ⭐ 启用 |
| DNS 路由 | - | - |

### 33.2.1  Nacos 配置中心内部结构

```
┌─── Nacos Server (复用 32 章装的 minimall-nacos) ────┐
│                                                       │
│  ① 配置存储                                            │
│     standalone: 内置 Derby DB                          │
│     生产: 外接 MySQL                                   │
│                                                       │
│  ② 配置组织 (三级隔离)                                  │
│                                                       │
│     Namespace (命名空间)                              │
│         └── Group (分组)                              │
│              └── DataId (配置文件名)                  │
│                                                       │
│     默认结构:                                          │
│       public  /  DEFAULT_GROUP  /  mini-mall-user.yaml │
│        └ 环境     └ 业务线        └ 配置文件名         │
│                                                       │
│  ③ 长连接推送 (gRPC :9848)                            │
│     客户端启动时跟 Nacos 建立 gRPC 长连接              │
│     配置一变, Nacos 主动 push, 客户端 50ms 内收到      │
└───────────────────────────────────────────────────────┘
```

---

## 33.3  4 个核心概念（F1 必懂）

### 33.3.1  bootstrap.yml vs application.yml

```
启动顺序差异:

  ① 先读 bootstrap.yml      ← 写【从哪拉远程配置】(Nacos 地址)
       ↓ 拿 Nacos 地址 + dataId
       ↓ 去 Nacos 拉 mini-mall-user.yaml 的内容
  ② 再读 application.yml    ← 写【本地默认值】
  ③ 远程配置【覆盖】本地

bootstrap 是"自举"的意思 —— 引导启动, 它比 application 更早。

如果把 nacos 配置写在 application.yml:
  容器准备好了 → 才读 application.yml 知道 nacos 地址 →
  太晚 → 启动期就要用的 Bean 拿不到远程值
```

### 33.3.2  dataId 命名规则

```
默认规则: ${spring.application.name}.${file-extension}

例如:
  application.name = "mini-mall-user"
  file-extension = "yaml"
  → dataId = "mini-mall-user.yaml"

bootstrap.yml 里改 file-extension 只能选: properties / yaml
(yml 也被 Nacos 2.x 兼容, 但官方推荐 yaml)
```

### 33.3.3  Group 分组

```
用来隔离【环境】或【业务线】, 默认 DEFAULT_GROUP

常见拆法:
  - 按环境:   DEV_GROUP / TEST_GROUP / PROD_GROUP
  - 按业务线: ORDER_GROUP / USER_GROUP / PAYMENT_GROUP

学习阶段都用默认 DEFAULT_GROUP, 不必折腾。
```

### 33.3.4  @RefreshScope（动态刷新的关键）

```java
@RefreshScope    // ← 标了这个的 Bean 才会动态刷新
@Component
public class JwtUtil {
    @Value("${jwt.expiration}")
    private Long expiration;       // Nacos 改了, 这个字段会自动更新
}
```

**没标 @RefreshScope 的后果**：

```
@Value 注入【只在启动时执行一次】, 字段值【固化】
Nacos 改了配置 → Spring 收到推送, 内部上下文已更新
                                            ↓
但 JwtUtil 这个 Bean 实例不会被重建 →
                                            ↓
expiration 字段还是旧值 → 业务感知不到
```

**标 @RefreshScope 的工作机制**：

```
Nacos 推送配置变化
       ↓
Spring 发布 RefreshEvent
       ↓
被 @RefreshScope 标的 Bean 实例【销毁】
       ↓
下次调用时【重新创建】, 重新执行 @Value 注入
       ↓
字段拿到新值 ✓
```

---

## 33.4  F1 路线图

| 步骤 | 做什么 | 改的文件 |
|---|---|---|
| F1.1 | 父 pom 加 `nacos-config` 依赖 | `pom.xml` |
| F1.2 | user-service 建 `bootstrap.yml` | 新建 |
| F1.3 | 在 Nacos 创建配置项 `mini-mall-user.yaml` | Nacos 控制台 / API |
| F1.4 | 重启验证：远程配置覆盖本地 | - |
| F1.5 | 改 Nacos 配置看是否动态刷新（会失败，留教学时刻）| - |
| F1.6 | 给 JwtUtil 加 `@RefreshScope` + 再验证 | `JwtUtil.java` |

---

## 33.5  F1.1：父 pom 加 nacos-config 依赖

### 33.5.1  改动位置

📁 `mini-mall-cloud/pom.xml`（父 pom 的 `<dependencies>` 区，跟 E 阶段加 discovery 同一处）

### 33.5.2  完整代码（改动后）

```xml
<!-- ⭐ 父级 <dependencies>：强制所有子模块都有 -->
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-bootstrap</artifactId>
    </dependency>

    <!-- ⭐ E 阶段新增：Nacos 服务发现 -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>

    <!-- ⭐ F1 新增：Nacos 配置中心
         跟 discovery 是同一个 Nacos Server, 不需要再装东西
         有了它, 服务启动时会去 Nacos 拉远程配置 -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
    </dependency>
</dependencies>
```

### 33.5.3  代码解释

| 依赖 | 作用 |
|---|---|
| `spring-cloud-starter-bootstrap` | A 阶段加的，确保 `bootstrap.yml` 会被加载 |
| `nacos-discovery` | E 阶段加的，向 Nacos 注册服务 |
| `nacos-config`（F1 新加）| 启动时从 Nacos 拉配置，订阅配置变化 |

⭐ **为什么放父 pom**：跟 E1 同样道理，3 个服务全要用，写父 pom 子模块自动继承。

---

## 33.6  F1.2：user-service 建 bootstrap.yml

### 33.6.1  新建文件路径

📁 `mini-mall-user/src/main/resources/bootstrap.yml` （新建）

跟 `application.yml` 同一目录，**但比 application.yml 先被读**。

### 33.6.2  完整代码

```yaml
# ═══════════════════════════════════════════════════════════
# bootstrap.yml — Spring Cloud 启动期最早读的配置
# F1 新增, 配 Nacos Config 拉远程配置
# ═══════════════════════════════════════════════════════════
spring:
  application:
    name: mini-mall-user

  cloud:
    nacos:
      config:
        # Nacos Server 地址 (同一个 server 同时管 discovery 和 config)
        server-addr: localhost:8848

        # 配置文件后缀, 决定 dataId 用什么扩展名
        # 这里设 yaml, dataId 就是 mini-mall-user.yaml
        file-extension: yaml

        # 分组, 默认 DEFAULT_GROUP
        group: DEFAULT_GROUP

        # 命名空间, 默认 public
        namespace: public
```

### 33.6.3  逐行解释

| 行 | 解释 |
|---|---|
| `spring.application.name: mini-mall-user` | **必须在 bootstrap 里再写一份**，因为 Nacos 拉配置时默认 `dataId = ${application.name}.${file-extension}`，bootstrap 阶段就要确定服务名，application.yml 太晚 |
| `cloud.nacos.config.server-addr` | Nacos 地址，跟 discovery 那个一样 |
| `file-extension: yaml` | 后缀，所以 dataId 是 `mini-mall-user.yaml` |
| `group: DEFAULT_GROUP` | 分组，默认就行 |
| `namespace: public` | 命名空间，默认就行 |

⚠️ **常见误区**：以为 `bootstrap.yml` 是 SCA 特有，其实是 Spring Cloud 通用规范，但需要 `spring-cloud-starter-bootstrap` 依赖（A 阶段加在父 pom 里了）。

---

## 33.7  F1.3：在 Nacos 创建配置项

### 33.7.1  方式 A：用 Nacos API 直接创建（推荐脚本化）

```powershell
# 1. 准备配置内容
$content = @"
# F1 远程配置: 在这里改 jwt.expiration, 服务会动态刷新
jwt:
  expiration: 86400000   # 24 小时 (毫秒), 故意改小让你看到效果
"@

# 2. URL encode (Nacos API 要求 form 提交)
Add-Type -AssemblyName System.Web
$encoded = [System.Web.HttpUtility]::UrlEncode($content)

# 3. POST 给 Nacos
$body = "dataId=mini-mall-user.yaml&group=DEFAULT_GROUP&content=$encoded&type=yaml"
curl.exe -s -X POST `
  -H "Content-Type: application/x-www-form-urlencoded" `
  -d $body `
  "http://localhost:8848/nacos/v1/cs/configs"
```

返回 `true` = 创建成功。

### 33.7.2  方式 B：Nacos 控制台手动建

1. 浏览器打开 http://localhost:8848/nacos （账号 `nacos` / `nacos`）
2. 左侧菜单 → 配置管理 → 配置列表
3. 右上角 `+` 创建配置
4. 填：
   - Data ID: `mini-mall-user.yaml`
   - Group: `DEFAULT_GROUP`
   - 配置格式: `YAML`
   - 配置内容: 同上 yaml
5. 点【发布】

### 33.7.3  验证配置项存在

```powershell
curl.exe -s "http://localhost:8848/nacos/v1/cs/configs?dataId=mini-mall-user.yaml&group=DEFAULT_GROUP"
```

期望响应：刚才填的 yaml 内容原样返回。

### 33.7.4  本地 vs 远程的差异（关键设计）

| 配置项 | 本地 `application.yml` 里 | Nacos 里 |
|---|---|---|
| `jwt.expiration` | `604800000` (7 天) | `86400000` (24 小时) |

⭐ **故意设不同**，验证时如果生成的 token 是 24 小时 → 证明远程覆盖了本地。

---

## 33.8  F1.4：重启 user-service 验证

### 33.8.1  重打 + 重启命令

```powershell
# kill 旧 user
$pid9001 = (Get-NetTCPConnection -LocalPort 9001 -State Listen).OwningProcess
Stop-Process -Id $pid9001 -Force

# 重打 jar (改了 pom 必须 clean)
cd "C:\Users\liyuq\OneDrive\桌面\Java学习代码\mini-mall-cloud"
mvn -pl mini-mall-user clean package -DskipTests

# 启 jar
& "D:\jdk-21.0.11\bin\java.exe" -jar `
  "mini-mall-user\target\mini-mall-user-0.0.1-SNAPSHOT.jar"
```

### 33.8.2  启动日志特征（证明拉到远程配置）

```log
INFO  c.a.cloud.nacos.NacosConfigProperties    : set nacos config namespace 'public' to ''
INFO  c.a.n.c.c.impl.LocalConfigInfoProcessor  : LOCAL_SNAPSHOT_PATH: C:\Users\liyuq\nacos\config
INFO  com.alibaba.nacos.common.remote.client   : [RpcClientFactory] create a new rpc client of ... config-0
INFO  com.alibaba.nacos.common.remote.client   : Register server push request handler:
```

最后一行 **"Register server push request handler"** 是关键 —— 长连接已建立，**Nacos 配置一变就会 push 过来**。

### 33.8.3  验证：登录看 token 的 exp

```powershell
# 登录
$body = '{"username":"alice","password":"123456"}'
Set-Content "$env:TEMP\login.json" $body -Encoding ascii -NoNewline
$resp = curl.exe -s -X POST -H "Content-Type: application/json" `
  --data-binary "@$env:TEMP\login.json" `
  http://localhost:9001/user/login | ConvertFrom-Json

# 解 JWT 第 2 段 (base64url 编码的 payload)
$token = $resp.data
$payload = $token.Split('.')[1]
$mod = $payload.Length % 4
if ($mod -gt 0) { $payload += '=' * (4 - $mod) }       # 补 base64 padding
$payload = $payload.Replace('-', '+').Replace('_', '/') # base64url → base64
$decoded = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload))
$claims = $decoded | ConvertFrom-Json

# 算时长
$hours = ($claims.exp - $claims.iat) / 3600
"token expiration = $hours 小时"
```

期望输出：
```
token expiration = 24 小时   ← Nacos 远程值生效, 本地的 7 天被覆盖
```

✅ **F1.4 完成** — 远程配置生效。

### 33.8.4  代码解释（JWT 解码部分）

| 行 | 解释 |
|---|---|
| `$token.Split('.')[1]` | JWT 三段式 `header.payload.signature`，取第 2 段（payload）|
| `$mod = $payload.Length % 4` + 补 `=` | base64 长度必须是 4 的倍数，不够补 `=` padding |
| `Replace('-', '+').Replace('_', '/')` | base64url 字符 `-_` 还原成标准 base64 的 `+/` |
| `FromBase64String + UTF8.GetString` | 解码字节流 → UTF-8 字符串 |
| `ConvertFrom-Json` | JSON 字符串 → PowerShell 对象 |
| `$claims.exp - $claims.iat` | exp（过期时间）- iat（签发时间）= token 有效时长（秒）|

---

## 33.9  F1.5：默认 @Value 不动态刷新（教学坑）

### 33.9.1  实验：改 Nacos 配置看效果

```powershell
# 把 Nacos 配置改成 2 小时, 不重启 user-service
$content = "jwt:`n  expiration: 7200000"
Add-Type -AssemblyName System.Web
$encoded = [System.Web.HttpUtility]::UrlEncode($content)
$body = "dataId=mini-mall-user.yaml&group=DEFAULT_GROUP&content=$encoded&type=yaml"
curl.exe -s -X POST -H "Content-Type: application/x-www-form-urlencoded" `
  -d $body "http://localhost:8848/nacos/v1/cs/configs"
```

返回 `true`。等 2 秒，看 user-service 日志：

```log
INFO  c.a.nacos.client.config.impl.CacheData  : [notify-context] dataId=mini-mall-user.yaml
INFO  o.s.c.e.event.RefreshEventListener      : Refresh keys changed: [jwt.expiration]
INFO  c.a.nacos.client.config.impl.CacheData  : [notify-ok] ... listener=NacosContextRefresher$1
```

**Spring 明确知道 `jwt.expiration` 变了**！

### 33.9.2  但是再登录看 token

```powershell
# 重新登录 (不重启服务)
$resp = curl.exe -s -X POST -H "Content-Type: application/json" `
  --data-binary "@$env:TEMP\login.json" `
  http://localhost:9001/user/login | ConvertFrom-Json
# ... 解 JWT ...
"token expiration = $hours 小时"
```

输出：
```
token expiration = 24 小时   ❌ 还是旧值, 不是 2 小时
```

### 33.9.3  为什么 Spring 已经"知道"了，token 还是旧值？

**根因**：`JwtUtil` 类没标 `@RefreshScope`。

```java
@Component                              // ← 普通 Bean
public class JwtUtil {
    @Value("${jwt.expiration}")
    private Long expiration;             // ← @Value 注入只在启动时执行一次
                                          //   注入后这个字段就【固化】了
    // ...
}
```

```
Nacos 推送变化
       ↓
Spring 上下文里的属性源更新了 (environment.jwt.expiration = 7200000)
       ↓
但 JwtUtil 这个 Bean 实例不会被重建
       ↓
expiration 字段值还是 86400000 (启动时注入的)
       ↓
生成 token 时还是用旧值 → token 还是 24 小时
```

---

## 33.10  F1.6：加 @RefreshScope 实现动态刷新

### 33.10.1  改动文件

📁 `mini-mall-user/src/main/java/com/minimall/user/util/JwtUtil.java`

### 33.10.2  改动 1：加 import

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;   // ← 新增
import org.springframework.stereotype.Component;
```

### 33.10.3  改动 2：类上加 @RefreshScope

```java
/**
 * F1 新增 @RefreshScope:
 *   配合 Nacos Config 实现动态刷新
 *   - 当 Nacos 上的 jwt.expiration / jwt.secret 改变时
 *   - Spring 销毁本 Bean 实例, 重新创建并重新执行 @Value 注入
 *   - 字段拿到新值, 不用重启服务
 *
 * 没标 @RefreshScope 时:
 *   @Value 只在启动时执行一次, 字段值固化, 远程改了字段不变
 */
@Component
@RefreshScope         // ⭐ 新增这一行
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    // 其他方法不变
}
```

### 33.10.4  代码解释

| 注解 | 作用 |
|---|---|
| `@Component` | 让 Spring 实例化这个类放进容器 |
| `@RefreshScope` | **额外**把它包装成"可刷新作用域"，配置变化时整个 Bean 销毁 + 重建 |
| `@Value("${jwt.expiration}")` | 启动时（以及每次重建时）从 Spring 环境读 `jwt.expiration` 值 |

**重建机制底层**：

```
@RefreshScope 实际上是 @Scope("refresh") 的语法糖
       ↓
Spring 为这个 Bean 创建一个【代理对象】(CGLib)
       ↓
你 @Autowired JwtUtil 拿到的实际是代理
       ↓
每次调用代理方法时, 代理去【RefreshScope 容器】查"当前实例"
       ↓
Nacos 推送变化 → RefreshEvent → RefreshScope 把当前实例清空
       ↓
下次调用时发现没实例 → 重建 (调构造 + @Value 注入)
```

### 33.10.5  验证完整动态刷新链路

```powershell
# 1. 重打 + 重启 user-service (因为改了 .java)
# (此时 Nacos 里 jwt.expiration = 7200000 = 2h)

# 启动后第一次登录 → 应该是 2 小时
# token expiration = 2 小时 ✓

# 2. 不重启服务, 改 Nacos 配置为 6 小时
$content = "jwt:`n  expiration: 21600000"
$encoded = [System.Web.HttpUtility]::UrlEncode($content)
$body = "dataId=mini-mall-user.yaml&group=DEFAULT_GROUP&content=$encoded&type=yaml"
curl.exe -X POST -H "Content-Type: application/x-www-form-urlencoded" `
  -d $body "http://localhost:8848/nacos/v1/cs/configs"

# 3. 等 1-2 秒, 重新登录
# token expiration = 6 小时 ✓ 动态刷新成功!
```

✅ **F1.6 完成** — 不重启服务，配置实时生效。

---

## 33.11  F1 底层原理（两条流程图）

### 33.11.1  服务启动时加载远程配置

```
java -jar mini-mall-user.jar
   ↓
① JVM 启动, 加载 SpringApplication
   ↓
② BootstrapApplicationListener (spring-cloud-starter-bootstrap 提供)
   监听到 Boot 启动事件, 创建【bootstrap 阶段的 ApplicationContext】
   ↓
③ bootstrap 阶段读 bootstrap.yml
   拿到 spring.application.name = mini-mall-user
   拿到 spring.cloud.nacos.config.server-addr = localhost:8848
   ↓
④ NacosConfigBootstrapConfiguration 启动 NacosPropertySourceLocator
   ↓
⑤ NacosPropertySourceLocator.locate() 干这件事:
   a) 拼 dataId: mini-mall-user.yaml
   b) 发 HTTP GET http://localhost:8848/nacos/v1/cs/configs?dataId=...
   c) 拿到 yaml 内容
   d) 解析成 Properties 加入【bootstrap 阶段的 PropertySource】
   ↓
⑥ bootstrap 阶段结束, 进入【主阶段 ApplicationContext】
   bootstrap 阶段的 PropertySource 被【继承】过来
   ↓
⑦ 读本地 application.yml, 加入 PropertySource (优先级低于远程)
   ↓
⑧ @Configuration / @Component / @Service 一个个实例化
   ↓
⑨ @Value("${jwt.expiration}") 注入时
   Spring 按优先级查: 远程(86400000) > 本地(604800000)
   注入 86400000
   ↓
⑩ 同时 NacosContextRefresher 启动一个监听器
   通过 gRPC 长连接订阅 Nacos 上 mini-mall-user.yaml 的变化
```

### 33.11.2  配置变化时的动态刷新

```
你改 Nacos 配置 (改成 7200000)
   ↓
Nacos Server 内部存储更新
   ↓
Nacos 通过 gRPC 长连接 push 给所有订阅者
   ↓
user-service 端的 CacheData 接收到推送
   ↓
触发 Listener: NacosContextRefresher$1
   ↓
发布 RefreshEvent (Spring 内置事件)
   ↓
RefreshEventListener 接住:
   ① 更新 Environment 里的 PropertySource (新值已就位)
   ② 调用 ContextRefresher.refresh()
      → 清空 RefreshScope 容器里的所有 Bean 实例
      → (注意只清实例, 不清 BeanDefinition)
   ↓
JwtUtil (标了 @RefreshScope) 的实例被清空
   ↓
下次有代码调 jwtUtil.generateToken(...)
   ↓
代理对象发现 RefreshScope 里没实例
   ↓
重建 JwtUtil: new JwtUtil() + @Value 重新注入 (拿到新值 7200000)
   ↓
generateToken 用新值 → token expiration = 2 小时 ✓
```

---

## 33.12  配置优先级规则

Spring Boot 多个 PropertySource 共存时的优先级（高 → 低）：

```
1. 命令行参数            (java -jar xxx.jar --jwt.expiration=999)
2. ⭐ Nacos 远程配置      (mini-mall-user.yaml)
3. 本地 application.yml
4. 本地 bootstrap.yml
5. 代码里 @Value 默认值   ("${jwt.expiration:60000}")
```

⭐ **核心规律**：远程配置 > 本地配置，命令行 > 远程。

**实际生效计算**：

```
本地 application.yml:  jwt.expiration: 604800000  (7天)
Nacos 远程:            jwt.expiration: 86400000   (24h)
命令行参数:            未传

→ 最终 @Value 注入的值: 86400000  (远程胜出)
```

---

## 33.13  F1 三大坑

### 坑 ①：bootstrap.yml 没生效

**现象**：服务启动了但没去 Nacos 拉配置，日志里没 `nacos config` 字样。

**原因**：父 pom 没引 `spring-cloud-starter-bootstrap`（Boot 2.4+ 起，bootstrap 加载机制需要这个）。

**修复**：父 pom 的 `<dependencies>` 加：
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bootstrap</artifactId>
</dependency>
```

A 阶段就加了，这个坑你不会再踩，但要知道为什么必要。

### 坑 ②：dataId 后缀写错

**现象**：bootstrap.yml 里 `file-extension: yml`，Nacos 上配置叫 `mini-mall-user.yaml`。

**原因**：dataId 默认拼接是 `${name}.${file-extension}`。`file-extension` 写 `yml` 就找 `mini-mall-user.yml`，跟实际不一致 → 拉空。

**修复**：bootstrap.yml 的 `file-extension` 和 Nacos 上的 dataId 后缀**完全一致**。

### 坑 ③：改 Nacos 没动态刷新

**现象**：改了 Nacos 配置，日志里也看到 `Refresh keys changed`，但业务代码还用旧值。

**原因**：用 `@Value` 的 Bean 没标 `@RefreshScope`。

**修复**：相关 Bean 类上加 `@RefreshScope`。

**注意**：不是所有 Bean 都需要 `@RefreshScope`，**只在用到 `@Value` 且这个值会动态变** 的类上标。乱标会让 Bean 不必要地销毁重建。

---

## 33.14  F1 目录结构变化

```
mini-mall-cloud/
├── pom.xml                                       ← ✏️ 父 dependencies 加 nacos-config
│
└── mini-mall-user/
    └── src/main/
        ├── java/com/minimall/user/
        │   └── util/
        │       └── JwtUtil.java                  ← ✏️ 类上加 @RefreshScope
        │                                            ✏️ import RefreshScope
        └── resources/
            ├── application.yml                   ← 无改动 (jwt.expiration 保留作兜底)
            └── bootstrap.yml                     ← ➕ 新建, 配 Nacos Config
```

⚠️ **product / gateway 这次没改**，是小范围演示。完整版做法是 3 个服务都改、3 个服务都建 bootstrap.yml、Nacos 上建 3 个配置项。

---

## 33.15  F1 命令速查

### 33.15.1  Nacos 配置 API

```bash
# 创建/更新配置
curl -X POST -H "Content-Type: application/x-www-form-urlencoded" \
  -d "dataId=mini-mall-user.yaml&group=DEFAULT_GROUP&content=<urlencoded内容>&type=yaml" \
  "http://localhost:8848/nacos/v1/cs/configs"

# 查询配置
curl "http://localhost:8848/nacos/v1/cs/configs?dataId=mini-mall-user.yaml&group=DEFAULT_GROUP"

# 删除配置
curl -X DELETE \
  "http://localhost:8848/nacos/v1/cs/configs?dataId=mini-mall-user.yaml&group=DEFAULT_GROUP"
```

### 33.15.2  PowerShell 包装的"改配置"函数

```powershell
function Set-NacosConfig {
    param([string]$DataId, [string]$Content)
    Add-Type -AssemblyName System.Web
    $encoded = [System.Web.HttpUtility]::UrlEncode($Content)
    $body = "dataId=$DataId&group=DEFAULT_GROUP&content=$encoded&type=yaml"
    curl.exe -s -X POST -H "Content-Type: application/x-www-form-urlencoded" `
      -d $body "http://localhost:8848/nacos/v1/cs/configs"
}

# 使用
Set-NacosConfig -DataId "mini-mall-user.yaml" -Content "jwt:`n  expiration: 21600000"
```

### 33.15.3  PowerShell 解 JWT 看 exp

```powershell
function Get-JwtExpHours {
    param([string]$Token)
    $payload = $Token.Split('.')[1]
    $mod = $payload.Length % 4
    if ($mod -gt 0) { $payload += '=' * (4 - $mod) }
    $payload = $payload.Replace('-', '+').Replace('_', '/')
    $claims = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json
    ($claims.exp - $claims.iat) / 3600
}

# 使用
$token = "eyJhbGc..."
"token expiration = $(Get-JwtExpHours $token) 小时"
```

---

## 33.16  接下来：F 阶段后续预告

F1 解决了"配置怎么管"，剩下的 F2 / F3 / F4：

| 阶段 | 目标 | 学到 |
|---|---|---|
| **F2** | Sentinel 限流 + 熔断 | 防刷接口、防雪崩、降级 |
| **F3** | SkyWalking 链路追踪 | 全链路可视化、性能瓶颈定位 |
| **F4** | Seata 分布式事务 | 跨服务的 ACID（订单+库存）|

**推荐顺序**：F2 → F3 → F4（按依赖度，F2 最独立、F4 最复杂）。

也可以**先把 F1 扩到 3 个服务都用**（product / gateway 也建 bootstrap.yml + 把敏感配置都迁过去），然后再进 F2。

---

## 33.17  章节地图（更新到 F2）

见本文档最末尾的 34.16 节。

---

# 第三十四章：F2 阶段 Sentinel 限流熔断

> 接续 F1（33 章），用同源体系（阿里）的下一个能力：流量治理。
> 完成日期：2026-06-20  |  F 阶段第二站

---

## 34.0  F2 阶段全景

### 34.0.1  F2 一句话目标

> 让每个微服务都能**抵御流量洪水**：超过设定阈值就**限流**、被调服务挂了就**熔断**、机器吃不消时**系统保护**，所有规则在 **Sentinel Dashboard** 集中可视化管理。

### 34.0.2  Sentinel = Nacos 的"治理双胞胎"

| | F1 Nacos Config | F2 Sentinel |
|---|---|---|
| 治什么 | 配置散乱 | 流量过载 |
| 客户端 jar | `nacos-config` | `sentinel-core` |
| 服务端 | Nacos Server (8848+9848) | Sentinel Dashboard (8858) |
| 规则放哪 | Nacos 配置项 | Dashboard（也可持久化到 Nacos）|
| 通信 | gRPC 长连接 | HTTP 心跳 + 反向通信 |

---

## 34.1  F2 痛点

```
现状: 没有任何限流

攻击者写脚本疯狂调 /user/login (撞密码)
   ↓
user-service 100% CPU + DB 连接池打满
   ↓
正常用户也访问不了 → 服务雪崩

或者真实秒杀场景:
   小米14 秒杀, 10w 用户同时请求 /product/buy
   ↓ 一个 user-service 实例没扛住挂了
   ↓ 流量全打到剩下实例 → 雪上加霜
   ↓ 全部挂 → 服务雪崩 → 业务全停
```

---

## 34.2  Sentinel 是什么 + 4 核心概念

### 34.2.1  一句话定位

> 阿里开源的**流量治理框架**，解决 4 件事：**限流 / 熔断 / 降级 / 系统保护**。

GitHub：https://github.com/alibaba/Sentinel

---

### 34.2.2  4 个核心概念 + 对应的 Java 类

| 概念 | 中文 | 干啥 | Sentinel 里的实际类 |
|---|---|---|---|
| **Resource** | 资源 | 被保护的对象 | `ResourceWrapper` |
| **Rule** | 规则 | 限流/熔断的阈值参数 | `FlowRule` / `DegradeRule` / `SystemRule` ... |
| **Slot Chain** | 槽链 | 一连串拦截器 | `ProcessorSlotChain` |
| **BlockHandler** | 降级方法 | 被拦时回调的"备胎" | 用户自己写的方法 |
| Entry | 入口 | 进入 Resource 的"许可证" | `Entry` |

---

### 34.2.3  Resource：被保护的对象

📁 来自 sentinel-core jar：`com.alibaba.csp.sentinel.slotchain.ResourceWrapper`

```java
// Sentinel 源码（简化）
public abstract class ResourceWrapper {
    protected final String name;       // 资源名(loginResource)
    protected final EntryType type;    // IN(入口流量) / OUT(出口流量)
    protected final int resourceType;  // 0=COMMON / 1=WEB / 2=RPC / 3=API_GATEWAY
}
```

**生成 Resource 的 3 种方式**：

```java
// 方式 ① 代码层手动 (最底层 API)
Entry entry = null;
try {
    entry = SphU.entry("loginResource");   // 进入资源, 创建 Resource
    // 业务代码
} catch (BlockException ex) {
    // 被限流时进这里
} finally {
    if (entry != null) entry.exit();        // 退出, 写统计数据
}

// 方式 ② 注解层 (项目用的, 推荐)
@SentinelResource("loginResource")
public Result<String> login(UserLoginDTO dto) { ... }

// 方式 ③ Web 自动适配 (Sentinel 自动给所有 HTTP 接口创建 Resource)
// 资源名规则: HTTP method + URL path  (例如 "POST:/user/login")
// 引入 sentinel-spring-webmvc-adapter 后自动生效
```

---

### 34.2.4  @SentinelResource 注解定义（全字段）

📁 来自 sentinel-annotation-aspectj jar：`com.alibaba.csp.sentinel.annotation.SentinelResource`

```java
// Sentinel 源码（简化）
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SentinelResource {
    String value() default "";                  // 资源名

    EntryType entryType() default EntryType.OUT; // 入口 / 出口

    // 限流/熔断时的降级方法
    String blockHandler() default "";
    Class<?>[] blockHandlerClass() default {};  // 跨类降级时用

    // 业务异常时的降级方法
    String fallback() default "";
    Class<?>[] fallbackClass() default {};
    Class<? extends Throwable>[] exceptionsToTrace() default { Throwable.class };
    Class<? extends Throwable>[] exceptionsToIgnore() default {};

    String defaultFallback() default "";        // 通用兜底
}
```

**项目里 login 用到的字段（对照）**：

```java
@SentinelResource(
    value = "loginResource",        // ← name
    blockHandler = "loginBlock",    // ← 限流降级
    fallback = "loginFallback"      // ← 业务异常降级
)
```

---

### 34.2.5  Rule：限流规则的完整字段

📁 来自 sentinel-core jar：`com.alibaba.csp.sentinel.slots.block.flow.FlowRule`

```java
// Sentinel 源码（简化）
public class FlowRule extends AbstractRule {
    // 继承自 AbstractRule
    private String resource;        // 资源名
    private String limitApp;        // 针对的调用方, default=所有

    // FlowRule 自己的字段
    private int grade = 1;          // 0=并发线程数, 1=QPS
    private double count;           // 阈值
    private int strategy = 0;       // 0=直接 1=关联 2=链路
    private String refResource;     // strategy=1/2 时关联的资源
    private int controlBehavior = 0;// 0=快速失败 1=Warm Up 2=排队等待
    private int warmUpPeriodSec = 10;
    private int maxQueueingTimeMs = 500;
    private boolean clusterMode;    // 集群限流
}
```

**代码层手动配规则（不用 Dashboard）**：

```java
FlowRule rule = new FlowRule();
rule.setResource("loginResource");
rule.setGrade(1);          // QPS 模式
rule.setCount(2);          // 每秒 2 次
rule.setLimitApp("default");

FlowRuleManager.loadRules(List.of(rule));  // 加载进内存
```

**5 种 Rule 对应 5 个 Manager**：

| Rule 类 | Manager 类 | 作用 |
|---|---|---|
| `FlowRule` | `FlowRuleManager` | 限流 |
| `DegradeRule` | `DegradeRuleManager` | 熔断 |
| `SystemRule` | `SystemRuleManager` | 系统保护 |
| `AuthorityRule` | `AuthorityRuleManager` | 黑白名单 |
| `ParamFlowRule` | `ParamFlowRuleManager` | 热点参数限流 |

---

### 34.2.6  SlotChain：拦截器链（核心机制）

📁 来自 sentinel-core jar：`com.alibaba.csp.sentinel.slotchain.ProcessorSlotChain`

```java
// Sentinel 源码（简化）
public interface ProcessorSlot<T> {
    void entry(Context context, ResourceWrapper resourceWrapper,
               T param, int count, boolean prioritized, Object... args)
            throws Throwable;

    void exit(Context context, ResourceWrapper resourceWrapper,
              int count, Object... args);

    void fireEntry(...);   // 触发下一个 slot 的 entry
    void fireExit(...);
}
```

**默认 SlotChain（按 @Order 升序排列）**：

```
请求 SphU.entry("loginResource")
    ↓
①  NodeSelectorSlot       order=-10000  构建调用链路树(DefaultNode)
    │   把 Resource 跟当前线程 Context 挂上
    ↓
②  ClusterBuilderSlot     order=-9000   维护 ClusterNode
    │   ClusterNode = 一个 resource 全局唯一的聚合统计
    ↓
③  LogSlot                order=-8000   异常日志
    ↓
④  StatisticSlot          order=-7000   ⭐ 滑动窗口实时统计
    │   写入: passQps / blockQps / successQps / exceptionQps / rt
    │   用 LeapArray (跳跃数组) 实现 1s 内 60 个 100ms 窗口
    ↓
⑤  AuthoritySlot          order=-6000   黑白名单
    │   throw AuthorityException
    ↓
⑥  SystemSlot             order=-5000   系统保护(load/CPU/RT)
    │   throw SystemBlockException
    ↓
⑦  ParamFlowSlot          order=-3000   热点参数限流
    │   throw ParamFlowException
    ↓
⑧  FlowSlot               order=-2000   ⭐ 限流规则检查 (项目用的)
    │   throw FlowException
    ↓
⑨  DegradeSlot            order=-1000   ⭐ 熔断规则检查
    │   throw DegradeException
    ↓
全部通过 → entry.exit() 写统计 → 执行原方法
任何 slot 抛 BlockException → 不进原方法 → SentinelResourceAspect 调 blockHandler
```

---

### 34.2.7  FlowSlot 内部检查逻辑（伪代码）

```java
// Sentinel 源码（FlowSlot.checkFlow 简化版）
public class FlowSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    @Override
    public void entry(Context context, ResourceWrapper resource, ...) {

        // ① 查这个 resource 有哪些 FlowRule
        List<FlowRule> rules = FlowRuleManager.getRules(resource.getName());

        // ② 一个个检查
        for (FlowRule rule : rules) {

            // 从 StatisticSlot 写的统计里读当前 QPS
            double currentQps = node.passQps();

            // 阈值检查
            if (currentQps + count > rule.getCount()) {
                throw new FlowException(rule.getLimitApp(), rule);
                //         ↑ 抛 BlockException 子类
            }
        }

        // ③ 全部规则通过, 调下一个 slot
        fireEntry(context, resource, node, count, prioritized, args);
    }
}
```

---

### 34.2.8  BlockException 异常家族（完整代码）

📁 来自 sentinel-core jar：`com.alibaba.csp.sentinel.slots.block.BlockException`

```java
// 基类 (注意: 不继承 RuntimeException, 是直接 extends Exception)
public abstract class BlockException extends Exception {
    public static final String BLOCK_EXCEPTION_FLAG = "SentinelBlockException";

    protected AbstractRule rule;
    private String ruleLimitApp;

    public BlockException(String ruleLimitApp) { this.ruleLimitApp = ruleLimitApp; }
    public AbstractRule getRule() { return rule; }
    // ...
}
```

**5 个子类**（按触发场景）：

```java
// ① 限流: 项目里命中过
public class FlowException extends BlockException {
    public FlowException(String ruleLimitApp, AbstractRule rule) {
        super(ruleLimitApp); this.rule = rule;
    }
}

// ② 熔断: F2.6 会用到
public class DegradeException extends BlockException { ... }

// ③ 系统保护(load/CPU 超阈值): F2.8 会用到
public class SystemBlockException extends BlockException { ... }

// ④ 黑白名单 (按调用方限制)
public class AuthorityException extends BlockException { ... }

// ⑤ 热点参数限流 (例如 productId=1 的查询限流, productId=2 不限)
public class ParamFlowException extends BlockException { ... }
```

⚠️ **关键设计点 1：`BlockException extends Exception`，不是 RuntimeException**

意味着：
- 编译期受检 → 方法签名必须显式 `throws BlockException`
- Spring 默认异常处理器 `@ExceptionHandler` 接得到（Spring 处理 Throwable）
- 但**如果 GlobalExceptionHandler 只写了 `@ExceptionHandler(Exception.class)`，会兜住 BlockException**，可能误吞

**项目里的应对**：让 `@SentinelResource(blockHandler="loginBlock")` 在 GlobalExceptionHandler 之前处理掉，所以 BlockException 不会泄漏到 Spring。

**关键设计点 2：blockHandler 方法签名末尾必须显式声明 BlockException**

```java
// ✗ 错误：找不到 blockHandler
public Result<String> loginBlock(UserLoginDTO dto)            // 缺 BlockException

// ✗ 错误：找不到
public Result<String> loginBlock(UserLoginDTO dto, Exception ex) // 必须是 BlockException

// ✓ 正确
public Result<String> loginBlock(UserLoginDTO dto, BlockException ex)
```

Sentinel 用**反射**按方法名找 + 严格匹配签名，签名不对默认输出 `Blocked by Sentinel`。

---

### 34.2.9  Context：连接 Resource 和 SlotChain 的桥梁

📁 来自 sentinel-core jar：`com.alibaba.csp.sentinel.context.Context`

```java
// Sentinel 源码（简化）
public class Context {
    private final String name;          // Context 名字
    private DefaultNode entranceNode;   // 入口节点(链路根)
    private Entry curEntry;             // 当前 Entry
    private String origin = "";         // 调用方标识 (limitApp 来源)
    // ...

    // 每个线程一个 Context (ThreadLocal)
    private static ThreadLocal<Context> contextHolder = new ThreadLocal<>();
}
```

**为什么需要 Context**：

```
同一个 Resource 在不同入口被调用时, 要分开统计。例如:
  loginResource 从 /user/login 入口 被调用 → 走 entrance-A
  loginResource 从 内部 admin 后台 被调用 → 走 entrance-B

通过 Context.entranceNode 区分两条链路, 限流规则可以"只限外部入口"。
```

**完整调用链路**：

```
SphU.entry("loginResource")
    ↓
ContextUtil.enter("sentinel_default_context")  ← 取/建 Context
    ↓ Context 关联到当前 ThreadLocal
ResourceWrapper res = new StringResourceWrapper("loginResource", IN);
    ↓
Entry entry = new CtEntry(res, slotChain, context);
    ↓
slotChain.entry(...)  ← ⭐ 进入 SlotChain
    ↓
... 8 个 slot 依次执行 ...
    ↓
返回 entry, 业务代码执行
    ↓
entry.exit() ← 触发 slotChain.exit() 写最终统计
```

---

## 34.3  F2 路线图（4 阶段 10 步）

```
阶段 1: 基础接入
  F2.0  Docker 启 Sentinel Dashboard
  F2.1  父 pom 加 sentinel-starter
  F2.2  3 服务 yml 配 Dashboard 地址
  F2.3  启动 + 验证 3 服务出现在 Dashboard

阶段 2: 单接口限流 + 降级
  F2.4  @SentinelResource + 自定义 BlockHandler
  F2.5  Dashboard 配 QPS=2 + curl 连发验证

阶段 3: 熔断 + 网关限流          ⏳ 待做
  F2.6  product /product/{id} 加异常比例熔断
  F2.7  gateway 接 sentinel-gateway-adapter 做全局限流

阶段 4: 系统保护 + 规则持久化      ⏳ 待做
  F2.8  系统级保护规则（load/CPU）
  F2.9  规则持久化到 Nacos（避免 Dashboard 重启规则丢）
```

本笔记记录 **F2.0~F2.5（阶段 1+2，已完成）**，F2.6~F2.9 留待后续。

---

## 34.4  F2.0：装 Sentinel Dashboard（Docker）

### 34.4.1  启动命令

```powershell
docker run -d --name minimall-sentinel `
  -p 8858:8858 `
  bladex/sentinel-dashboard:1.8.6
```

### 34.4.2  参数解释

| 参数 | 含义 |
|---|---|
| `-d` | 后台跑 |
| `--name minimall-sentinel` | 容器名，方便后续 stop/rm |
| `-p 8858:8858` | 容器 8858 (HTTP+UI) → 本机 8858 |
| `bladex/sentinel-dashboard:1.8.6` | 社区维护的 Dashboard 镜像（阿里官方没有 Docker Hub 镜像，用这个社区版）|

### 34.4.3  启动验证

```powershell
docker logs minimall-sentinel | Select-String "Started DashboardApplication"
# → "Started DashboardApplication in 1.957 seconds"
```

控制台：http://localhost:8858 （账号 `sentinel` / `sentinel`）

⚠️ **版本约束**：Sentinel Client 1.8.6+ 对应 SCA 2023.0.1.2（项目用的），Dashboard 用 1.8.6 完美兼容。

---

## 34.5  F2.1：父 pom 加 sentinel-starter

📁 `mini-mall-cloud/pom.xml` 的 `<dependencies>` 段：

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-bootstrap</artifactId>
    </dependency>

    <!-- E: Nacos 服务发现 -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>

    <!-- F1: Nacos 配置中心 -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
    </dependency>

    <!-- ⭐ F2 新增：Sentinel 限流熔断
         - 启动时自动连接 Dashboard 上报心跳
         - 拉取规则到本地内存
         - 拦截 @SentinelResource 标的方法做流控 -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
    </dependency>
</dependencies>
```

**为什么放父 pom**：跟 nacos-discovery / nacos-config 同处，3 个服务全要用，写一次省事。

---

## 34.6  F2.2：3 服务 yml 配置

3 个 yml 都加同样的 sentinel 配置块，**只有端口号要错开**（避免本机内冲突）。

### 34.6.1  user-service yml

📁 `mini-mall-user/src/main/resources/application.yml`

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848

    # ═════════════════════════════════════════════════════════
    # F2 新增：Sentinel 配置
    # transport.dashboard = Dashboard 地址(服务上报心跳给它)
    # transport.port       = 客户端开一个端口给 Dashboard 反向通信
    #                       0 = 随机端口(避免冲突)
    # eager = true: 启动立即连接(默认 false 是首次调用时才连)
    # ═════════════════════════════════════════════════════════
    sentinel:
      transport:
        dashboard: 127.0.0.1:8858
        port: 8719
      eager: true
```

### 34.6.2  product-service yml

📁 `mini-mall-product/src/main/resources/application.yml`

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: 127.0.0.1:8858
        port: 8720          # 跟 user 的 8719 错开避免冲突
      eager: true
```

### 34.6.3  gateway yml

📁 `mini-mall-gateway/src/main/resources/application.yml`

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: 127.0.0.1:8858
        port: 8721          # 跟 user(8719) / product(8720) 错开
      eager: true
```

### 34.6.4  关键字段解释

| 字段 | 作用 | 注意点 |
|---|---|---|
| `transport.dashboard` | Dashboard 地址 | **必须用 `127.0.0.1`，不要用 `localhost`**（IPv6 坑见 34.12）|
| `transport.port` | 客户端本地监听端口 | Dashboard 向客户端反向通信（推规则、拉指标），**多服务必须错开** |
| `eager` | 是否启动立即连 | `true` = 立即；`false` = 首次调用时才连（默认）|

---

## 34.7  F2.3：重打 + 启动 + Dashboard 验证

### 34.7.1  重打 3 个 jar

```powershell
cd "C:\Users\liyuq\OneDrive\桌面\Java学习代码\mini-mall-cloud"
mvn -pl mini-mall-user,mini-mall-product,mini-mall-gateway clean package -DskipTests
```

### 34.7.2  按顺序启 3 服务

```powershell
# 终端 1: user
& "D:\jdk-21.0.11\bin\java.exe" -jar "...\mini-mall-user-0.0.1-SNAPSHOT.jar"

# 终端 2: product
& "D:\jdk-21.0.11\bin\java.exe" -jar "...\mini-mall-product-0.0.1-SNAPSHOT.jar"

# 终端 3: gateway (最后)
& "D:\jdk-21.0.11\bin\java.exe" -jar "...\mini-mall-gateway-0.0.1-SNAPSHOT.jar"
```

### 34.7.3  Sentinel 客户端"懒注册"特性

⚠️ **关键认知**：即使 `eager: true`，Dashboard 上**也要等接口被第一次调用**才出现该 resource。

启动后先发几个请求触发：

```powershell
curl.exe -s http://localhost:9001/user/1
curl.exe -s http://localhost:9002/product/1
```

### 34.7.4  验证 Dashboard 应用列表

```powershell
# 登录 Dashboard 拿 cookie
curl.exe -s -X POST -c "$env:TEMP\sen.cookie" `
  -d "username=sentinel&password=sentinel" `
  "http://localhost:8858/auth/login"

# 查询应用列表
curl.exe -s -b "$env:TEMP\sen.cookie" "http://localhost:8858/app/names.json"
```

**期望响应**：

```json
{"success":true,"data":[
  "mini-mall-gateway",
  "mini-mall-user",
  "sentinel-dashboard",
  "mini-mall-product"
]}
```

3 个微服务 + Dashboard 自身 = 4 个应用全部上报 ✅

---

## 34.8  F2.4：@SentinelResource + BlockHandler + Fallback

### 34.8.1  改动文件

📁 `mini-mall-user/src/main/java/com/minimall/user/controller/UserController.java`

### 34.8.2  添加 import

```java
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
```

### 34.8.3  给 login 加注解 + 添加 2 个降级方法

```java
/**
 * ③ 登录返 JWT (F2 加 Sentinel 限流)
 *
 * @SentinelResource 三个参数:
 *   value = "loginResource"
 *     → 资源名, Dashboard 上配规则时按这个名字
 *
 *   blockHandler = "loginBlock"
 *     → 被【限流/熔断/系统保护】拦截时调这个方法
 *     → 同类里, 方法签名必须跟原方法【参数+返回值】完全一致, 末尾加 BlockException
 *
 *   fallback = "loginFallback"
 *     → 业务【抛 RuntimeException】时调这个 (跟 blockHandler 互补)
 *     → 注意: BusinessException 也会触发 fallback
 */
@PostMapping("/login")
@SentinelResource(
        value = "loginResource",
        blockHandler = "loginBlock",
        fallback = "loginFallback"
)
public Result<String> login(@Valid @RequestBody UserLoginDTO dto) {
    return Result.success(userService.login(dto));
}

/**
 * Sentinel 限流降级方法
 *
 * 必须满足:
 *   ① public 方法
 *   ② 跟原方法【同一个类】(或 blockHandlerClass 指定外部类)
 *   ③ 返回值类型 + 参数列表跟原方法一致, 末尾追加 BlockException
 *
 * Sentinel 拦下请求后会调这里, 我们返一个友好提示, 不让前端看到 500
 */
public Result<String> loginBlock(UserLoginDTO dto, BlockException ex) {
    // ex.getClass().getSimpleName() 可以拿到具体哪个规则触发
    // FlowException = 限流  DegradeException = 熔断  SystemBlockException = 系统保护
    return Result.error(429, "登录请求太频繁, 请 1 秒后再试 (触发规则: "
            + ex.getClass().getSimpleName() + ")");
}

/**
 * Sentinel 业务异常降级方法
 *
 * 触发场景: login 方法内部抛 RuntimeException (例如 BusinessException "密码错")
 * 注意: 如果不写 fallback, 这种异常会直接被 GlobalExceptionHandler 接住
 *
 * 这里我们仍然让原异常透传走, 让 GlobalExceptionHandler 处理 (跟原逻辑保持一致)
 * → 所以 fallback 实际上是把异常重新抛出
 */
public Result<String> loginFallback(UserLoginDTO dto, Throwable ex) {
    // 把原异常透传出去, 让 GlobalExceptionHandler 处理
    if (ex instanceof RuntimeException re) throw re;
    throw new RuntimeException(ex);
}
```

### 34.8.4  代码逐块解释

| 部分 | 解释 |
|---|---|
| `value = "loginResource"` | 资源名，Dashboard 上配规则时按这个名字找。**不写默认是"类名.方法名"** |
| `blockHandler = "loginBlock"` | 限流/熔断时回调的方法名。**注意只是方法名字符串**，Sentinel 用反射找 |
| `fallback = "loginFallback"` | 业务异常（RuntimeException）时回调 |
| `blockHandler` 签名 | 必须 `Result<String> loginBlock(UserLoginDTO dto, BlockException ex)` — 跟原方法参数一致 + 末尾追加 `BlockException` |
| `fallback` 签名 | 类似，但末尾参数是 `Throwable ex`（覆盖所有异常）|
| `instanceof RuntimeException re` | Java 17 模式匹配语法，简化 cast |

### 34.8.5  BlockHandler vs Fallback 区别

| | BlockHandler | Fallback |
|---|---|---|
| 触发场景 | Sentinel 规则拦截（限流/熔断）| 业务方法抛 Throwable |
| 异常类型 | `BlockException` | `Throwable`（含所有异常）|
| 都写时优先级 | BlockException 走 BlockHandler，其他走 Fallback | - |

---

## 34.9  F2.5：配限流规则 + curl 压测

### 34.9.1  关键概念：流控规则的 5 个字段

| 字段 | 值 | 含义 |
|---|---|---|
| `resource` | `loginResource` | 资源名 |
| `grade` | `1` | 阈值类型：0=并发线程数，1=QPS |
| `count` | `2.0` | 阈值（每秒最多 2 个请求）|
| `strategy` | `0` | 流控模式：0=直接，1=关联，2=链路 |
| `controlBehavior` | `0` | 流控效果：0=快速失败，1=Warm Up，2=排队等待 |
| `limitApp` | `default` | 针对的调用方，默认所有 |

### 34.9.2  用 Dashboard API 配规则

```powershell
# 1. 登录拿 cookie
curl.exe -s -X POST -c "$env:TEMP\sen.cookie" `
  -d "username=sentinel&password=sentinel" `
  "http://localhost:8858/auth/login"

# 2. 准备规则 JSON
$ruleJson = '{"resource":"loginResource","grade":1,"count":2,"strategy":0,"controlBehavior":0,"clusterMode":false,"limitApp":"default","app":"mini-mall-user","ip":"host.docker.internal","port":8719}'
Set-Content -Path "$env:TEMP\rule.json" -Value $ruleJson -Encoding ascii -NoNewline

# 3. POST 给 Dashboard
curl.exe -s -b "$env:TEMP\sen.cookie" -X POST `
  -H "Content-Type: application/json" `
  --data-binary "@$env:TEMP\rule.json" `
  "http://localhost:8858/v2/flow/rule"
```

**期望响应**：
```json
{"success":true, "data":{"id":1, "resource":"loginResource", "count":2.0, ...}}
```

⚠️ **注意 `ip` 字段**：Dashboard 在 Docker 容器内，反向连客户端用 `host.docker.internal`（Docker 内置的"宿主机"DNS 名）。直接写 `127.0.0.1` 在容器内会指向容器自己。

### 34.9.3  方式 B：Dashboard UI 手动配（更直观）

1. 浏览器 http://localhost:8858 → 登录
2. 左侧选 `mini-mall-user` → "簇点链路"
3. 找到 `loginResource` → 点 "+流控"
4. 填：阈值类型 `QPS`、单机阈值 `2`
5. 新增

### 34.9.4  压测验证：1 秒连发 10 次

```powershell
1..10 | ForEach-Object {
    $r = curl.exe -s -o "$env:TEMP\r.txt" -w "%{http_code}" `
        -X POST -H "Content-Type: application/json" `
        --data-binary "@$env:TEMP\login.json" `
        http://localhost:9001/user/login
    $body = Get-Content "$env:TEMP\r.txt" -Raw
    "$_. HTTP $r | $body"
}
```

### 34.9.5  实测结果

```
1. HTTP 200 | {"code":200,"data":"eyJhbGc..."}        ← 第 1 次, 放行
2. HTTP 200 | {"code":200,"data":"eyJhbGc..."}        ← 第 2 次, 放行 (达到 QPS=2)
3. HTTP 200 | {"code":429,"message":"FlowException"}  ← 第 3 次起, 被限流!
4. HTTP 200 | {"code":429,"message":"FlowException"}
...
10. HTTP 200 | {"code":429,"message":"FlowException"}
```

⚠️ **HTTP 状态码仍然是 200**：因为我们的 BlockHandler 返的是 `Result.error(429, ...)`，包在 200 响应体里。这是常规做法 —— 用业务 code 区分状态，而不是 HTTP code。**前端通过 `data.code` 判断**。

✅ **F2.5 完美验证** — 限流 + 自定义降级生效。

---

## 34.10  Sentinel 工作流程（源码级深入）

### 34.10.1  全链路总览（请求 → BlockHandler 回调）

```
client → curl POST /user/login
   ↓
Tomcat 接收, DispatcherServlet 派发给 UserController.login(dto)
   ↓
SentinelResourceAspect (AOP 切入)
   ↓
   ├─ ① SphU.entry("loginResource")  进入 SlotChain
   ├─ ② 8 个 slot 依次过
   ├─ ③ 通过 → 反射调原 login() → entry.exit()
   └─ ④ 抛 BlockException → 反射调 blockHandler
```

---

### 34.10.2  ① SentinelResourceAspect 拦截源码

📁 `com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect`

```java
@Aspect
public class SentinelResourceAspect extends AbstractSentinelAspectSupport {

    // 切点: 所有带 @SentinelResource 的方法
    @Pointcut("@annotation(com.alibaba.csp.sentinel.annotation.SentinelResource)")
    public void sentinelResourceAnnotationPointcut() { }

    // 环绕通知
    @Around("sentinelResourceAnnotationPointcut()")
    public Object invokeResourceWithSentinel(ProceedingJoinPoint pjp) throws Throwable {

        Method originMethod = resolveMethod(pjp);
        SentinelResource annotation = originMethod.getAnnotation(SentinelResource.class);

        // 资源名: 注解 value > 类名.方法名
        String resourceName = getResourceName(annotation.value(), originMethod);
        EntryType entryType = annotation.entryType();
        int resourceType = annotation.resourceType();

        Entry entry = null;
        try {
            // ⭐⭐ 核心: SphU.entry 进入 SlotChain
            entry = SphU.entry(resourceName, resourceType, entryType, pjp.getArgs());

            // 通过, 调用原方法
            return pjp.proceed();

        } catch (BlockException ex) {
            // 被任何 slot 拦下, 调 blockHandler
            return handleBlockException(pjp, annotation, ex);

        } catch (Throwable ex) {
            // 业务异常, 调 fallback
            return handleFallback(pjp, annotation, ex);

        } finally {
            if (entry != null) entry.exit(1, pjp.getArgs());  // 写统计 + 释放
        }
    }
}
```

---

### 34.10.3  ② SphU.entry 进入 SlotChain（源码）

📁 `com.alibaba.csp.sentinel.SphU`

```java
public class SphU {
    public static Entry entry(String name, int resourceType, EntryType type, Object[] args)
            throws BlockException {
        return Env.sph.entry(name, type, 1, args);   // 转发给 CtSph
    }
}

// CtSph 是 SphU 背后的执行者
public class CtSph implements Sph {

    public Entry entry(...) throws BlockException {
        // 1) 包装 Resource
        StringResourceWrapper resource = new StringResourceWrapper(name, type);

        // 2) 取或创建 SlotChain (一个 Resource 一条链)
        ProcessorSlot<Object> chain = lookProcessChain(resource);
        if (chain == null) {
            return new CtEntry(resource, null, ContextUtil.getContext());  // 兜底直通
        }

        // 3) 取当前线程 Context
        Context context = ContextUtil.getContext();
        Entry entry = new CtEntry(resource, chain, context);

        try {
            // ⭐ 4) 进入 SlotChain
            chain.entry(context, resource, null, count, prioritized, args);
        } catch (BlockException e1) {
            entry.exit(count, args);   // 异常路径也要 exit, 写 blockQps
            throw e1;
        }

        return entry;
    }
}
```

---

### 34.10.4  ③ SlotChain 链式调用（fireEntry 机制）

📁 `com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot`

```java
// 每个 slot 都继承这个基类, 自带链式调用能力
public abstract class AbstractLinkedProcessorSlot<T> implements ProcessorSlot<T> {

    private AbstractLinkedProcessorSlot<?> next = null;   // ← 指向下一个 slot

    @Override
    public void fireEntry(Context context, ResourceWrapper resource,
                          Object obj, int count, boolean prioritized, Object... args)
            throws Throwable {
        if (next != null) {
            next.transformEntry(context, resource, obj, count, prioritized, args);
            //   ↑ 调用链上下一个 slot
        }
    }
}
```

**每个 slot 的处理模板**：

```java
public class XxxSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    @Override
    public void entry(Context context, ResourceWrapper resource, ...) throws Throwable {

        // 1) 检查本 slot 的规则
        if (违反规则) {
            throw new BlockException(...);   // 整条链断, 不调 fireEntry
        }

        // 2) 通过 → 调下一个 slot
        fireEntry(context, resource, node, count, prioritized, args);
    }
}
```

⭐ **关键：一旦哪个 slot 抛 BlockException，后面的 slot 全不执行，直接回到 SphU.entry 的 catch。**

---

### 34.10.5  ④ StatisticSlot：滑动窗口实时统计

📁 `com.alibaba.csp.sentinel.slots.statistic.StatisticSlot`

```java
public class StatisticSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    @Override
    public void entry(Context context, ResourceWrapper resource,
                      DefaultNode node, int count, ...) throws Throwable {
        try {
            // 1) 先调下游 slot (FlowSlot/DegradeSlot...)
            fireEntry(context, resource, node, count, prioritized, args);

            // 2) 下游全通过 → 写统计
            node.addPassRequest(count);                          // pass + N
            node.increaseThreadNum();                            // 并发线程数 +1
            context.getCurEntry().getOriginNode().addPassRequest(count);

        } catch (BlockException e) {
            // 3) 被限流 → 写 blockQps
            node.increaseBlockQps(count);
            throw e;
        } catch (Throwable e) {
            // 4) 业务异常 → 写 exceptionQps
            node.increaseExceptionQps(count);
            throw e;
        }
    }
}
```

**滑动窗口数据结构（LeapArray）**：

```java
// 默认 1 秒内 2 个 500ms 窗口 (intervalInMs=1000, sampleCount=2)
//
// 时间轴:
//   |---- 窗口0 ----|---- 窗口1 ----|---- 窗口0 ----|----  窗口1 ----|
//   0ms          500ms         1000ms          1500ms          2000ms
//
// 当前时间 700ms → 落在窗口1
//   passQps = 窗口0.pass + 窗口1.pass  (滑动 1 秒内的总和)
//
// LeapArray<MetricBucket> 用循环数组实现, 避免每秒新建对象, 性能极高

public abstract class LeapArray<T> {
    protected final AtomicReferenceArray<WindowWrap<T>> array;
    protected final int sampleCount;     // 几个窗口
    protected final int intervalInMs;    // 总时长
    protected final int windowLengthInMs; // 每窗口时长

    public WindowWrap<T> currentWindow(long timeMillis) {
        // 算出当前时间该落到哪个窗口
        int idx = calculateTimeIdx(timeMillis);
        long windowStart = calculateWindowStart(timeMillis);

        WindowWrap<T> old = array.get(idx);
        if (old == null) {
            // 创建新窗口, CAS 保证线程安全
            WindowWrap<T> window = new WindowWrap<>(windowLengthInMs, windowStart, newEmptyBucket());
            if (array.compareAndSet(idx, null, window)) return window;
        }
        // ...
    }
}
```

---

### 34.10.6  ④ FlowSlot：限流规则检查

📁 `com.alibaba.csp.sentinel.slots.block.flow.FlowSlot`

```java
public class FlowSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    @Override
    public void entry(Context context, ResourceWrapper resource,
                      DefaultNode node, int count, ...) throws Throwable {
        checkFlow(resource, context, node, count, prioritized);
        fireEntry(context, resource, node, count, prioritized, args);
    }

    void checkFlow(ResourceWrapper resource, Context context, DefaultNode node, int count, ...) {

        // 1) 查这个 resource 的所有 FlowRule
        List<FlowRule> rules = FlowRuleManager.getFlowRules(resource.getName());

        // 2) 一个个 check
        for (FlowRule rule : rules) {
            if (!canPassCheck(rule, context, node, count, prioritized)) {
                throw new FlowException(rule.getLimitApp(), rule);
                //         ↑ 抛 BlockException 子类
            }
        }
    }

    boolean canPassCheck(FlowRule rule, Context context, DefaultNode node, int count, ...) {
        // 调到底层 TrafficShapingController
        // grade=1 (QPS): 从 ClusterNode 读 passQps, 跟 rule.count 比
        return rule.getRater().canPass(node, count, prioritized);
    }
}
```

---

### 34.10.7  ⑤ 抛 BlockException 后的反射回调

📁 `com.alibaba.csp.sentinel.annotation.aspectj.AbstractSentinelAspectSupport`

```java
protected Object handleBlockException(ProceedingJoinPoint pjp,
                                       SentinelResource annotation,
                                       BlockException ex) throws Throwable {

    // 1) 找 blockHandler 方法 (annotation.blockHandler() = "loginBlock")
    Method blockHandlerMethod = extractBlockHandlerMethod(pjp,
            annotation.blockHandler(),
            annotation.blockHandlerClass());

    if (blockHandlerMethod != null) {
        // 2) 拼参数: 原方法参数 + BlockException
        Object[] originArgs = pjp.getArgs();
        Object[] args = new Object[originArgs.length + 1];
        System.arraycopy(originArgs, 0, args, 0, originArgs.length);
        args[args.length - 1] = ex;

        // 3) 反射调用
        if (Modifier.isStatic(blockHandlerMethod.getModifiers())) {
            return blockHandlerMethod.invoke(null, args);
        } else {
            return blockHandlerMethod.invoke(pjp.getTarget(), args);
        }
    }

    // 没找到 blockHandler → 默认输出
    return handleFallback(pjp, annotation.fallback(), annotation.defaultFallback(), null, ex);
}

// 找方法: 按名字 + 参数类型严格匹配
private Method extractBlockHandlerMethod(ProceedingJoinPoint pjp,
                                          String name, Class<?>[] locationClass) {
    Class<?> clazz = locationClass.length > 0 ? locationClass[0] : pjp.getTarget().getClass();

    // 原方法参数类型
    Class<?>[] parameterTypes = ((MethodSignature) pjp.getSignature()).getParameterTypes();

    // 拼上 BlockException
    Class<?>[] signature = new Class<?>[parameterTypes.length + 1];
    System.arraycopy(parameterTypes, 0, signature, 0, parameterTypes.length);
    signature[signature.length - 1] = BlockException.class;

    return findMethod(clazz, name, signature);   // 反射查
}
```

---

### 34.10.8  项目里 login 限流的完整调用栈（实测）

```
HTTP POST /user/login (第 3 次, 期望被限流)
   ↓
Tomcat NioEndpoint → DispatcherServlet
   ↓
HandlerMapping 找到 UserController.login(dto)
   ↓
SentinelResourceAspect.invokeResourceWithSentinel(pjp)
   ↓
   SphU.entry("loginResource", 0, OUT, [dto])
       ↓
   CtSph.entry(...)
       ↓
   chain.entry(context, resource, ...)
       ↓
   ┌── NodeSelectorSlot.entry()     找/建 DefaultNode → fireEntry()
   ├── ClusterBuilderSlot.entry()   ClusterNode passQps=2 → fireEntry()
   ├── LogSlot.entry()              → fireEntry()
   ├── StatisticSlot.entry()
   │     try { fireEntry() }            ← 进入下游
   │     catch BlockException
   │     │     node.increaseBlockQps(1)  ← 写统计
   │     │     throw
   │     │
   │     ↓ 下游:
   │     AuthoritySlot.entry()      → fireEntry()
   │     SystemSlot.entry()         → fireEntry()
   │     ParamFlowSlot.entry()      → fireEntry()
   │     FlowSlot.entry()
   │         rules = [FlowRule{count=2}]
   │         currentQps = 3 > 2
   │         ⭐ throw new FlowException("default", rule)
   │
   ↑ 异常一路向上抛
   SphU.entry catch BlockException
   ↑
SentinelResourceAspect catch BlockException
   ↓ 进入 handleBlockException()
   找 method "loginBlock"(UserLoginDTO, BlockException) ← 反射
   找到 ✓
   blockHandlerMethod.invoke(controller, dto, flowEx)
   ↓
loginBlock(dto, FlowException) 执行
   return Result.error(429, "登录请求太频繁... (触发规则: FlowException)")
   ↓
Spring MVC 序列化 → response body
   ↓
client 收到 {"code":429,"message":"...FlowException..."}
```

---

### 34.10.9  关键源码定位（自己看源码用）

| 看什么 | 类全限定名 |
|---|---|
| 注解定义 | `com.alibaba.csp.sentinel.annotation.SentinelResource` |
| AOP 切面 | `com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect` |
| 入口 API | `com.alibaba.csp.sentinel.SphU` / `com.alibaba.csp.sentinel.SphO` |
| 链路装配 | `com.alibaba.csp.sentinel.CtSph` |
| 默认链工厂 | `com.alibaba.csp.sentinel.slots.DefaultSlotChainBuilder` |
| 限流 slot | `com.alibaba.csp.sentinel.slots.block.flow.FlowSlot` |
| 熔断 slot | `com.alibaba.csp.sentinel.slots.block.degrade.DegradeSlot` |
| 统计 slot | `com.alibaba.csp.sentinel.slots.statistic.StatisticSlot` |
| 滑动窗口 | `com.alibaba.csp.sentinel.slots.statistic.base.LeapArray` |
| 规则中心 | `FlowRuleManager` / `DegradeRuleManager` / `SystemRuleManager` |

---

## 34.11  F2 三大坑（真实踩过）

### 坑 ①：IPv6 + Windows 杀软导致 `Permission denied: getsockopt`

**现象**：

```
ERROR GrpcClient: Server check fail, port 9848 ...
Caused by: AnnotatedSocketException: Permission denied:
   getsockopt: localhost/[0:0:0:0:0:0:0:1]:9848
```

**根因**：
- yml 用 `localhost`
- Windows 解析 `localhost` 优先 IPv6 `::1`
- 杀毒软件（Windows Defender / 360 等）阻止 IPv6 套接字操作

**修复**：所有 yml 把 `localhost:xxxx` 改成 `127.0.0.1:xxxx`，强制 IPv4。

**教训**：Windows + Docker + Nacos 场景下，永远用 `127.0.0.1` 别用 `localhost`。

### 坑 ②：Nacos 容器 OOM 静默退出

**现象**：第二天回来重启服务全部连不上 Nacos，`Connection refused: getsockopt: /127.0.0.1:9848`

**排查**：

```powershell
docker ps -a --filter "name=nacos"
# minimall-nacos | Exited (137) 10 hours ago
```

**Exit 137 = 128 + 9（SIGKILL）**，常见原因：
- Docker 内存限制触发 OOMKiller
- 系统资源不足，Docker Desktop 主动杀
- 笔记本休眠/睡眠后内存压力大

**修复**：`docker start minimall-nacos`

**教训**：开发期建议给 nacos 容器加内存限制 `-m 1g`，或者用 `--restart=unless-stopped` 自动重启。

### 坑 ③：BlockHandler 方法签名不匹配 → Sentinel 默认输出

**现象**：被限流的请求返回 `Blocked by Sentinel (flow limiting)`，不是我们写的友好消息。

**根因**：blockHandler 方法签名跟原方法不一致（参数缺了 dto，或没加 BlockException）。

**修复**：严格对照模板：

```java
原方法:
  public Result<String> login(UserLoginDTO dto)

blockHandler 必须:
  public Result<String> loginBlock(UserLoginDTO dto, BlockException ex)
                                   └ 一样 ┘  └ 末尾追加 ┘
```

**教训**：blockHandler 找不到 → Sentinel 用默认输出，不会报错也不会警告，**一定要测一次确认走自己的方法**。

---

## 34.12  F2 阶段成果对比

| 能力 | F1 后 | F2 后 |
|---|---|---|
| 限流 | ❌ | ✅ Dashboard 可视化 |
| 熔断 | ❌ | ⏳ F2.6 |
| 降级响应 | 500 兜底 | ✅ 自定义 BlockHandler |
| 网关全局限流 | ❌ | ⏳ F2.7 |
| 系统保护 | ❌ | ⏳ F2.8 |
| 规则持久化 | - | ⏳ F2.9 |
| 接口监控 | 无 | ✅ Dashboard 实时 QPS/RT |

---

## 34.13  F2 目录结构变化

```
mini-mall-cloud/
├── pom.xml                              ← ✏️ 父 dependencies 加 sentinel-starter
│
├── mini-mall-user/
│   └── src/main/
│       ├── java/com/minimall/user/
│       │   └── controller/
│       │       └── UserController.java  ← ✏️ login 加 @SentinelResource
│       │                                  ✏️ 新增 loginBlock / loginFallback
│       └── resources/
│           └── application.yml          ← ✏️ 加 cloud.sentinel
│                                         ✏️ localhost → 127.0.0.1
│
├── mini-mall-product/
│   └── src/main/resources/
│       └── application.yml              ← ✏️ 加 cloud.sentinel + 127.0.0.1
│
└── mini-mall-gateway/
    └── src/main/resources/
        └── application.yml              ← ✏️ 加 cloud.sentinel + 127.0.0.1
```

---

## 34.14  命令速查

### 34.14.1  Docker 管理

```bash
# 启 / 停 Sentinel Dashboard
docker start minimall-sentinel
docker stop  minimall-sentinel
docker logs --tail 20 minimall-sentinel

# 启 / 停 Nacos
docker start minimall-nacos
docker stop  minimall-nacos
```

### 34.14.2  Sentinel API（带 cookie）

```powershell
# 登录拿 cookie
curl.exe -s -X POST -c "$env:TEMP\sen.cookie" `
  -d "username=sentinel&password=sentinel" `
  "http://localhost:8858/auth/login"

# 列出所有应用
curl.exe -s -b "$env:TEMP\sen.cookie" "http://localhost:8858/app/names.json"

# 查某应用的资源（簇点链路）
curl.exe -s -b "$env:TEMP\sen.cookie" `
  "http://localhost:8858/resource/machineResource.json?app=mini-mall-user&ip=host.docker.internal&port=8719"

# 配限流规则
$rule = '{"resource":"xxx","grade":1,"count":2,"limitApp":"default","app":"mini-mall-user","ip":"host.docker.internal","port":8719}'
Set-Content "$env:TEMP\rule.json" $rule -Encoding ascii -NoNewline
curl.exe -s -b "$env:TEMP\sen.cookie" -X POST -H "Content-Type: application/json" `
  --data-binary "@$env:TEMP\rule.json" `
  "http://localhost:8858/v2/flow/rule"
```

### 34.14.3  压测脚本

```powershell
# 1 秒连发 N 次
$N = 10
1..$N | ForEach-Object {
    $r = curl.exe -s -o "$env:TEMP\r.txt" -w "%{http_code}" `
        -X POST -H "Content-Type: application/json" `
        --data-binary "@$env:TEMP\login.json" `
        http://localhost:9001/user/login
    "$_. HTTP $r | $(Get-Content $env:TEMP\r.txt -Raw)"
}
```

---

## 34.15  F2.6 实战：异常数熔断完整闭环

> F2.4/F2.5 做了"限流"（防爆量），F2.6 做"熔断"（防雪崩）。两者一对，构成 Sentinel 最常用的 80% 场景。

---

### 34.15.1  熔断 vs 限流的本质区别

| | 限流（FlowRule）| 熔断（DegradeRule）|
|---|---|---|
| **触发条件** | 请求**量**超过阈值 | 后端**表现**变差 |
| **目的** | 防上游打爆下游 | 防下游变差拖累上游 |
| **关注的指标** | "进来多少请求" | "处理结果如何"（异常率/RT）|
| **效果方向** | 拒绝多余流量 | 暂停一段时间，等下游恢复 |
| **现实类比** | 售票窗口限号 | 体检不达标停业整顿 |

---

### 34.15.2  熔断的 3 种策略（DegradeRule.grade）

| grade | 中文 | 触发逻辑 | 适用 |
|---|---|---|---|
| **0** | 慢调用比例 | 1 秒内 RT > 阈值 的占比 ≥ count | 下游变慢、有死锁风险 |
| **1** | 异常比例 | 1 秒内异常占比 ≥ count (范围 0~1) | 下游不稳定 |
| **2** | 异常数 | 1 秒内异常**绝对数** ≥ count | 低 QPS 接口（比例失真）|

---

### 34.15.3  熔断状态机（必懂）

```
┌─────────────────────────────────────┐
│  CLOSED (默认, 正常放行)             │
│  统计窗口内异常率 / RT 等指标         │
└────────┬────────────────────────────┘
         │ 触发条件满足
         ↓
┌─────────────────────────────────────┐
│  OPEN (熔断打开)                     │
│  所有请求秒拒 → DegradeException     │
│  → 调 blockHandler 兜底              │
│  持续 timeWindow 秒                   │
└────────┬────────────────────────────┘
         │ timeWindow 时间到
         ↓
┌─────────────────────────────────────┐
│  HALF-OPEN (试探阶段)                │
│  放 1 个请求探测下游恢复情况          │
│  ├─ 探测成功 → CLOSED                │
│  └─ 探测失败 → 回 OPEN, 再 timeWindow │
└─────────────────────────────────────┘
```

⭐ **核心思想**：熔断不是"永久关闭"，是"暂停 + 周期性试探"，避免下游恢复后还卡着不知道。

---

### 34.15.4  DegradeRule 完整字段

📁 来自 sentinel-core jar：`com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule`

```java
// Sentinel 源码（简化）
public class DegradeRule extends AbstractRule {

    // 公共字段(继承自 AbstractRule)
    private String resource;        // 资源名
    private String limitApp;        // 限制的调用方

    // DegradeRule 自己的字段
    private int grade = 1;          // 0=慢调用比例 1=异常比例 2=异常数
    private double count;           // 阈值(意义随 grade 变)
    private int timeWindow;         // 熔断持续秒数

    private int minRequestAmount = 5;     // 最小请求数(窗口内 < 这个数不统计)
    private double slowRatioThreshold = 1.0;  // 慢调用阈值(grade=0 用)
    private int statIntervalMs = 1000;    // 统计窗口毫秒
}
```

**3 种 grade 下的 count 含义**：

| grade | count 含义 | 取值范围 |
|---|---|---|
| 0 (慢调用比例) | RT 阈值毫秒 (例 100ms) | 1~999999 |
| 1 (异常比例) | 比例 0~1 (例 0.5) | **必须 0.0~1.0** |
| 2 (异常数) | 绝对数 (例 3) | 整数 |

⚠️ **F2.6 踩过的坑**：grade=1 时 count 必须是比例 (0.5)，不能写 50（百分比），否则 Dashboard 报错 `Ratio threshold should be in range: [0.0, 1.0]`。

---

### 34.15.5  ProductController 新增 flaky 接口

📁 `mini-mall-product/src/main/java/com/minimall/product/controller/ProductController.java`

#### 新增 import

```java
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import java.util.concurrent.ThreadLocalRandom;
```

#### 新增方法（在 list() 之后追加）

```java
// ════════════════════════════════════════════════════════════════
// F2.6 演示接口: 异常比例熔断
// ════════════════════════════════════════════════════════════════

/**
 * 不稳定接口 (30% 概率抛业务异常)
 *
 * 用来演示 Sentinel 熔断:
 *   ① 没规则时: 30% 请求失败, 70% 成功
 *   ② 配 DegradeRule(异常率 50%, minRequestAmount 5, timeWindow 10s):
 *       a) 1 秒内 < 5 个请求 → 不统计, 不熔断
 *       b) 1 秒内 ≥ 5 个请求, 异常率 ≥ 50% → 熔断 10 秒
 *       c) 熔断期间所有请求直接 DegradeException → blockHandler 兜底
 *       d) 10 秒后 HALF-OPEN, 放 1 个试探, 成功就 CLOSED, 失败回 OPEN
 */
@GetMapping("/flaky")
@SentinelResource(
        value = "flakyResource",
        blockHandler = "flakyBlock"
        // 不写 fallback: 业务异常正常抛, 让 Sentinel 统计为"异常"
)
public Result<String> flaky() {
    // 30% 抛业务异常 (会被 Sentinel 计入 exceptionQps)
    if (ThreadLocalRandom.current().nextInt(100) < 30) {
        throw new BusinessException("flaky 接口随机失败 (模拟下游不稳定)");
    }
    return Result.success("flaky ok");
}

/**
 * flaky 的限流/熔断降级方法
 *
 * Sentinel 拦下(熔断打开 / 限流触发)时调这里:
 *   - DegradeException: 熔断打开
 *   - FlowException: 限流触发
 *
 * 我们返一个 Result.error 友好提示, 不让前端看到 500
 */
public Result<String> flakyBlock(BlockException ex) {
    return Result.error(503, "下游服务繁忙, 暂时降级 (触发规则: "
            + ex.getClass().getSimpleName() + ")");
}
```

#### 代码逐行解释

| 行 | 解释 |
|---|---|
| `@SentinelResource(value = "flakyResource", blockHandler = "flakyBlock")` | 把方法标为受保护资源，资源名 `flakyResource` |
| `ThreadLocalRandom.current().nextInt(100) < 30` | **线程安全**的随机数（多线程下比 `Math.random()` 性能高 100x），30% 概率触发 |
| `throw new BusinessException(...)` | **故意抛业务异常** — Sentinel 会统计为 `exceptionQps` |
| **不写 `fallback`** | 让 BusinessException 自然向上传播 → Spring `GlobalExceptionHandler` 接住返 500 → Sentinel 在 `StatisticSlot` 中已经把它计入异常统计 |
| `blockHandler` 签名只有 `(BlockException ex)` | 原方法无参，所以降级方法签名末尾只追加 `BlockException`，前面没有其他参数 |
| `ex.getClass().getSimpleName()` | 区分是 `FlowException`（限流）还是 `DegradeException`（熔断），方便排查 |

⭐ **关键设计**：业务异常**不被** `fallback` 吞掉，Sentinel 才能统计到异常，触发熔断。如果业务异常被 `fallback` 转成成功响应，Sentinel 看不到"异常"，熔断永远不会触发。

---

### 34.15.6  尝试 1：异常比例熔断（grade=1）→ 未触发

#### 创建规则（异常率 ≥ 50% 触发）

```powershell
$ruleJson = '{"resource":"flakyResource","grade":1,"count":0.5,"timeWindow":10,"minRequestAmount":5,"statIntervalMs":1000,"slowRatioThreshold":1.0,"app":"mini-mall-product","ip":"192.168.101.49","port":8720,"limitApp":"default"}'
Set-Content "$env:TEMP\degrade.json" -Value $ruleJson -Encoding ascii -NoNewline
curl.exe -s -b "$env:TEMP\sen.cookie" -X POST `
  -H "Content-Type: application/json" `
  --data-binary "@$env:TEMP\degrade.json" `
  "http://localhost:8858/degrade/rule"
```

#### 字段解释

| 字段 | 值 | 含义 |
|---|---|---|
| `grade` | `1` | 异常比例 |
| `count` | `0.5` | 异常率 ≥ 50% 触发 |
| `timeWindow` | `10` | 熔断持续 10 秒 |
| `minRequestAmount` | `5` | 窗口内 < 5 个请求不统计（避免少量请求误判）|
| `statIntervalMs` | `1000` | 统计窗口 1 秒 |
| `ip` | `192.168.101.49` | **必须跟 Dashboard 上 product 的实际 IP 一致**（否则报 "given ip does not belong to given app"）|

#### 压测结果

```
30 次请求, 7 次异常 (实际异常率 ~23%)
→ 1 秒窗口内异常率 < 50%
→ 熔断未触发
→ 结论: 异常比例阈值设得太高, 不适合 30% 异常率的接口
```

---

### 34.15.7  尝试 2：异常数熔断（grade=2）→ 触发成功 ✅

更换策略：用**绝对异常数**（更可控）。

#### 替换规则（1 秒内异常 ≥ 3 个触发）

```powershell
# 先删旧规则
curl.exe -s -b "$env:TEMP\sen.cookie" -X DELETE "http://localhost:8858/degrade/rule/1"

# 新规则
$ruleJson = '{"resource":"flakyResource","grade":2,"count":3,"timeWindow":10,"minRequestAmount":3,"statIntervalMs":1000,"slowRatioThreshold":1.0,"app":"mini-mall-product","ip":"192.168.101.49","port":8720,"limitApp":"default"}'
Set-Content "$env:TEMP\degrade.json" -Value $ruleJson -Encoding ascii -NoNewline
curl.exe -s -b "$env:TEMP\sen.cookie" -X POST `
  -H "Content-Type: application/json" `
  --data-binary "@$env:TEMP\degrade.json" `
  "http://localhost:8858/degrade/rule"
```

#### grade=2 字段说明

| 字段 | 值 | 含义 |
|---|---|---|
| `grade` | `2` | 异常数 |
| `count` | `3` | 1 秒内 ≥ 3 个异常触发 |
| `minRequestAmount` | `3` | 窗口内 ≥ 3 个请求才统计 |
| `timeWindow` | `10` | 熔断 10 秒 |

---

### 34.15.8  压测验证 50 次（完整实测）

```powershell
1..50 | ForEach-Object {
    $r = curl.exe -s -w "%{http_code}" -o "$env:TEMP\f.txt" `
        http://localhost:9002/product/flaky
    $body = Get-Content "$env:TEMP\f.txt" -Raw
    $tag = if ($body -match '"code":200') { "✓ ok      " }
           elseif ($body -match 'DegradeException') { "🔥 DEGRADE" }
           elseif ($body -match '"code":500') { "✗ bus500  " }
           else { "?         " }
    "[$_]  $tag"
}
```

#### 实测结果（关键节选）

```
[1-12]   ✓ ok          ← 全成功 (随机运气好)
[13]     ✗ bus500       ← 第 1 个异常
[14]     ✓
[15-16]  ✗ bus500       ← 但与前面太分散, 1 秒窗口未累计 3 个
...
[28]     ✗ bus500
[29-30]  ✓
[31]     ✗ bus500
...
[34-35]  ✗ bus500       ← ⭐ 1 秒内累计 3 个异常
[36]     🔥 DEGRADE     ← ⭐ 熔断打开!
[37]-[50] 🔥 DEGRADE    ← 后续 15 个请求全部秒拒
```

⭐ **观察**：从 [36] 开始**业务方法不再执行**，全部走 `flakyBlock` 返 `Result.error(503, "...DegradeException...")`。

---

### 34.15.9  等 10 秒验证 HALF-OPEN → CLOSED 恢复

```powershell
"=== 等 10 秒让熔断窗口结束... ==="
Start-Sleep -Seconds 11

"=== 此时熔断进入 HALF-OPEN, 发 3 个试探请求 ==="
1..3 | ForEach-Object {
    $body = curl.exe -s http://localhost:9002/product/flaky
    $tag = if ($body -match '"code":200') { "✓ ok (恢复 CLOSED)" }
           elseif ($body -match 'DegradeException') { "🔥 DEGRADE (仍 OPEN)" }
           else { "✗ 业务错" }
    "[第${_}次试探]  $tag"
    Start-Sleep -Milliseconds 300
}
```

#### 实测

```
[第1次试探]  ✓ ok (恢复 CLOSED)
[第2次试探]  ✓ ok
[第3次试探]  ✓ ok
```

✅ **熔断完整闭环**：CLOSED → OPEN → HALF-OPEN → CLOSED 全跑通。

---

### 34.15.10  DegradeSlot 内部源码

📁 `com.alibaba.csp.sentinel.slots.block.degrade.DegradeSlot`

```java
public class DegradeSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    @Override
    public void entry(Context context, ResourceWrapper resource,
                      DefaultNode node, int count, ...) throws Throwable {

        // 1) 查这个 resource 的所有 CircuitBreaker (DegradeRule 转成的)
        List<CircuitBreaker> circuitBreakers = DegradeRuleManager.getCircuitBreakers(resource.getName());

        for (CircuitBreaker cb : circuitBreakers) {
            // 2) 检查每个熔断器
            if (!cb.tryPass(context)) {
                // tryPass 内部:
                //   OPEN 状态 + 在 timeWindow 内 → 返 false
                //   OPEN 状态 + timeWindow 到了 → CAS 切到 HALF-OPEN, 放 1 个
                //   CLOSED → 返 true
                //   HALF-OPEN + 已放过 1 个 → 返 false
                throw new DegradeException(cb.getRule().getLimitApp(), cb.getRule());
            }
        }

        // 3) 全部通过, 调下一个 slot
        fireEntry(context, resource, node, count, prioritized, args);
    }

    @Override
    public void exit(Context context, ResourceWrapper resource, int count, ...) {
        // 业务方法跑完后, 通知所有 CircuitBreaker 统计这次结果
        Entry curEntry = context.getCurEntry();
        if (curEntry.getBlockError() != null) return;  // 被限流的不算

        List<CircuitBreaker> circuitBreakers = DegradeRuleManager.getCircuitBreakers(resource.getName());
        for (CircuitBreaker cb : circuitBreakers) {
            cb.onRequestComplete(context);
            // onRequestComplete 内部:
            //   ① 把本次耗时/异常写入滑动窗口
            //   ② 检查触发条件:
            //       grade=1: 异常率 ≥ count?
            //       grade=2: 异常数 ≥ count?
            //   ③ 满足 → CAS CLOSED → OPEN, 记录 OPEN 时间戳
        }
    }
}
```

#### 关键 CircuitBreaker 实现（异常数模式）

📁 `com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.ExceptionCircuitBreaker`

```java
public class ExceptionCircuitBreaker extends AbstractCircuitBreaker {

    private final int minRequestAmount;  // 最小请求数
    private final double threshold;       // 阈值(异常数)

    @Override
    public void onRequestComplete(Context context) {
        Entry entry = context.getCurEntry();
        SimpleErrorCounter counter = stat.currentWindow().value();

        if (entry.getError() != null) {     // 业务异常
            counter.getErrorCount().add(1);
        }
        counter.getTotalCount().add(1);

        // 检查是否触发熔断
        handleStateChangeWhenThresholdExceeded(stat);
    }

    private void handleStateChangeWhenThresholdExceeded(LeapArray<SimpleErrorCounter> stat) {
        // 累加所有窗口的统计
        long totalCount = 0, errorCount = 0;
        for (SimpleErrorCounter c : stat.values()) {
            totalCount += c.getTotalCount().sum();
            errorCount += c.getErrorCount().sum();
        }

        if (totalCount < minRequestAmount) return;  // 请求数不够, 不判断

        // grade=2 (异常数): errorCount >= threshold
        if (errorCount >= threshold) {
            // CAS 切到 OPEN
            transformToOpen();   // 记录 OPEN 时间戳, 启动 timeWindow 倒计时
        }
    }
}
```

---

### 34.15.11  F2.6 踩坑回顾

#### 坑 ①：grade=1 时 count 不能写 50（百分比）

```
请求: {"grade":1, "count":50}    ← 期望 50%
响应: "Ratio threshold should be in range: [0.0, 1.0]"

修复: {"grade":1, "count":0.5}   ← 必须是小数比例
```

#### 坑 ②：30% 异常率配 50% 阈值，永远触发不了

```
flaky 接口 30% 失败率
DegradeRule.count=0.5 (50%)
→ 实际异常率 < 阈值, 熔断不会触发

修复: 用 grade=2 异常数模式, 设 count=3 (绝对数), 更容易复现
```

#### 坑 ③：Dashboard API 的 ip 必须是 Sentinel 识别的 IP

```
配规则用 ip=host.docker.internal → "given ip does not belong to given app"

排查: 查 Dashboard machines.json:
  curl http://localhost:8858/app/mini-mall-product/machines.json
  → {"ip":"192.168.101.49","port":8720}

修复: 用 192.168.101.49
```

---

### 34.15.12  F2.6 目录变化

```
mini-mall-cloud/mini-mall-product/
└── src/main/java/com/minimall/product/
    └── controller/
        └── ProductController.java      ← ✏️ 新增 import + flaky() + flakyBlock()
```

只改一个文件，新增约 50 行代码。yml/pom **都没动**（F2.0~F2.3 已搞定基础）。

---

## 34.16  F2.7 实战：网关全局限流

> F2.4/F2.5 在 user-service 内部限流（接口层），F2.7 跳到网关层做"全公司流量入口"的总闸门。
> 学完知道为什么"两层都要"。

---

### 34.16.1  为什么网关也要限流（接口层不够吗）

```
F2.4/F2.5 只在 user-service 内部限流, 留下 3 个痛点:

  ① 上游打爆下游
     攻击者一秒 10w 个请求, 全转给 user-service, 网关自己撑不住

  ② 流量没"总闸门"
     100 个不同 URL 各自有规则, 但所有 URL 加起来不能超 X 怎么办?

  ③ 接口未上线就要保护
     新接口还没 @SentinelResource, 也要兜底
```

⭐ **网关全局限流 = 入口总闸门**。

---

### 34.16.2  Gateway 限流 vs 接口限流的本质差异

| | 普通 Sentinel（user/product） | Gateway Sentinel |
|---|---|---|
| 资源识别 | 注解 `@SentinelResource("xxx")` | 自动识别**路由 ID** 或 **API 分组** |
| 限流粒度 | 方法级 | 路径 / Header / IP / 参数 |
| Web 框架 | Spring MVC | Spring Cloud Gateway (WebFlux) |
| 客户端 jar | `spring-cloud-starter-alibaba-sentinel` | + `spring-cloud-alibaba-sentinel-gateway` |
| 异常处理 | `@SentinelResource(blockHandler=...)` | `GatewayCallbackManager.setBlockHandler(...)` |

---

### 34.16.3  Sentinel Gateway 的 2 种限流粒度

```
① 按路由 ID 限流 (RouteId, 默认)
   uri lb://mini-mall-user 这条路由整体限流
   → 所有 /user/** 加起来 QPS=10

② 按 API 分组限流 (CustomApiDefinition)
   自定义 API 组合(例如"敏感接口组" = /user/login + /user/register)
   → 这一组合 QPS=5
```

F2.7 演示 ①（最常用）。

---

### 34.16.4  F2.7 路线（5 步）

| 步 | 内容 |
|---|---|
| F2.7.1 | gateway pom 加 `spring-cloud-alibaba-sentinel-gateway` |
| F2.7.2 | 新建 `SentinelGatewayConfig` 注册自定义降级响应 |
| F2.7.3 | gateway 启动类排除 common-core 的 `GlobalExceptionHandler` **（最大坑）**|
| F2.7.4 | 重启 gateway + 配 GatewayFlowRule (按 user-route，QPS=2) |
| F2.7.5 | 压测验证 |

---

### 34.16.5  F2.7.1：gateway pom 加 sentinel-gateway-adapter

📁 `mini-mall-gateway/pom.xml`

```xml
<dependencies>
    <!-- 原有的 spring-cloud-starter-gateway + common-core + jjwt 不动 -->

    <!-- ⭐ F2.7 新增：Sentinel Gateway 适配器
         - 普通 sentinel 是 MVC + Servlet 风格 (user/product 用的)
         - Gateway 是 WebFlux + Netty, 需要专门的 adapter
         - 它会自动把每条路由识别为 Sentinel 资源, 资源名 = 路由 ID -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-alibaba-sentinel-gateway</artifactId>
    </dependency>
</dependencies>
```

| 改动 | 解释 |
|---|---|
| 不放父 pom 只在 gateway 加 | **依赖隔离** — 普通 sentinel 是 MVC，gateway 用 WebFlux，jar 不一样 |
| 引这个 jar 后 SCG 自动装配 | **SentinelGatewayFilter** + **SentinelGatewayBlockExceptionHandler** 自动生效 |
| **不要再手动 @Bean 它们** | **重复 Bean 坑** — `SentinelSCGAutoConfiguration` 已注册，再写会报 "bean has already been defined" |

---

### 34.16.6  F2.7.2：新建 SentinelGatewayConfig 自定义降级

📁 `mini-mall-gateway/src/main/java/com/minimall/gateway/config/SentinelGatewayConfig.java`（新建）

```java
package com.minimall.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.domain.Result;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import jakarta.annotation.PostConstruct;

/**
 * Sentinel Gateway 自定义降级响应 (F2.7)
 *
 * 重要认知:
 *   SCA 2023.0.1.2 的 SentinelSCGAutoConfiguration 已自动注册了:
 *     - SentinelGatewayFilter           (核心拦截器)
 *     - SentinelGatewayBlockExceptionHandler  (默认异常处理)
 *   所以这里【不再手动 @Bean】, 避免 Bean 名冲突
 *
 * 本类只干一件事:
 *   通过 @PostConstruct 注册【自定义 BlockRequestHandler】
 *   替换默认的"Blocked by Sentinel: FlowException" 字符串输出, 改成 JSON
 */
@Configuration
public class SentinelGatewayConfig {

    @PostConstruct
    public void initBlockHandlers() {
        ObjectMapper mapper = new ObjectMapper();

        BlockRequestHandler blockRequestHandler = (exchange, throwable) -> {
            Result<Void> result = Result.error(429,
                    "网关限流: 请求太频繁, 请稍后再试 ("
                            + throwable.getClass().getSimpleName() + ")");
            String body;
            try {
                body = mapper.writeValueAsString(result);
            } catch (JsonProcessingException e) {
                body = "{\"code\":429,\"message\":\"too many requests\"}";
            }
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body);
        };

        // ⭐ 必须用 GatewayCallbackManager 不是 WebFluxCallbackManager
        GatewayCallbackManager.setBlockHandler(blockRequestHandler);
    }
}
```

#### 代码逐块解释

| 部分 | 解释 |
|---|---|
| `@PostConstruct` | Spring 把 Bean 创建好后自动调，**只调用一次** |
| `BlockRequestHandler` | 函数式接口，签名 `(exchange, throwable) -> ServerResponse` |
| `ObjectMapper.writeValueAsString(...)` | Jackson 把 Result 对象序列化成 JSON 字符串 |
| `ServerResponse.status(...).bodyValue(body)` | **WebFlux 风格**响应（不是 MVC 的 ResponseEntity）|
| `GatewayCallbackManager.setBlockHandler(...)` | **全局静态注册**（不通过 Spring Bean 机制）|

#### ⭐ 大坑：GatewayCallbackManager vs WebFluxCallbackManager

```
错误（user/product 用的, 跟 @SentinelResource 配套）:
  com.alibaba.csp.sentinel.adapter.spring.webflux.callback.WebFluxCallbackManager

正确（SCG 网关专用）:
  com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager

弄错了限流也"看起来生效"(请求被拦), 但用的是默认输出而不是自定义响应
```

---

### 34.16.7  F2.7.3：排除 common-core 的 GlobalExceptionHandler（最大坑）

#### 现象（不修复会发生）

```
被限流的请求 → 不是返 429 + 友好提示
              → 返 500 "系统繁忙，请稍后再试"
```

#### 根因链

```
Sentinel 抛 BlockException (ParamFlowException / FlowException)
  ↓
common-core 的 GlobalExceptionHandler 也被装配到 gateway
  (因为 @ComponentScan("com.minimall") 扫到 com.minimall.common.core)
  ↓
@ExceptionHandler(Exception.class) 优先级太高
  把 BlockException 当作普通 Exception 吞掉
  ↓
返 500 "系统繁忙" 而不是 SentinelGatewayBlockExceptionHandler 的 429
```

⚠️ **Spring 5 之后 `@RestControllerAdvice` 在 WebFlux 也生效**，不是只对 MVC，所以 common-core 这个全局异常会污染 gateway。

#### 修复

📁 `mini-mall-gateway/src/main/java/com/minimall/gateway/MiniMallGatewayApplication.java`

```java
package com.minimall.gateway;

import com.minimall.common.core.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * F2.7 修订: 排除 common-core 的 GlobalExceptionHandler
 *   原因: 它的 @ExceptionHandler(Exception.class) 兜底会吞掉 Sentinel 的 BlockException,
 *         导致网关限流返 500 而不是 429
 */
@SpringBootApplication
@ComponentScan(
        value = "com.minimall",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = GlobalExceptionHandler.class    // ← 精确排除一个类
        )
)
public class MiniMallGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniMallGatewayApplication.class, args);
    }
}
```

#### 代码解释

| 部分 | 解释 |
|---|---|
| `excludeFilters` | `@ComponentScan` 的过滤器，定义"哪些类不要扫" |
| `FilterType.ASSIGNABLE_TYPE` | 按类匹配（其他选项：注解 / 正则 / 自定义）|
| `classes = GlobalExceptionHandler.class` | 精确指定一个类 |

⭐ **这一条改动如果不做**，前面所有 F2.7 努力白费。

---

### 34.16.8  F2.7.4：重启 + 触发资源 + 配 GatewayFlowRule

#### 重启 gateway

```powershell
$pid9080 = (Get-NetTCPConnection -LocalPort 9080 -State Listen).OwningProcess
Stop-Process -Id $pid9080 -Force
mvn -pl mini-mall-gateway clean package -DskipTests
& "D:\jdk-21.0.11\bin\java.exe" -jar mini-mall-gateway-0.0.1-SNAPSHOT.jar
```

#### 触发资源注册（先发几个请求让 Sentinel 看见路由）

```powershell
$token = (curl.exe -s -X POST ... http://localhost:9080/user/login | ConvertFrom-Json).data
1..3 | ForEach-Object {
    curl.exe -s -o NUL -H "Authorization: Bearer $token" http://localhost:9080/product/1
}
```

#### 配 GatewayFlowRule（QPS=2）

```powershell
# 登录 Dashboard 拿 cookie
curl.exe -s -X POST -c "$env:TEMP\sen.cookie" `
  -d "username=sentinel&password=sentinel" `
  "http://localhost:8858/auth/login"

# 配规则
$ruleJson = '{"resource":"product-route","resourceMode":0,"grade":1,"count":2,"interval":1,"intervalUnit":0,"controlBehavior":0,"burst":0,"maxQueueingTimeoutMs":500,"app":"mini-mall-gateway","ip":"192.168.101.49","port":8721}'
Set-Content "$env:TEMP\gwrule.json" $ruleJson -Encoding ascii -NoNewline
curl.exe -s -b "$env:TEMP\sen.cookie" -X POST `
  -H "Content-Type: application/json" `
  --data-binary "@$env:TEMP\gwrule.json" `
  "http://localhost:8858/gateway/flow/new.json"
```

---

### 34.16.9  GatewayFlowRule 字段详解

| 字段 | 值 | 含义 |
|---|---|---|
| `resource` | `product-route` | 路由 ID（在 gateway yml routes 里定义的 `id`）|
| `resourceMode` | `0` | **0=路由 ID / 1=自定义 API 分组** |
| `grade` | `1` | 限流模式：0=并发线程数 / 1=QPS |
| `count` | `2` | 阈值（每秒 2 次）|
| `interval` | `1` | 统计窗口大小（数值）|
| `intervalUnit` | `0` | 窗口单位：0=秒 / 1=分钟 / 2=小时 / 3=天 |
| `controlBehavior` | `0` | 控制策略：0=快速失败 / 1=匀速排队 |
| `burst` | `0` | 突发请求容量（瞬间允许的额外请求数）|
| `ip` | `192.168.101.49` | **必须跟 Dashboard 上 gateway 的实际 IP 一致** |

⚠️ **不要踩坑**：API 是 `/gateway/flow/new.json`（不是普通 `/flow/rule`），字段名是 `interval`（不是 `intervalSec`）。

---

### 34.16.10  F2.7.5：压测验证

```powershell
1..8 | ForEach-Object {
    $body = curl.exe -s -H "Authorization: Bearer $token" http://localhost:9080/product/1
    $tag = if ($body -match '"code":429') { "🚫 LIMITED (429)" }
           elseif ($body -match '"id":1')  { "✓ ok            " }
           else { "?               " }
    "[$_]  $tag  $($body.Substring(0, [Math]::Min(110, $body.Length)))"
}
```

#### 实测结果

```
[1]  ✓ ok                {"code":200,"data":{"id":1,"name":"小米 14 Pro",...}}
[2]  ✓ ok                {"code":200,"data":{"id":1,"name":"小米 14 Pro",...}}
[3]  🚫 LIMITED (429)    {"code":429,"message":"网关限流: 请求太频繁 (ParamFlowException)"}
[4]  🚫 LIMITED (429)    ...
[5]  🚫 LIMITED (429)    ...
[6]  🚫 LIMITED (429)    ...
[7]  🚫 LIMITED (429)    ...
[8]  🚫 LIMITED (429)    ...
```

✅ **完美**：QPS=2 内通过，超出全部返 429 + 自定义 JSON 响应。

---

### 34.16.11  F2.7 三大踩坑（真实）

#### 坑 ①：手动 @Bean SentinelGatewayFilter → 启动报 "bean has already been defined"

**原因**：`SentinelSCGAutoConfiguration` 已自动注册 → 重复定义

**修复**：删掉 `@Bean GlobalFilter sentinelGatewayFilter()` 方法

---

#### 坑 ②：用了 WebFluxCallbackManager 不是 GatewayCallbackManager → 限流仍是默认输出

**现象**：请求被拦，但返回的是 `Blocked by Sentinel...` 字符串而不是我们的自定义 JSON

**修复**：

```java
// 错
WebFluxCallbackManager.setBlockHandler(...)

// 对
GatewayCallbackManager.setBlockHandler(...)
```

import 也要换成 `com.alibaba.csp.sentinel.adapter.gateway.sc.callback.*`

---

#### 坑 ③：GlobalExceptionHandler 吞掉 BlockException → 限流返 500 而不是 429

**现象**：限流的请求返回 `{"code":500, "message":"系统繁忙"}`

**根因**：`@RestControllerAdvice` 在 WebFlux 也生效，把 BlockException 当 Exception 兜底了

**修复**：gateway 启动类 `@ComponentScan` 排除 GlobalExceptionHandler

---

### 34.16.12  F2.7 目录变化

```
mini-mall-gateway/
├── pom.xml                                       ← ✏️ 加 sentinel-gateway
└── src/main/
    ├── java/com/minimall/gateway/
    │   ├── MiniMallGatewayApplication.java       ← ✏️ @ComponentScan 排除 GlobalExceptionHandler
    │   └── config/
    │       └── SentinelGatewayConfig.java        ← ➕ 新建, 自定义 BlockRequestHandler
    └── resources/
        └── application.yml                       ← 无改动 (F2.2 已配 sentinel)
```

---

### 34.16.13  网关层 vs 接口层（你纠结过的）

**两层都要，互补**：

| 场景 | 谁挡 |
|---|---|
| 外部 10w 个请求打 /user/login | 网关先挡（量级问题）|
| 用户输错密码 5 次想锁号 | 接口层挡（业务规则）|
| user-service 内部 Feign 调 product-service | 接口层挡（内部不经过网关）|

```
快递公司类比:
  ① 总仓库门口的保安 (网关层):  挡量级问题(整体限流)
  ② 分拣员           (接口层):  挡业务问题(单接口限流)
  ③ 内部转运         (Feign):   完全绕过门口 → 必须靠分拣员
```

⭐ **网关像门卫，接口像保险柜**。

---

## 34.17  深入：Sentinel 规则到底是什么

> 用户卡点：规则配在 Dashboard 上眼花，代码里看不到，到底"规则"是什么？

---

### 34.17.1  规则的本质 = Java 对象（POJO）

```
你以为的规则:
  Dashboard 上配的"那个东西"

实际上的规则:
  是一个 Java 对象, 存在 user-service 的内存里
```

📁 来自 sentinel-core jar：

```java
public class FlowRule extends AbstractRule {
    private String resource;        // 管哪个资源
    private int grade;              // 限流类型
    private double count;           // 阈值
    private String limitApp;        // 调用方
    // ... 就是个普通 POJO, 一堆 getter/setter
}
```

**规则 = 一个对象 + 几个字段**。

---

### 34.17.2  5 种规则 = 5 个 Java 类

| 类 | 干啥 | 你用过 |
|---|---|---|
| `FlowRule` | 限流 | ✅ F2.5 |
| `DegradeRule` | 熔断 | ✅ F2.6 |
| `SystemRule` | 系统保护 | F2.8 |
| `AuthorityRule` | 黑白名单 | - |
| `ParamFlowRule` | 热点参数限流 | - |
| `GatewayFlowRule` | 网关限流 | ✅ F2.7 |

**每种规则对应一个 Manager**：

```java
FlowRule         → FlowRuleManager.loadRules(...)
DegradeRule      → DegradeRuleManager.loadRules(...)
SystemRule       → SystemRuleManager.loadRules(...)
GatewayFlowRule  → GatewayRuleManager.loadRules(...)
```

---

### 34.17.3  3 种配规则方式

| | Dashboard UI | 代码硬编码 | Nacos 持久化 |
|---|---|---|---|
| 学习 / 演示 | ✅ 最简单 | ⚠️ 重启麻烦 | ❌ 配置麻烦 |
| 开发期 | ✅ 灵活 | ✅ 可控 | ⚠️ overkill |
| **生产期** | ❌ **一重启就丢** | ⚠️ 改规则要重启 | ✅ **唯一答案** |

---

### 34.17.4  方式 ①：Dashboard UI 配（你之前用的）

```
浏览器: http://localhost:8858
  → 簇点链路 → 找 loginResource → 点 "+流控"
  → 填 QPS=2 → 新增
       ↓
Dashboard 通过 HTTP 推规则到 user-service:8719
       ↓
user-service 收到 → FlowRuleManager.loadRules(rules)
       ↓
规则在内存里
```

**优点**：可视化、不用改代码
**缺点**：⚠️ **Dashboard 重启 → 规则全丢**

---

### 34.17.5  方式 ②：代码硬编码（启动时加载）

📁 完整可运行示例：

```java
package com.minimall.demo;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import java.util.Collections;

@Configuration
public class DemoSentinel {

    /**
     * 启动时把规则塞进内存
     * 启动后 Dashboard 上也能看到这条规则(实时推回去)
     */
    @PostConstruct
    public void init() {
        FlowRule rule = new FlowRule();
        rule.setResource("demoResource");   // 资源名 (跟下面注解一致)
        rule.setGrade(1);                    // 1 = QPS 模式
        rule.setCount(5);                    // 每秒最多 5 次

        FlowRuleManager.loadRules(Collections.singletonList(rule));
    }

    @SentinelResource(value = "demoResource", blockHandler = "demoBlock")
    public String doSomething() {
        return "ok";
    }

    public String demoBlock(BlockException ex) {
        return "限流了";
    }
}
```

#### 代码解释

| 行 | 解释 |
|---|---|
| `new FlowRule()` | 创建规则对象（就是 new 一个 POJO）|
| `setResource("demoResource")` | 告诉规则"我管谁"（必须跟 `@SentinelResource("...")` 字符串一致）|
| `setGrade(1)` | 1 = QPS 模式（0 = 并发线程数）|
| `setCount(5)` | 每秒 5 次 |
| `FlowRuleManager.loadRules(...)` | **把规则装载到内存** |
| `@SentinelResource("demoResource")` | 标这个方法 = 资源名 demoResource |
| **没有任何主动检查代码** | Sentinel SlotChain 自动检查 |

**优点**：服务启动就有规则、跟代码一起 git 管理
**缺点**：改规则要重启

---

### 34.17.6  方式 ③：Nacos 持久化（F2.9 预告 / 生产标配）

```
Nacos 上存规则 (JSON 配置):
  Data ID: mini-mall-user-flow-rules.json
  内容: [{"resource":"loginResource","grade":1,"count":2}, ...]
       ↓
user-service 启动时:
  ReadableDataSource ds = new NacosDataSource(...)
  FlowRuleManager.register2Property(ds.getProperty())
       ↓
Nacos 改了 → 实时推 → 内存自动更新
       ↓
✓ Dashboard 重启也不丢
✓ 改规则不重启
```

---

### 34.17.7  规则在代码里"怎么用"——答案：你不用管

**这是最反直觉的事**：

```
你只写了:
  @SentinelResource("loginResource")
  public Result login(...) { ... }

你没写:
  if (规则违反) ...
  FlowRuleManager.check(...)

那规则在哪生效?
```

#### Sentinel 在背后做的事

```
请求进来
  ↓
@SentinelResource AOP 切入 (Sentinel 自动装配的 SentinelResourceAspect)
  ↓
SphU.entry("loginResource")  ← Sentinel 内部调
  ↓
SlotChain 8 个 slot 依次过
  ↓
其中 FlowSlot.entry() 干这事:
  List<FlowRule> rules = FlowRuleManager.getRules("loginResource");
  for (rule : rules) {
      if (当前 QPS > rule.getCount()) {
          throw new FlowException(...);
      }
  }
  ↓
通过 → 执行 login()
失败 → 抛 FlowException → 调 loginBlock
```

⭐ **你只管标注解 + 配规则，Sentinel 自动用**。

---

### 34.17.8  规则 / 资源 / Sentinel 三角关系

```
┌──────────────────────────┐
│ 你的代码 (Resource 端)    │
│   @SentinelResource(      │
│     value = "loginRes")   │←─┐
└──────────────────────────┘  │
                              │ 资源名(字符串)
                              │ 必须一致
                              ↓
┌──────────────────────────┐  │
│ 规则 (Rule)               │  │
│   FlowRule rule = new...  │──┘
│   rule.setResource(       │
│     "loginRes")           │
│   rule.setCount(2)        │
└──────────────────────────┘
            ↓
   FlowRuleManager.loadRules
            ↓
┌──────────────────────────┐
│ 内存                       │
│ Map<resource, List<Rule>> │
│ "loginRes" → [rule]       │
└──────────────────────────┘
            ↓
请求来时, SlotChain 查 → 检查 → 通过 / 拒
```

---

### 34.17.9  项目里你已经会的 5 件事

| 你做过的 | 知识点对应 |
|---|---|
| `@SentinelResource("loginResource")` | 资源声明 |
| Dashboard 配 QPS=2 | 规则配置（方式 ①）|
| 拦下来调 `loginBlock` | 规则生效（SlotChain 自动检查）|
| 后续 Dashboard 重启规则丢 | 方式 ① 的缺点 |
| F2.9 待做：持久化 Nacos | 方式 ③，生产标配 |

---

### 34.17.10  核心总结（5 句话）

```
1. 规则就是 Java 对象 (POJO)
2. 5 种规则 = 5 个 Java 类
3. 配规则 3 种方式: UI / 代码 / Nacos
4. 用规则: 你不用管, Sentinel SlotChain 自动检查
5. 资源跟规则靠"名字字符串"绑在一起
```

---

## 34.18  F2.8 实战：系统保护（整机自动限流）

> 跟前面所有规则都不同：F2.8 不绑定任何 resource，看的是【整台机器累不累】。

---

### 34.18.1  跟前面限流/熔断的本质区别

```
F2.4 接口限流: 给【单个方法】限, 阈值固定
   loginResource QPS=2

F2.6 熔断:     看【单个方法】异常率, 触发就关闭
   flakyResource 1秒3异常 → 关 10 秒

F2.7 网关限流: 给【整条路由】限
   product-route QPS=2

F2.8 系统保护: 看【整台机器】负载, 触发就全局限流
   CPU > 80% → 这台机器拒绝所有新请求, 给已有请求喘息空间
                ↑
              跟"哪个方法"无关, 跟"机器累不累"有关
```

⭐ **系统保护 = 应用级整体限流，不绑定任何 resource。**

---

### 34.18.2  5 个保护指标

| 指标 | 触发条件 | 容易演示？ |
|---|---|---|
| `qps` | **全部入口 QPS** > 阈值 | ✅ 最容易，连发就行 |
| `avgRt` | 平均响应时间 > 阈值（毫秒）| ⚠️ 需要慢接口 |
| `maxThread` | 并发线程数 > 阈值 | ⚠️ 需要并发压测工具 |
| `highestSystemLoad` | 系统 load1 > 阈值 | ❌ 笔记本难复现 |
| `highestCpuUsage` | CPU 使用率 > 阈值（0.0~1.0）| ❌ 同上 |

⭐ **设 -1 = 不启用这个指标。**

---

### 34.18.3  SystemRule 不绑定 resource（关键差异）

```java
// 前面所有规则都要 setResource("xxx")
FlowRule flow = new FlowRule();
flow.setResource("loginResource");   // ← 必填

// SystemRule 不需要!
SystemRule sys = new SystemRule();
sys.setQps(10);                       // 全局 QPS 10
                                       // ↑ 不绑定任何 resource, 对整台机器生效
```

📁 来自 sentinel-core jar `com.alibaba.csp.sentinel.slots.system.SystemRule`：

```java
// Sentinel 源码（简化）
public class SystemRule extends AbstractRule {
    private double highestSystemLoad = -1;   // load 阈值, -1=禁用
    private double highestCpuUsage   = -1;   // CPU 阈值
    private double qps               = -1;   // 全局 QPS
    private long   avgRt             = -1;   // 平均 RT
    private long   maxThread         = -1;   // 最大线程数

    // ⚠️ 注意继承的 resource 字段【不用】, SystemRule 是 app 级
}
```

---

### 34.18.4  F2.8 实施：配 SystemRule (qps=3)

#### Dashboard API 调用

```powershell
# 登录拿 cookie (略)
# 注意 API 路径是 /system/new.json 不是 /system/rule
$sysRule = 'app=mini-mall-user&ip=192.168.101.49&port=8719' +
           '&highestSystemLoad=-1' +
           '&avgRt=-1' +
           '&maxThread=-1' +
           '&qps=3' +
           '&highestCpuUsage=-1'
curl.exe -s -b "$env:TEMP\sen.cookie" -X POST -d $sysRule `
  "http://localhost:8858/system/new.json"
```

期望响应：
```json
{"success":true,"data":{"id":1,"app":"mini-mall-user","qps":3.0,...}}
```

---

### 34.18.5  压测验证（混合多接口）

**重点**：因为是整机保护，要用**多种接口**混合压测才能体现。

```powershell
1..3 | ForEach-Object { curl.exe -s http://localhost:9001/user/1 }
1..3 | ForEach-Object {
    curl.exe -s -X POST -H "Content-Type: application/json" `
      --data-binary "@$env:TEMP\login.json" `
      http://localhost:9001/user/login
}
1..2 | ForEach-Object { curl.exe -s http://localhost:9001/hello }
```

#### 实测结果

```
GET /user/1     ✓ ok            ← 第 1 个, 占 1/3 配额
GET /user/1     ✓ ok            ← 第 2 个, 占 2/3
GET /user/1     ✓ ok            ← 第 3 个, 占满 3/3
POST /login     ?  Blocked by Sentinel (flow limiting)   ← 后续全限
POST /login     ?  Blocked by Sentinel (flow limiting)
POST /login     ?  Blocked by Sentinel (flow limiting)
GET /hello      ?  Blocked by Sentinel (flow limiting)
GET /hello      ?  Blocked by Sentinel (flow limiting)
```

⭐ **3 种不同接口都被限**，证明系统保护是【应用级整体配额】。

---

### 34.18.6  关键现象：blockHandler 不生效（必懂）

#### 现象

被限的请求返回 `Blocked by Sentinel (flow limiting)` 字符串，**不是** `loginBlock` 返回的友好 JSON。

#### 根因

```
@SentinelResource 走【方法层】
  ↓ 抛 BlockException 在方法切入点
  ↓ SentinelResourceAspect 反射调 blockHandler
  ↓ 返友好 JSON

SystemRule 走【Web 层 Adapter】
  ↓ 在 Controller 之前拦
  ↓ @SentinelResource AOP 都没机会进
  ↓ 直接默认输出 "Blocked by Sentinel..."
```

#### 生产解决（不做演示）

引入 `sentinel-spring-webmvc-adapter`，注册全局 `BlockExceptionHandler`：

```java
@Configuration
public class SentinelWebMvcConfig {
    @Bean
    public SentinelWebMvcConfig webMvcConfig() {
        return new SentinelWebMvcConfig().setBlockExceptionHandler((req, resp, ex) -> {
            // 写自定义响应
        });
    }
}
```

⭐ **学习版本省略**，记住"系统保护跟接口 blockHandler 在两层"就够了。

---

### 34.18.7  5 指标实际使用建议

| 指标 | 推荐阈值 | 适用 |
|---|---|---|
| `qps` | 单机承载能力的 80% | 防爆量（最常用）|
| `avgRt` | 200ms | 防"慢"传染 |
| `maxThread` | Tomcat 最大线程 * 80% | 防线程池打满 |
| `highestSystemLoad` | CPU 核数 * 2.5 | Linux 服务器适用 |
| `highestCpuUsage` | 0.7（70%）| 笔记本 / 虚拟机不准 |

⭐ **3 条经验**：
1. `qps` 必配（最有效）
2. `avgRt` 配上有助于发现下游慢调用
3. `highestSystemLoad` / `highestCpuUsage` 在容器化环境（K8s）下不可靠，慎用

---

### 34.18.8  F2.8 速查总结

```
✅ SystemRule 不绑 resource → 整机配额
✅ 5 个指标(qps / avgRt / maxThread / load1 / cpuUsage), 设 -1 = 禁用
✅ Web 层拦截, 比 @SentinelResource AOP 更早
✅ 默认输出 "Blocked by Sentinel" 字符串, 生产要配 webmvc-adapter
✅ API: POST /system/new.json
✅ 配多个指标只要任一触发就限流
```

---

## 34.19  F2.9 实战：规则持久化到 Nacos

> F 阶段终极一步。完成这步，你的 Sentinel 才离生产可用。

---

### 34.19.1  F2.9 解决的真痛

```
F2.0~F2.8 你配的所有规则:
  - loginResource QPS=2
  - flakyResource 熔断
  - product-route QPS=2
  - SystemRule QPS=3

存在 Sentinel Dashboard 内存里
       ↓
Dashboard 一重启 (容器 OOM / 升级 / 部署) → 全 ✗ 消失
       ↓
所有保护瞬间失效, 服务"裸奔"
       ↓
💥 生产事故
```

**F2.9 解决**：规则**持久化到 Nacos**（同一个 Nacos Server 既管配置又管 Sentinel 规则）。

---

### 34.19.2  推模式 vs 拉模式（必须懂）

```
拉模式 (默认, 学习用):
  Dashboard ──HTTP push──→ user-service
                              ↑ 规则在 user-service 内存
  Dashboard 自己也存(但仅内存) → 重启就丢

推模式 (生产用, F2.9 要做):
  Nacos ─【长连接 push】→ user-service
                            ↑ 启动时拉一次 + 订阅变化
  Dashboard 只读 Nacos, 不再是规则真源
  Dashboard 重启 → 规则在 Nacos 里, 不丢 ✓
```

---

### 34.19.3  F2.9 路线（4 步）

| 步 | 内容 |
|---|---|
| F2.9.1 | 加 `sentinel-datasource-nacos` 依赖 |
| F2.9.2 | 写 `SentinelNacosConfig` 注册数据源 |
| F2.9.3 | 在 Nacos 上建 2 个规则配置项（限流 + 熔断）|
| F2.9.4 | 重启 user-service + 验证启动加载 + 动态推送 |

---

### 34.19.4  F2.9.1：加 sentinel-datasource-nacos 依赖

📁 `mini-mall-user/pom.xml`

```xml
<!-- ⭐ F2.9 新增：Sentinel 规则持久化到 Nacos
     SCA BOM 自动锁版本(跟 sentinel-core 同步)
     启动时 user-service 会去 Nacos 拉规则 + 订阅推送
     解决"Dashboard 重启规则丢"的问题 -->
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-datasource-nacos</artifactId>
</dependency>
```

⭐ **不放父 pom**：F2.9 只在 user-service 演示，product/gateway 暂不演示。

---

### 34.19.5  F2.9.2：写 SentinelNacosConfig（核心代码）

📁 `mini-mall-user/src/main/java/com/minimall/user/config/SentinelNacosConfig.java`（新建）

```java
package com.minimall.user.config;

import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Sentinel 规则持久化到 Nacos (F2.9 核心)
 *
 * 干两件事:
 *   ① 启动时去 Nacos 拉规则配置, 加载到 Sentinel 内存
 *   ② 订阅 Nacos 配置变化, 改了规则实时推过来, 不用重启
 */
@Configuration
public class SentinelNacosConfig {

    // ─── Nacos 配置(跟 F1 用同一个 Nacos Server) ───────────
    private static final String NACOS_SERVER  = "127.0.0.1:8848";
    private static final String GROUP         = "SENTINEL_GROUP";

    // ─── 配置项 dataId(每种规则一个文件) ──────────────────
    private static final String FLOW_DATA_ID    = "mini-mall-user-flow-rules.json";
    private static final String DEGRADE_DATA_ID = "mini-mall-user-degrade-rules.json";

    @PostConstruct
    public void init() {
        loadFlowRules();
        loadDegradeRules();
        System.out.println("=========== Sentinel 规则已从 Nacos 加载 ===========");
    }

    /**
     * 加载限流规则
     *
     * 步骤:
     *   1) 创建 NacosDataSource (内部维护跟 Nacos 的长连接)
     *      构造参数: nacos地址 + group + dataId + 反序列化函数
     *
     *   2) 用 FlowRuleManager.register2Property() 把数据源挂到 Sentinel 规则中心
     *      之后:
     *        - 启动时立即拉一次, 内容加载到 FlowRuleManager 内存
     *        - Nacos 配置变化时 → NacosDataSource 收到 push → 通知 FlowRuleManager 重新加载
     */
    private void loadFlowRules() {
        ReadableDataSource<String, List<FlowRule>> flowDataSource = new NacosDataSource<>(
                NACOS_SERVER,
                GROUP,
                FLOW_DATA_ID,
                source -> JSON.parseObject(source, new TypeReference<List<FlowRule>>() {})
        );
        FlowRuleManager.register2Property(flowDataSource.getProperty());
    }

    private void loadDegradeRules() {
        ReadableDataSource<String, List<DegradeRule>> degradeDataSource = new NacosDataSource<>(
                NACOS_SERVER,
                GROUP,
                DEGRADE_DATA_ID,
                source -> JSON.parseObject(source, new TypeReference<List<DegradeRule>>() {})
        );
        DegradeRuleManager.register2Property(degradeDataSource.getProperty());
    }
}
```

#### 代码逐块解释

| 部分 | 解释 |
|---|---|
| `ReadableDataSource<String, List<FlowRule>>` | 只读数据源（Sentinel 只读 Nacos，写要回 Nacos）|
| `String` 泛型 | 原始数据类型（Nacos 配置就是字符串）|
| `List<FlowRule>` 泛型 | 反序列化后的目标类型 |
| `NacosDataSource<>(server, group, dataId, parser)` | 构造一个连 Nacos 的数据源 |
| `JSON.parseObject + TypeReference` | fastjson 解析泛型集合（Sentinel 内置 fastjson 依赖）|
| `register2Property(...)` | **关键**：把数据源的 Property 注册给 RuleManager，挂上后自动联动 |
| `@PostConstruct` | Bean 创建后自动调一次，启动时加载 |

#### NacosDataSource 内部原理

```
new NacosDataSource(...)
   ↓
内部启动一个 ConfigService(Nacos SDK)
   ↓
ConfigService.getConfig(dataId, group) ← 立即拉一次
   ↓
解析后 setValue() 通知所有监听者
   ↓
同时 addListener() 订阅后续变化
   ↓
Nacos 配置改了 → push → onChange 回调 → 重新解析 → setValue → 通知监听者
```

⭐ `register2Property(property)` 就是把 `FlowRuleManager` 注册成监听者。

---

### 34.19.6  F2.9.3：Nacos 上建规则配置项

#### 建限流规则（QPS=2）

```powershell
$flowJson = '[{"resource":"loginResource","grade":1,"count":2,"limitApp":"default","strategy":0,"controlBehavior":0,"clusterMode":false}]'
Add-Type -AssemblyName System.Web
$encoded = [System.Web.HttpUtility]::UrlEncode($flowJson)
$body = "dataId=mini-mall-user-flow-rules.json&group=SENTINEL_GROUP&content=$encoded&type=json"
curl.exe -s -X POST -H "Content-Type: application/x-www-form-urlencoded" -d $body `
  "http://localhost:8848/nacos/v1/cs/configs"
```

#### 建熔断规则

```powershell
$degradeJson = '[{"resource":"flakyResource","grade":2,"count":3,"timeWindow":10,"minRequestAmount":3,"statIntervalMs":1000,"slowRatioThreshold":1.0,"limitApp":"default"}]'
$encoded = [System.Web.HttpUtility]::UrlEncode($degradeJson)
$body = "dataId=mini-mall-user-degrade-rules.json&group=SENTINEL_GROUP&content=$encoded&type=json"
curl.exe -s -X POST -H "Content-Type: application/x-www-form-urlencoded" -d $body `
  "http://localhost:8848/nacos/v1/cs/configs"
```

#### 配置项命名约定

```
mini-mall-user-flow-rules.json     ← 限流规则
mini-mall-user-degrade-rules.json  ← 熔断规则
mini-mall-user-system-rules.json   ← 系统保护规则(可加)
mini-mall-user-authority-rules.json ← 黑白名单(可加)
mini-mall-user-param-flow-rules.json ← 热点参数(可加)
```

每种规则 1 个 dataId，分文件管理。

#### Group 命名

```
DEFAULT_GROUP  ← F1 业务配置用 (mini-mall-user.yaml 那个)
SENTINEL_GROUP ← F2.9 Sentinel 规则用 (本节)
```

⭐ **分开 Group 避免业务和规则混在一起**。

---

### 34.19.7  F2.9.4：重启验证 + 启动加载

#### 重打 + 重启 user-service

```powershell
$pid9001 = (Get-NetTCPConnection -LocalPort 9001 -State Listen).OwningProcess
Stop-Process -Id $pid9001 -Force
mvn -pl mini-mall-user clean package -DskipTests
& "D:\jdk-21.0.11\bin\java.exe" -jar `
  "mini-mall-user-0.0.1-SNAPSHOT.jar"
```

#### 关键日志（证明规则从 Nacos 加载）

启动日志里会有：
```
=========== Sentinel 规则已从 Nacos 加载 ===========
```

（来自我们 `@PostConstruct` 里的 println）

#### 压测验证（启动后立即生效）

```powershell
1..6 | ForEach-Object {
    $body = curl.exe -s -X POST -H "Content-Type: application/json" `
      --data-binary "@$env:TEMP\login.json" `
      http://localhost:9001/user/login
    $tag = if ($body -match '"code":429') { "🚫 LIMITED" }
           elseif ($body -match '"code":200') { "✓ ok      " }
           else { "?         " }
    "[$_]  $tag  $($body.Substring(0, [Math]::Min(110, $body.Length)))"
}
```

**实测结果**：
```
[1]  ✓ ok        {"code":200,"data":"eyJhbGc..."}
[2]  ✓ ok        {"code":200,"data":"eyJhbGc..."}
[3]  🚫 LIMITED  {"code":429,"message":"...FlowException..."}
[4]  🚫 LIMITED  ...
[5]  ✓ (新一秒)
[6]  🚫
```

✅ **未通过 Dashboard 配规则，规则直接来自 Nacos**。

---

### 34.19.8  F2.9 终极测试：改 Nacos 看动态推送

#### 改 Nacos 规则（QPS 2 → 5），不重启服务

```powershell
$flowJson = '[{"resource":"loginResource","grade":1,"count":5,"limitApp":"default","strategy":0,"controlBehavior":0,"clusterMode":false}]'
$encoded = [System.Web.HttpUtility]::UrlEncode($flowJson)
$body = "dataId=mini-mall-user-flow-rules.json&group=SENTINEL_GROUP&content=$encoded&type=json"
curl.exe -s -X POST -H "Content-Type: application/x-www-form-urlencoded" -d $body `
  "http://localhost:8848/nacos/v1/cs/configs"

# 等 2 秒, 然后压测 7 次
Start-Sleep -Seconds 2
1..7 | ForEach-Object {
    # 同上压测脚本
}
```

#### 实测结果

```
[1-5]  ✓ ok        ← 5 个全过! 不重启服务规则就更新了
[6-7]  🚫 LIMITED  ← 超出 QPS=5 被限
```

✅ **动态推送验证成功** — Nacos 改一行配置，服务实时拿到新规则。

---

### 34.19.9  NacosDataSource + register2Property 工作原理

```
应用启动
  ↓
@PostConstruct 调 init()
  ↓
new NacosDataSource(server, group, dataId, parser)
   ① 内部启动 NacosConfigService (Nacos SDK)
   ② NacosConfigService 跟 Nacos Server 建 gRPC 长连接
   ③ 立即 getConfig(dataId, group) 拉一次
   ④ 内部 setValue("...json...")
   ⑤ 注册 listener: Nacos 推变化 → onChange → 重新 setValue
  ↓
FlowRuleManager.register2Property(ds.getProperty())
   ① getProperty() 返回数据源的 Property 对象
   ② FlowRuleManager 注册成 Property 的监听者
   ③ 数据源 setValue 时 → 通知 FlowRuleManager → loadRules
  ↓
启动完成, 规则已在内存

Nacos 改配置
  ↓
长连接 push 到 user-service
  ↓
NacosDataSource 收到 → 解析 JSON → setValue
  ↓
FlowRuleManager 监听到 → loadRules
  ↓
规则更新, 业务下一个请求开始按新规则限流
```

---

### 34.19.10  Dashboard 双向同步问题（生产坑）

⚠️ **F2.9 配完后存在的局限**：

```
1. 在 Dashboard 上改规则:
   - 改了【内存】生效, 但【不会同步回 Nacos】
   - user-service 重启 → 拉 Nacos 的旧值 → 你的 Dashboard 改动丢

2. 直接改 Nacos:
   - 推到 user-service 内存 ✓
   - Dashboard 看不到这个改动(Dashboard 不订阅 Nacos)
   - 体验割裂
```

**生产解决**（不做演示）：
1. 自己改 Sentinel Dashboard 源码（让 Dashboard 写 Nacos）
2. 用 SCA 团队的 `sentinel-dashboard-nacos` fork
3. 完全弃用 Dashboard，只用 Nacos 控制台改

⭐ **学习项目走方案 3 最简单**。

---

### 34.19.11  F2.9 目录变化

```
mini-mall-cloud/mini-mall-user/
├── pom.xml                                       ← ✏️ 加 sentinel-datasource-nacos
└── src/main/java/com/minimall/user/
    └── config/
        └── SentinelNacosConfig.java              ← ➕ 新建 NacosDataSource 配置
```

只改一个文件、新增一个文件。**真正的"几行代码上生产"**。

---

### 34.19.12  F2.9 核心总结

```
✅ NacosDataSource = Nacos 配置 → Sentinel 规则的【桥梁】

✅ register2Property = 把数据源挂到规则中心
   挂上后:
     - 启动时自动拉一次
     - 配置变化时自动推过来
     - 业务代码零感知

✅ Dashboard 配规则【临时】, Nacos 配规则【永久】
   生产环境只改 Nacos

✅ Group=SENTINEL_GROUP 跟 F1 的 DEFAULT_GROUP 隔开
   避免混淆"业务配置"和"sentinel 规则"

✅ 每种规则一个 dataId, 分文件管理
   mini-mall-user-flow-rules.json
   mini-mall-user-degrade-rules.json
   ...

✅ 已知局限: Dashboard 跟 Nacos 不双向同步, 生产需改造
```

---

## 34.20  F 阶段总结 + 后续方向

### 34.20.1  F 阶段全景成果

| 步骤 | 解决的事 | 状态 |
|---|---|---|
| F1 | 配置中心 + @RefreshScope 动态刷新 | ✅ |
| F2.0~F2.3 | Sentinel 接入 | ✅ |
| F2.4~F2.5 | 接口限流 + 自定义降级 | ✅ |
| F2.6 | 异常熔断 + 状态机 | ✅ |
| F2.7 | 网关全局限流 | ✅ |
| F2.8 | 系统保护（整机）| ✅ |
| F2.9 | 规则持久化 Nacos（生产关键）| ✅ |

---

### 34.20.2  现在的 mini-mall-cloud 状态

```
┌─────────────────────────────────────────────────────┐
│ 服务发现:    Nacos              ✓ E 阶段           │
│ 配置中心:    Nacos              ✓ F1                │
│ 网关:        Spring Cloud Gateway ✓ D 阶段          │
│ JWT 鉴权:    AuthGlobalFilter   ✓ D3                │
│ 链路追踪:    traceId (RequestLogFilter) ✓ D5        │
│ 接口限流:    Sentinel @SentinelResource ✓ F2.4      │
│ 异常熔断:    Sentinel DegradeRule ✓ F2.6            │
│ 网关限流:    Sentinel Gateway   ✓ F2.7              │
│ 系统保护:    Sentinel SystemRule ✓ F2.8             │
│ 规则持久化:  Nacos Push 模式    ✓ F2.9              │
│                                                     │
│ → 已具备生产级微服务治理能力!                       │
└─────────────────────────────────────────────────────┘
```

---

### 34.20.3  G 阶段候选方向

| 方向 | 内容 | 优先级 |
|---|---|---|
| **G1 Redis 缓存** | RedisTemplate + 缓存注解 + 缓存穿透/雪崩 | ★★★ |
| **G2 Seata 分布式事务** | 跨服务 ACID（订单创建 + 扣库存）| ★★ |
| **G3 SkyWalking 链路追踪** | 全链路可视化 + 性能瓶颈定位 | ★★ |
| **G4 监控告警** | Prometheus + Grafana + 钉钉告警 | ★ |

**推荐顺序**：G1 → G3 → G4 → G2（Seata 最复杂留最后）。

---

## 34.21  v8 → v9 完整章节地图

| 章 | 内容 | 来源 |
|---|---|---|
| 1~20 | 工程基础 + A/B/C 阶段 | v7 |
| 21.0~21.11 | D 阶段全景 + D1/D2 | D 笔记 |
| 21.12, 21.13 | D3, D4 完整实录 | v8 新增 |
| 22~31 | 底层补习 10 章 | v8 新增 |
| 32.0~32.9 | E 阶段 Nacos 注册中心 | E 笔记 |
| 33.0~33.17 | F1 Nacos 配置中心 + @RefreshScope | F1 笔记 |
| **34.0~34.21** | **F2 Sentinel 全套（F2.0~F2.9 全部完成）** | **F2 笔记，本节** |

---

**F 阶段归档完毕。** 下面接 G 阶段第一波（业务模块搬迁）。

---

# 第 35 章  G 阶段开篇：从"治理就绪"到"业务搬迁"

## 35.0  我们处在哪里？

E + F 阶段做完后, 微服务的【治理能力】100% 拉满了:

| 能力 | 状态 | 来源 |
|---|---|---|
| 服务注册发现 | ✅ | Nacos discovery (E) |
| 配置中心 + 动态刷新 | ✅ | Nacos config + @RefreshScope (F1) |
| 限流 / 熔断 / 系统保护 | ✅ | Sentinel + GatewayCallbackManager (F2) |
| 规则持久化 | ✅ | NacosDataSource (F2.9) |
| 网关鉴权透传 userId | ✅ | AuthGlobalFilter (D3) |
| 全链路日志 traceId | ✅ | RequestLogFilter (D5) |

但【业务覆盖率】很低: 单体 mini-mall 有 9 个业务模块, 微服务里只搬了 User + Product 两个。

→ G 阶段的任务: **把单体里剩下的业务模块按 DDD 边界搬到对应的微服务里**。

## 35.1  G 阶段路线图

```
G3  ─ 业务模块搬迁
       ├─ G3.0  建 mini-mall-order 空骨架   (本章 36)
       ├─ G3.1  Category   → product        (本章 37)
       ├─ G3.2  Address    → user           (本章 38)
       ├─ G3.3  联调验证 12 步              (本章 39)
       ├─ G3.4  CartItem   → order   (依赖 G1 Redis, 待办)
       ├─ G3.5  Orders     → order   (依赖 G1 + G2, 待办)
       └─ G3.6  Seckill    → order   (依赖 G1 + G2 + Lua, 待办)
G1  ─ Redis 接入                            (待办, 解锁 Favorite/Cart)
G2  ─ RabbitMQ 接入                         (待办, 解锁 Orders/Seckill)
```

## 35.2  DDD 拆分提案 (我们目前在用的边界)

```
                       ┌──────────────────────┐
                       │   mini-mall-gateway  │ 9080 ✅
                       └──────────┬───────────┘
                                  │
        ┌─────────────────────────┼─────────────────────────┐
        ▼                         ▼                         ▼
  ┌──────────┐             ┌──────────────┐          ┌──────────────┐
  │ user 9001│ ✅           │ product 9002 │ ✅       │ order   9003 │ ⭐G3.0新建
  │ - 登录/注册│            │ - 商品/分类  │          │ - 购物车     │
  │ - 地址    │ ⭐G3.2      │ - 收藏      │ 待办     │ - 订单       │
  └──────────┘             └──────────────┘          │ - 秒杀       │
                                                     └──────────────┘
                                                     依赖: Redis + MQ
```

**为什么这样拆？**
- **用户域** (user): 管"人" → 账号 + 地址跟着用户
- **商品域** (product): 管"东西" → 商品 + 分类 + 收藏
- **订单域** (order): 管"交易" → 购物车 + 订单 + 秒杀, 全是写流程, 吃 Redis + MQ


---


# 第 36 章  G3.0  建 mini-mall-order 空骨架

## 36.1  目标

跑通一个【最简】可启动 + 可注册 Nacos 的微服务壳子, 后续 CartItem / Orders / Seckill 都往这里塞。

## 36.2  文件结构图

```
mini-mall-cloud/
├── pom.xml                           ← 改 1 处 (打开 mini-mall-order 注释)
└── mini-mall-order/                  ← 全新
    ├── pom.xml                       ← 新建 (抄 product, 最简)
    └── src/main/
        ├── java/com/minimall/order/
        │   ├── MiniMallOrderApplication.java   ← 启动类
        │   └── controller/
        │       └── HelloController.java        ← 验证骨架可用
        └── resources/
            └── application.yml                 ← 9003 + Nacos + Sentinel
```

## 36.3  Step 1 — 父 pom 解开 module 注释

修改文件: `mini-mall-cloud/pom.xml`

```xml
<modules>
    <module>mini-mall-common</module>
    <module>mini-mall-user</module>
    <module>mini-mall-product</module>
    <module>mini-mall-gateway</module>
    <!-- ⭐ G3.0 解开: 迁购物车/订单/秒杀的载体 -->
    <module>mini-mall-order</module>
</modules>
```

### 知识点 ① Maven `<module>` 的作用

| 行 | 含义 |
|---|---|
| `<module>X</module>` | Maven 把 X 当成子模块, 跑 `mvn install` 时按顺序参与编译 |
| 注释掉 `<module>` | 子目录里 pom 写得再漂亮也没用, Maven **当它不存在** |

→ 这就是为什么不打开这个注释, 后续创建的 order 模块根本编不到。

### 知识点 ② 父子 pom 的"聚合"和"继承"是两件事

```
父 pom 的两个角色:
┌─────────────────────────────────────────────────┐
│ 1. 聚合 (Aggregate)                            │
│    通过 <modules> 列子模块                     │
│    一次 mvn install 把所有子模块编一遍         │
├─────────────────────────────────────────────────┤
│ 2. 继承 (Inherit)                              │
│    通过 <parent> 关系给子模块"传家底"          │
│    传的东西: properties / dependencyMgmt /     │
│              dependencies / pluginMgmt / repos │
└─────────────────────────────────────────────────┘
```

→ 子模块的 pom 必须写 `<parent>` 才算继承, 写 `<module>` 跟父在父 pom 才算聚合, **两者要同时写**才完整工作。

## 36.4  Step 2 — `mini-mall-order/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <modelVersion>4.0.0</modelVersion>

    <!-- 继承根 pom 自动拿到:
         ① Spring Boot 3.3.5 BOM
         ② SC / SCA / 业务依赖的 dependencyManagement (锁版本)
         ③ spring-cloud-starter-bootstrap (父 dependencies 强制塞)
         ④ Nacos discovery / config / Sentinel (父 dependencies 自动给)
         ⑤ 阿里云 Maven 镜像
    -->
    <parent>
        <groupId>com.minimall</groupId>
        <artifactId>mini-mall-cloud</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>mini-mall-order</artifactId>
    <name>mini-mall-order</name>
    <description>订单服务: 购物车 / 订单 / 秒杀</description>

    <dependencies>
        <!-- ① 自家 common-core (Result/异常/SecurityContextHolder) -->
        <dependency>
            <groupId>com.minimall</groupId>
            <artifactId>mini-mall-common-core</artifactId>
        </dependency>

        <!-- ② Spring Web (写 @RestController 必备) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- ③ Lombok (common-core 里加了 optional=true 不传递, 各模块自己引) -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- ─── 暂时不引 ─────────────────────────────────────────
             MyBatis-Plus / MySQL  → 等下一步搬 CartItem 再加
             spring-boot-starter-data-redis → G1 接 Redis 时加
             spring-boot-starter-amqp        → G2 接 RabbitMQ 时加
             这就是 feedback_concrete_first: 用到再加, 不预先准备
             ───────────────────────────────────────────────────── -->
    </dependencies>

    <!-- 显式声明 spring-boot-maven-plugin 才能打 fat jar
         (父 pluginManagement 只锁版本, 这里必须再写一次才生效) -->
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 知识点 ③ 为什么这个 pom 这么薄？

父 pom 的 `<dependencies>` 段已经给所有子模块强制塞了:

```xml
<!-- 父 pom 的强制依赖 (子模块自动继承) -->
<dependencies>
    <dependency>spring-cloud-starter-bootstrap</dependency>         <!-- 读 bootstrap.yml -->
    <dependency>spring-cloud-starter-alibaba-nacos-discovery</dependency>  <!-- 注册 Nacos -->
    <dependency>spring-cloud-starter-alibaba-nacos-config</dependency>      <!-- 拉配置 -->
    <dependency>spring-cloud-starter-alibaba-sentinel</dependency>          <!-- 限流熔断 -->
</dependencies>
```

→ 所以 order 子 pom 只写了【业务必须】的 3 个: common-core / web / lombok。

### 知识点 ④ `pluginManagement` vs `plugins` 区别

| 段 | 作用 | 子模块行为 |
|---|---|---|
| `<pluginManagement><plugins>` | **只锁版本, 不引入** | 子模块要再写 `<plugin>` 才生效 |
| `<plugins>` | **真引入** | 子模块自动继承, 自动生效 |

→ Boot 的 `spring-boot-maven-plugin` 放在父 `pluginManagement` 是为了让 common 模块**不打 fat jar** (因为 common 不是可运行 Spring Boot 应用), 业务模块要 fat jar **就必须自己再写一遍**。

## 36.5  Step 3 — `application.yml`

```yaml
# ════════════════════════════════════════════════════════════════
# mini-mall-order 订单服务配置
# 端口分配 (避免冲突):
#   9001 = user      9002 = product     9003 = order     9080 = gateway
# Sentinel 客户端口 (拿来跟 Dashboard 互发心跳):
#   8719 = user      8720 = product     8721 = gateway   8722 = order
# ════════════════════════════════════════════════════════════════

server:
  port: 9003                        # ⭐ order 服务对外端口

spring:
  application:
    name: mini-mall-order           # ⭐ Nacos 上显示的服务名 + Feign 调用别名

  cloud:
    # ─── Nacos 服务发现 (E 阶段) ────────────────────────────────
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848

    # ─── Sentinel 限流熔断 (F2 阶段) ───────────────────────────
    sentinel:
      transport:
        dashboard: 127.0.0.1:8858   # ⭐ Sentinel Dashboard 地址 (上报心跳/拉规则)
        port: 8722                  # ⭐ 跟 user/product/gateway 错开避免冲突
      eager: true                   # 启动立即建连, 否则等第一个请求才建, 不便于调试

logging:
  level:
    com.minimall: debug             # 自家代码 debug, 别人 info
    root: info
```

### 知识点 ⑤ 为什么 `spring.application.name` 这么重要？

```
spring.application.name = "mini-mall-order" 这一个字符串决定了:
  ① Nacos 控制台显示的服务名
  ② Feign 调用别名: @FeignClient(name="mini-mall-order")
  ③ 网关路由: uri: lb://mini-mall-order
  ④ Nacos 配置中心订阅的 dataId: mini-mall-order / mini-mall-order.properties
  ⑤ Sentinel Dashboard 上的应用名
```

→ 这一行改名所有相关都跟着改, 是【全局命名锚点】。

### 知识点 ⑥ Sentinel 客户端 port 为什么各服务要错开？

```
跑在同一台机器上, 三个 Java 进程都想监听:
  user → 8719
  product → 8720
  gateway → 8721
  order → 8722

如果两个进程都用 8719, 第二个启动的会报:
  Address already in use: bind
```

→ 跟 server.port 一样的道理, **本地多进程必须端口隔离**。

## 36.6  Step 4 — 启动类 `MiniMallOrderApplication.java`

```java
package com.minimall.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * mini-mall-order 订单服务启动类
 */
@SpringBootApplication
@ComponentScan("com.minimall")           // ⭐ 扩到 com.minimall 整层
public class MiniMallOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniMallOrderApplication.class, args);
        System.out.println("=========== mini-mall-order 启动成功 ===========");
    }
}
```

### 知识点 ⑦ `@SpringBootApplication` 拆开等于什么？

```
@SpringBootApplication
    ↓ 解开
= @Configuration              ← 这个类本身就是配置类
+ @EnableAutoConfiguration    ← 启动 Spring Boot 自动装配 (核心!)
+ @ComponentScan              ← 默认扫描【本类所在包及子包】
```

### 知识点 ⑧ 为什么要额外写 `@ComponentScan("com.minimall")`？

```
不加, @SpringBootApplication 默认只扫 com.minimall.order.**
加了, 把扫描范围扩到 com.minimall.**

因为 common-core 的类在 com.minimall.common 下:
  com.minimall.common.core.exception.GlobalExceptionHandler  ← 不扫到, 异常没人接, 500
  com.minimall.common.core.domain.Result                       ← 编译能用 (import 拿到), 但 Bean 不注册
```

→ 单体里包名是统一的 com.minimall.minimall, 不用关心; 微服务里跨模块共享代码就必须扩扫描范围。

### 知识点 ⑨ 暂时没加 `@MapperScan` 的原因

order 服务**还没引** MyBatis-Plus, 现阶段:
- 不查库, 不存库
- 没 Mapper 接口
- 加 @MapperScan 反而启动报错 (扫不到指定包)

→ 等下一步搬 CartItem 时再加, 符合 `feedback_concrete_first`。

## 36.7  Step 5 — `HelloController.java`

```java
package com.minimall.order.controller;

// ⭐ Result 是从 common-core 拿的, 不是 order 自己写的
// 这就是 common-core 的价值: 3 个服务返回格式统一, 改 1 处 3 处生效
import com.minimall.common.core.domain.Result;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController                       // ① 告诉 Spring: 这是个 Controller, 方法返的对象自动序列化成 JSON
@RequestMapping("/order")             // ② 类级前缀, 类里所有方法都以 /order 开头
public class HelloController {

    @GetMapping("/hello")             // ③ 方法绑 GET, 最终路径 = 类 /order + 方法 /hello
    public Result<String> hello() {
        return Result.success("hello from order");
    }
}
```

### 知识点 ⑩ `@RestController` 拆开

```
@RestController
    ↓ 解开
= @Controller     ← 注册为 Spring MVC 控制器
+ @ResponseBody   ← 方法返的对象不走视图解析, 直接走 HttpMessageConverter
                    (Spring Boot 默认装 Jackson, 自动转 JSON)
```

→ 如果只写 `@Controller`, 方法返 `Result<String>` 会被当成"视图名", 找模板找不到就 404。

## 36.8  Step 6 — 编译 + 启动 + 验证

```bash
# 编译 order + 所有依赖 (-am = also-make 把上游 common-core 也编)
mvn clean install -DskipTests -pl mini-mall-order -am

# 启动 (Win 上用绝对路径避开 JDK 8 PATH)
'D:\jdk-21.0.11\bin\java.exe' -jar mini-mall-order/target/mini-mall-order-0.0.1-SNAPSHOT.jar

# 验证
curl http://127.0.0.1:9003/order/hello
# → {"code":200,"message":"操作成功","data":"hello from order"}
```

### 知识点 ⑪ `mvn install -pl X -am` 拆开

| 参数 | 含义 |
|---|---|
| `-pl mini-mall-order` | project-list, **只编**指定模块 |
| `-am` | also-make, 自动把它**依赖的**上游模块也编 (common-core) |
| `-DskipTests` | 跳过测试 |

→ 比 `mvn clean install` 整个项目编**快 5~10 倍**, 改一个模块用它最爽。

## 36.9  Step 7 — 启动日志关键 3 行

```
✓ nacos registry, DEFAULT_GROUP mini-mall-order 192.168.32.1:9003 register finished
✓ Started MiniMallOrderApplication in 11.204 seconds
✓ =========== mini-mall-order 启动成功 ===========
```

→ 第一行说明 Nacos 注册成功, Nacos 控制台 (http://127.0.0.1:8848/nacos , 账号 nacos/nacos) 服务列表里能看到 `mini-mall-order`。


---


# 第 37 章  G3.1  Category 搬到 product 服务

## 37.1  搬迁清单

| 文件 | 单体路径 | 微服务路径 |
|---|---|---|
| Entity | `com.minimall.minimall.entity.Category` | `com.minimall.product.entity.Category` |
| Mapper | `com.minimall.minimall.mapper.CategoryMapper` | `com.minimall.product.mapper.CategoryMapper` |
| Service 接口 | `com.minimall.minimall.service.ICategoryService` | `com.minimall.product.service.ICategoryService` |
| Service 实现 | `com.minimall.minimall.service.impl.CategoryServiceImpl` | `com.minimall.product.service.impl.CategoryServiceImpl` |
| Controller | `com.minimall.minimall.controller.CategoryController` | `com.minimall.product.controller.CategoryController` |

## 37.2  Entity 代码 + 解析

```java
package com.minimall.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
public class Category implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;
    private String icon;
    private Integer sort;
    private Byte status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Byte isDeleted;
}
```

### 知识点 ① 三个 MP 注解

| 注解 | 作用 |
|---|---|
| `@TableId(value="id", type=IdType.AUTO)` | 标记主键; AUTO = 用数据库自增, 入库后 MP 自动回填到 entity.id |
| `@TableLogic` | 标记**逻辑删除字段**; MP 自动把 DELETE 改成 UPDATE set is_deleted=1, SELECT 自动加 where is_deleted=0 |
| `@TableField` | 字段映射 (未使用; entity 字段 camelCase, 表字段 snake_case 时, 配合 application.yml 的 `map-underscore-to-camel-case: true` 自动转换, 无需手写 @TableField) |

### 知识点 ② `IdType` 类型选择

```
IdType.AUTO        ← MySQL 自增 (本项目用)
IdType.ASSIGN_ID   ← 雪花算法 (分库分表场景)
IdType.ASSIGN_UUID ← UUID (大字符串, 性能差)
IdType.INPUT       ← 你自己管 (一般不用)
IdType.NONE        ← 完全不管
```

### 知识点 ③ `Serializable` 必要性

```
为什么 entity 一般实现 Serializable?
  ① 防止以后放 Redis (序列化必备)
  ② 防止以后通过 RPC (Dubbo) 跨进程传输
  ③ Session 集群存储
```

→ 不实现也能跑 MP CRUD, 但**留着是好习惯**。

## 37.3  Mapper 代码 + 解析

```java
package com.minimall.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimall.product.entity.Category;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
```

### 知识点 ④ `BaseMapper` 白嫖了什么？

`BaseMapper<T>` 是 MP 给的接口, 你 extends 一下就有 **16 个方法**:

```java
// 单条 CRUD
int insert(T entity)
int deleteById(Serializable id)
int updateById(T entity)
T   selectById(Serializable id)

// 条件 CRUD
int delete(Wrapper<T> wrapper)
int update(T entity, Wrapper<T> wrapper)
List<T> selectList(Wrapper<T> wrapper)
T   selectOne(Wrapper<T> wrapper)
Long selectCount(Wrapper<T> wrapper)

// 批量 CRUD
int deleteBatchIds(Collection<? extends Serializable> ids)
List<T> selectBatchIds(Collection<? extends Serializable> ids)

// 分页
<E extends IPage<T>> E selectPage(E page, Wrapper<T> wrapper)

// 其他
boolean exists(Wrapper<T> wrapper)
List<Map<String,Object>> selectMaps(Wrapper<T> wrapper)
List<Object> selectObjs(Wrapper<T> wrapper)
<E extends IPage<Map<String,Object>>> E selectMapsPage(E page, Wrapper<T> wrapper)
```

→ **0 行实现代码, 16 个方法**。

### 知识点 ⑤ `@Mapper` vs `@MapperScan`

```
@Mapper (类级)
  作用: 单个接口标记为 Mapper
  位置: 在每个 Mapper 接口上写

@MapperScan("com.minimall.product.mapper") (启动类级)
  作用: 批量扫描整个包下所有接口当 Mapper
  位置: 在启动类上写一次
```

→ 两个**择一**即可。我们用 @MapperScan 在启动类批量扫, 也兼容 @Mapper, 双保险。

## 37.4  Service 接口 + 实现 + 解析

```java
// IService 接口
public interface ICategoryService extends IService<Category> {
}

// 实现 (空类!)
@Service
public class CategoryServiceImpl
        extends ServiceImpl<CategoryMapper, Category>
        implements ICategoryService {
    // 类体空白! 业务方法将来在这里加
}
```

### 知识点 ⑥ `IService` vs `BaseMapper` 区别

| 维度 | `BaseMapper` | `IService` |
|---|---|---|
| 层次 | 数据访问层 (DAO) | 业务封装层 (Service) |
| 方法数 | 16 个 | 25 个 (内部调 BaseMapper) |
| 命名 | `selectById` / `insert` | `getById` / `save` |
| 适用 | 单条 SQL | 批量 + 流式 + 事务 |

→ 一般 Controller 调 Service, Service 内部偶尔调 baseMapper (复杂条件)。

### 知识点 ⑦ `ServiceImpl<M, T>` 内部干啥？

```java
// ServiceImpl 源码片段
public class ServiceImpl<M extends BaseMapper<T>, T> implements IService<T> {
    @Autowired
    protected M baseMapper;        // ⭐ 自动注入 CategoryMapper

    public boolean save(T entity) {
        return SqlHelper.retBool(baseMapper.insert(entity));
    }
    // ... 24 个类似实现
}
```

→ ServiceImpl 替你自动注入了 Mapper, 然后用 Mapper 实现 IService 所有方法。**你 0 行代码白嫖 25 个业务方法**。

## 37.5  Controller 代码 + 解析

```java
package com.minimall.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimall.common.core.domain.Result;
import com.minimall.product.entity.Category;
import com.minimall.product.service.ICategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/category")
public class CategoryController {

    @Autowired
    private ICategoryService categoryService;

    /** ① 分类列表 (启用的排前, 再按 sort 升序) */
    @GetMapping("/list")
    public Result<List<Category>> list() {
        QueryWrapper<Category> w = new QueryWrapper<>();
        w.orderByDesc("status")        // 启用的排前面 (1>0)
         .orderByAsc("sort");          // sort 越小越靠前
        return Result.success(categoryService.list(w));
    }

    /** ② 详情 */
    @GetMapping("/{id}")
    public Result<Category> getById(@PathVariable Long id) {
        return Result.success(categoryService.getById(id));
    }

    /** ③ 新增 (单体 bug 修复: 加 @RequestBody, 返刚保存的 category 含自增 id) */
    @PostMapping
    public Result<Category> create(@RequestBody Category category) {
        categoryService.save(category);
        return Result.success(category);
    }

    /** ④ 修改 (路径里的 id 是权威 id, 防前端 body 注入别的 id) */
    @PutMapping("/{id}")
    public Result<Category> update(@PathVariable Long id,
                                   @RequestBody Category category) {
        category.setId(id);
        categoryService.updateById(category);
        return Result.success(category);
    }

    /** ⑤ 删除 (逻辑删除, 实际 UPDATE category SET is_deleted=1) */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.removeById(id);
        return Result.success();
    }
}
```

### 知识点 ⑧ `QueryWrapper` 链式条件构造器

```java
QueryWrapper<Category> w = new QueryWrapper<>();
w.eq("user_id", 1)               // WHERE user_id = 1
 .ne("status", 0)                 // AND status != 0
 .like("name", "手机")            // AND name LIKE '%手机%'
 .gt("sort", 10)                  // AND sort > 10
 .between("price", 100, 200)      // AND price BETWEEN 100 AND 200
 .in("id", 1, 2, 3)               // AND id IN (1, 2, 3)
 .orderByDesc("create_time")      // ORDER BY create_time DESC
 .orderByAsc("sort")              // ORDER BY ..., sort ASC
 .last("LIMIT 10");               // LIMIT 10 (拼到 SQL 最后)
```

→ 用列名字符串拼条件, 类型不安全但简单。MP 还有 `LambdaQueryWrapper` 用 lambda 表达式拼, 类型安全但啰嗦。

### 知识点 ⑨ 单体 Controller 里的 3 个 bug 修复

| # | 单体写法 | 问题 | 微服务里改成 |
|---|---|---|---|
| 1 | `public <category>Result<Category> addCategory(Category c)` | `<category>` 被当成泛型类型变量 | 去掉, 改成正常签名 |
| 2 | `return Result.success()` 没传 data | 返回签名是 `Result<Category>`, 返了 `Result<Void>` 不匹配 | 改成 `Result.success(category)` |
| 3 | `category` 参数无 `@RequestBody` | 前端发 JSON, Spring MVC 走 form 绑定收不到 | 加 `@RequestBody` |

### 知识点 ⑩ 路径风格统一

```
单体:  /api/categories (复数 + /api 前缀)
微服务: /category      (单数, 跟其他模块 /user /product /order 风格一致)
```

→ 微服务里统一**单数 + 无前缀** (前缀让网关路由处理)。


---


# 第 38 章  G3.2  Address 搬到 user 服务

## 38.1  为什么这章是【整轮搬迁的重头戏】？

Category 是【无用户态】的全局数据 (所有人共享), 搬迁等于纯复制粘贴。

Address 是【与用户绑定】的数据, 涉及单体 → 微服务的【**上下文传递机制转换**】, 这是微服务架构的核心知识点。

## 38.2  ⭐ 核心对比：单体 vs 微服务的上下文传递

```
┌─── 单体 (一个 JVM 进程) ──────────────────────────────────┐
│                                                          │
│  前端 ─[Bearer token]─→ JwtInterceptor                  │
│                            │                             │
│                            ▼ 解 token                    │
│                       ThreadLocal (UserContext) ◄────┐  │
│                            │                          │  │
│                            ▼                          │  │
│                        Controller                     │  │
│                            │                          │  │
│                            ▼                          │  │
│                   UserContext.getUserId() ────────────┘  │
│                                                          │
│  ⭐ ThreadLocal 起作用是因为【同一个线程从头到尾】       │
└──────────────────────────────────────────────────────────┘

┌─── 微服务 (两个 JVM 进程, 跨进程通信) ────────────────────┐
│                                                          │
│  前端 ─[Bearer token]─→ 网关:9080                       │
│                          │                               │
│                          ▼ AuthGlobalFilter 解 token     │
│                     mutate request, 加 X-User-Id header  │
│                          │                               │
│                          ▼ HTTP 转发 (新的进程!)         │
│                     user-service:9001                    │
│                          │                               │
│                          ▼                               │
│                     Controller                           │
│                          │                               │
│                          ▼                               │
│         @RequestHeader("X-User-Id") Long userId          │
│                                                          │
│  ⭐ ThreadLocal 这里【没用】, 跨进程内存不共享           │
└──────────────────────────────────────────────────────────┘
```

**一句话记忆：**
> 单体的 ThreadLocal、微服务的 Header, **本质都是"上下文传递"**, 只是介质不同。

## 38.3  Entity / Mapper / Service / 实现 (4 个文件) — 与 Category 一样的套路

代码 99% 同 Category, 只列差异:

```java
// Address.java (差异: 多一个 userId 字段)
@TableId(value = "id", type = IdType.AUTO)
private Long id;
private Long userId;   // ⭐ 关键: 这个值在微服务里从网关 X-User-Id header 来
private String receiver;
// ... receiver/phone/province/city/district/detail/isDefault/createTime/updateTime
@TableLogic
private Byte isDeleted;

// AddressMapper.java
@Mapper
public interface AddressMapper extends BaseMapper<Address> {}

// IAddressService.java
public interface IAddressService extends IService<Address> {}

// AddressServiceImpl.java
@Service
public class AddressServiceImpl
        extends ServiceImpl<AddressMapper, Address>
        implements IAddressService {
}
```

## 38.4  Controller 完整代码 + 逐段解析

```java
package com.minimall.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimall.common.core.domain.Result;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.user.entity.Address;
import com.minimall.user.service.IAddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 收货地址 Controller (从单体搬, 核心改动: UserContext → X-User-Id header)
 *
 * 端点 (网关代理):
 *   GET    /user/address           → 我的地址列表
 *   GET    /user/address/{id}      → 地址详情 (含越权校验)
 *   POST   /user/address           → 新增 (强制写 userId, 不信前端)
 *   PUT    /user/address/{id}      → 修改 (含越权校验)
 *   DELETE /user/address/{id}      → 删除 (含越权校验)
 */
@RestController
@RequestMapping("/user/address")        // ⭐ 跟 user-service 其他接口共用 /user 前缀
public class AddressController {

    @Autowired
    private IAddressService addressService;

    /** ① 我的地址列表 */
    @GetMapping
    public Result<List<Address>> list(
            // ⭐ 不用 UserContext 了, 直接从 header 拿
            // 网关 AuthGlobalFilter 已经把它塞进来
            @RequestHeader("X-User-Id") Long userId
    ) {
        QueryWrapper<Address> w = new QueryWrapper<>();
        w.eq("user_id", userId)                    // ⭐ 强制只查自己的
         .orderByDesc("is_default")                // 默认置顶
         .orderByDesc("create_time");

        return Result.success(addressService.list(w));
    }

    /** ② 详情 (含越权校验) */
    @GetMapping("/{id}")
    public Result<Address> detail(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return Result.success(getAndCheckOwn(id, userId));
    }

    /** ③ 新增 (强制盖 userId, 防伪造) */
    @PostMapping
    public Result<Address> create(
            @RequestBody Address address,
            @RequestHeader("X-User-Id") Long userId
    ) {
        address.setUserId(userId);     // ⭐ 强制盖, 防伪造
        addressService.save(address);
        return Result.success(address);
    }

    /** ④ 修改 (先越权校验, 再强制盖 id+userId) */
    @PutMapping("/{id}")
    public Result<Address> update(
            @PathVariable Long id,
            @RequestBody Address address,
            @RequestHeader("X-User-Id") Long userId
    ) {
        getAndCheckOwn(id, userId);   // 先校验"这条 id 真是你的"
        address.setId(id);
        address.setUserId(userId);
        addressService.updateById(address);
        return Result.success(address);
    }

    /** ⑤ 删除 (逻辑删除) */
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId
    ) {
        getAndCheckOwn(id, userId);
        addressService.removeById(id);
        return Result.success();
    }

    /**
     * 私有工具: 查 + 越权校验
     *
     * 三种结果:
     *   - 不存在        → 404 BusinessException
     *   - 不是当前用户  → 403 BusinessException (越权)
     *   - 校验通过      → 返回 Address 实体
     */
    private Address getAndCheckOwn(Long id, Long currentUserId) {
        Address addr = addressService.getById(id);
        if (addr == null) {
            throw new BusinessException(404, "地址不存在");
        }
        if (!addr.getUserId().equals(currentUserId)) {
            throw new BusinessException(403, "无权访问该地址");
        }
        return addr;
    }
}
```

### 知识点 ① `@RequestHeader` 注解的"魔法"

```java
public Result<List<Address>> list(
        @RequestHeader("X-User-Id") Long userId  // Spring 自动:
                                                  // ① 从 HTTP header 取 X-User-Id 值
                                                  // ② 用 ConversionService 把 String 转 Long
                                                  // ③ 注入到方法参数 userId
) {}
```

| 注解可选属性 | 含义 |
|---|---|
| `name = "..."` (或不写名) | header 名称 |
| `required = false` | header 缺失时不报错 (默认 true, 缺失返 400) |
| `defaultValue = "0"` | header 缺失或为空时的默认值 |

### 知识点 ② 为什么要"强制盖 userId, 不信前端"？

```
不防御场景:
  前端伪造请求 → POST /user/address {"receiver":"我","userId":99}
  网关解了 alice 的 token → X-User-Id: 1
  但 Controller 直接 save(address) → address.userId=99
  → 等于以 alice 身份给【userId=99 的用户】塞了一条地址

防御:
  address.setUserId(userId);   // 强制覆盖前端传的, 用 header 里的
  save(address)                // 一定写到 alice 自己头上
```

→ 这种"以网关身份为唯一权威"是微服务安全的【铁律】。

### 知识点 ③ 越权校验的"三态"

```
GET /user/address/{id}

入参: id=999, currentUserId=1
                ↓
        addressService.getById(999)
                ↓
        ┌───────┴───────┐
        ↓               ↓
      null         非 null
        ↓               ↓
   404"不存在"      检查 userId
                        ↓
                ┌───────┴───────┐
                ↓               ↓
            != 1            == 1
                ↓               ↓
          403"无权"       返回 addr
```

→ 把"不存在"和"越权"区分开返不同状态码, 前端能给出友好提示。

### 知识点 ④ 把 currentUserId **显式传参**的好处

```java
// 写法 A: 隐式拿 (单体常用)
private Address getAndCheckOwn(Long id) {
    Long userId = UserContext.getUserId();  // 内部隐式调
    Address addr = ...;
    if (!addr.getUserId().equals(userId)) throw new BusinessException(403, ...);
    return addr;
}

// 写法 B: 显式传 (本项目用)
private Address getAndCheckOwn(Long id, Long currentUserId) {
    // 所有依赖都在参数里, 一眼能看出方法依赖什么
}
```

→ 写法 B 的好处:
- ✅ 方法【对外部状态零依赖】
- ✅ 测试时容易构造 (不用 mock ThreadLocal)
- ✅ 接口语义清晰, 阅读者不用追到方法内部才知道还依赖了 UserContext


---


# 第 39 章  G3.3  联调验证 12 步

## 39.1  网关路由配置补充

新增路由 + 白名单 (`mini-mall-gateway/src/main/resources/application.yml`):

```yaml
spring:
  cloud:
    gateway:
      routes:
        # user-route + product-route 已存在 (E 阶段)
        - id: user-route
          uri: lb://mini-mall-user
          predicates:
            - Path=/user/**

        - id: product-route
          uri: lb://mini-mall-product
          predicates:
            - Path=/product/**

        # ⭐ G3.1 新增: 商品分类走商品服务
        # 注: Address 不用单起路由, 因为它在 /user/address/** 下,
        #     被 user-route 的 /user/** 自动覆盖
        - id: category-route
          uri: lb://mini-mall-product
          predicates:
            - Path=/category/**
```

白名单 (`AuthGlobalFilter.WHITE_LIST`):

```java
private static final List<String> WHITE_LIST = List.of(
        "/user/login", "/user/register",
        // ⭐ G3.1: 商品分类对游客也可见 (列表/详情)
        // 注: startsWith 会把 POST/PUT/DELETE 也放过, 教学项目暂不区分
        //     生产环境应该用 method + path 双维度白名单
        "/category"
);
```

### 知识点 ① 白名单匹配方式有 3 种

```java
// 方式 1: startsWith (本项目用)
WHITE_LIST.stream().anyMatch(path::startsWith)
// 优点: 简单
// 缺点: /category 会放过 GET/POST/PUT/DELETE 所有方法

// 方式 2: equals
WHITE_LIST.stream().anyMatch(path::equals)
// 优点: 精确
// 缺点: 写 100 条 path 才能匹配 RESTful 风格

// 方式 3: AntPathMatcher (Spring 提供)
new AntPathMatcher().match("/category/**", path)
// 优点: 支持 *, **, ?
// 缺点: 反复创建对象会慢, 要做单例
```

### 知识点 ② Path 匹配优先级

```
Gateway 路由按【写入顺序】依次匹配, 第一条匹配成功就转发。
所以:
  /user/address/1 先匹配到 /user/** → mini-mall-user (对)
  /category/list 跳过 /user/**, 跳过 /product/**, 匹配 /category/** → mini-mall-product (对)
```

→ 写路由时, 把【最具体】的放前面。

## 39.2  12 步 curl 验证清单

```
═══════════ Category 5 接口 (无需 token) ═══════════
① GET    /category/list        → 200, 列表
② POST   /category             → 200, 返新 id
③ GET    /category/{id}        → 200, 详情
④ PUT    /category/{id}        → 200, 修改
⑤ DELETE /category/{id}        → 200, 逻辑删除

═══════════ Address 5 接口 (需 token) ═══════════
⑥ POST   /user/login           → 拿 alice token
⑦ POST   /user/address         → 200, 新增
⑧ GET    /user/address         → 200, 列表
⑨ PUT    /user/address/{id}    → 200, 修改
⑩ DELETE /user/address/{id}    → 200, 删除

═══════════ 边界测试 ═══════════
⑪ GET    /user/address/999     → 404, "地址不存在"
⑫ 无 token 访问 /user/address  → 401, 网关拦截
```

## 39.3  关键命令解析

### ⚠️ Win + PowerShell + curl + 中文 = 编码地狱

```bash
# ❌ 这样写, 中文字符会被 PowerShell 编为 GBK
curl -X POST http://... -d '{"name":"测试分类G3"}'
# → 后端 Jackson 报: Invalid UTF-8 start byte 0xb2

# ✅ 解决方案: JSON 写到文件, 用 --data-binary 读
echo '{"name":"TestCatG3","sort":99}' > /tmp/cat.json
curl -X POST http://... --data-binary @/tmp/cat.json
```

### 知识点 ③ `-d` vs `--data-binary` 差异

| 参数 | 处理 |
|---|---|
| `-d` (= `--data`) | 会**移除换行符**, 把数据当 form 处理 |
| `--data-binary` | **原样**发送, 一字节不改, 推荐用于 JSON |
| `--data-raw` | 原样发送但不解析 `@file` 前缀 |

### 知识点 ④ 从 JSON 响应里提取 token (没有 jq 的方案)

```bash
# 后端返: {"code":200,"message":"操作成功","data":"eyJhbGc..."}

TOKEN=$(curl -s ... | sed -E 's/.*"data":"([^"]+)".*/\1/')
#                          ↑ 正则: 找 "data":" 开头, 直到下一个 "
echo "$TOKEN" > /tmp/token.txt
```

### 知识点 ⑤ Bearer Token 标准格式

```
HTTP header:
  Authorization: Bearer eyJhbGc...
  ↑              ↑     ↑
  header 名      方案   JWT 内容
                 (固定 7 字符: "Bearer ")
```

→ 后端去 Bearer 前缀: `token.substring(7)`。

## 39.4  跑通后的验证结果

| # | curl | 结果 |
|---|---|---|
| ① | `GET /category/list` | ✅ 2 条数据 (数码 + fjb) |
| ② | `POST /category` | ✅ id=4 回填 |
| ③ | `GET /category/4` | ✅ 自动 createTime |
| ④ | `PUT /category/4` | ✅ 更新 |
| ⑤ | `DELETE /category/4` | ✅ 逻辑删除 (列表里 id=4 消失) |
| ⑥ | `POST /user/login` | ✅ alice token 拿到 |
| ⑦ | `POST /user/address` | ✅ userId=1 自动注入 |
| ⑧ | `GET /user/address` | ✅ 3 条地址 |
| ⑨ | `PUT /user/address/3` | ✅ |
| ⑩ | `DELETE /user/address/3` | ✅ |
| ⑪ | `GET /user/address/999` | ✅ 404 "地址不存在" |
| ⑫ | 无 token 访问 | ✅ 401 网关拦下 |


---


# 第 40 章  MyBatis-Plus 在微服务里的角色解析

## 40.1  常见误解 vs 实际事实

| 误解 | 实际事实 |
|---|---|
| "用 MP CRUD 需要建 common-mybatis 公共模块" | ❌ 不需要。CRUD 是 `mybatis-plus-spring-boot3-starter` 给的, **starter 就是能力本身** |
| "公共模块 = 用得了 MP" | ❌ 反了。**有 starter 就能用 MP**, 公共模块只是"再封一层" |
| "建公共模块才能集中分页插件配置" | ✅ 这点对。但 3 行 Bean 散在 2 个服务也不痛 |

## 40.2  MP 在项目里的"分工图"

```
mini-mall-cloud/pom.xml (父)
  └─ <dependencyManagement>
        └─ mybatis-plus-spring-boot3-starter (3.5.9)  ← 版本管理 (只锁不引)

mini-mall-user/pom.xml + mini-mall-product/pom.xml (子)
  └─ <dependency>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>  ← 真引依赖
                              ↓
                MP 启动时自动:
                  ① 注入 SqlSessionFactory
                  ② 注入 SqlSessionTemplate
                  ③ 扫描 @Mapper / @MapperScan 接口
                  ④ 给每个 Mapper 接口生成代理 Bean
                  ⑤ 处理 @TableId / @TableLogic / @TableField 注解
                              ↓
            Service / Controller 里直接用:
              extends BaseMapper<T>     白嫖 16 个 CRUD
              extends ServiceImpl<M,T>   白嫖 25 个业务方法
```

→ **所以 user/product 服务的 CRUD 一直在工作**, 不依赖任何"公共模块"。

## 40.3  Category/Address 5+5 接口 ↔ MP 方法 全映射

| curl 路径 | Controller 方法 | 调的 MP 方法 | MP 内部 SQL |
|---|---|---|---|
| `GET /category/list` | `list()` | `IService.list(w)` | `SELECT * FROM category WHERE is_deleted=0` (因 @TableLogic) |
| `POST /category` | `create()` | `IService.save(c)` | `INSERT INTO category(...) VALUES(...)` |
| `GET /category/4` | `getById()` | `IService.getById(4)` | `SELECT * FROM category WHERE id=4 AND is_deleted=0` |
| `PUT /category/4` | `update()` | `IService.updateById(c)` | `UPDATE category SET ... WHERE id=4` |
| `DELETE /category/4` | `delete()` | `IService.removeById(4)` | `UPDATE category SET is_deleted=1 WHERE id=4` ⭐ 因 @TableLogic |
| `POST /user/address` | `create()` | `IService.save(a)` | `INSERT INTO address(...) VALUES(...)` |
| ... (同上) | ... | ... | ... |

→ **每一行 curl 都是 MP 在背后跑 SQL**。`CategoryServiceImpl` 类体空白, 业务**0 行代码**, 全靠 MP 白嫖。

## 40.4  关于"分页插件"——什么时候才需要补？

```java
// 现在 Category 列表: 没翻页
QueryWrapper<Category> w = ...;
List<Category> list = categoryService.list(w);
// 即使表里 100 万行也是 SELECT * 全查, 性能崩

// 加分页:
Page<Category> p = new Page<>(1, 10);              // 第 1 页, 每页 10 条
IPage<Category> result = categoryService.page(p, w);
// 没装拦截器: SELECT * (全查, 性能崩)
// 装了拦截器: SELECT * LIMIT 0, 10 (正常)
```

**插件 Bean 长这样**, 单体里就有:

```java
@Configuration
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

### 微服务里要不要补？

**目前选择: 暂不补** (符合 `feedback_concrete_first`)

理由:
- 现在 Category/Address 都没翻页需求, 加了用不上
- 等下次做"商品列表分页"那一刻再补, 痛感真实, 学得透
- 即使要补, 也是各服务各放一份 (3 行 Bean × 2 = 6 行重复, 还可接受)
- 等 3 个以上服务都要分页, 再抽 `mini-mall-common-mybatis` 模块

## 40.5  关于"自动填充 createTime/updateTime"

测试 ② 返回 `createTime: null` 的原因:

```
代码层: entity.createTime / updateTime 没填值
入库时: MP insert(entity) → INSERT (..., create_time, update_time) VALUES (..., NULL, NULL)
数据库: 字段定义有 DEFAULT CURRENT_TIMESTAMP → MySQL 自己塞了当前时间
再查询: createTime/updateTime 有值了 (测试 ③ 详情就能看到)
```

→ 这不是 bug。**单体跟微服务都一样**, 都靠 DB default 兜底。

**真要代码层自动填**, 加 `MetaObjectHandler`:

```java
@Component
public class MetaHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
```

然后 entity 字段加注解:

```java
@TableField(fill = FieldFill.INSERT)
private LocalDateTime createTime;
@TableField(fill = FieldFill.INSERT_UPDATE)
private LocalDateTime updateTime;
```

→ 同样**暂不补**, 等真需要"跨数据库兼容" (PostgreSQL 没 ON UPDATE CURRENT_TIMESTAMP) 再补。


---


# 第 41 章  本轮章节地图 + 待办清单

## 41.1  v9 → v10 (本轮新增) 章节地图

| 章 | 内容 | 来源 |
|---|---|---|
| 1~20 | 工程基础 + A/B/C 阶段 | v7 |
| 21~31 | D 阶段全景 + 底层补习 | v8 |
| 32 | E 阶段 Nacos 注册中心 | v8 |
| 33 | F1 Nacos 配置中心 | v9 |
| 34 | F2 Sentinel (F2.0~F2.9 全套) | v9 |
| **35** | **G 阶段开篇 + DDD 拆分** | **v10 新增** |
| **36** | **G3.0 建 mini-mall-order 空骨架** | **v10 新增** |
| **37** | **G3.1 Category 搬到 product** | **v10 新增** |
| **38** | **G3.2 Address 搬到 user (上下文转换核心)** | **v10 新增** |
| **39** | **G3.3 联调验证 12 步** | **v10 新增** |
| **40** | **MyBatis-Plus 在微服务里的角色解析** | **v10 新增** |
| 41 | 本章 (索引) | v10 新增 |

## 41.2  待办清单 (按优先级)

| 优先 | 任务 | 阻塞依赖 |
|---|---|---|
| ★★★ | **G1 接 Redis** | 阻塞 Favorite / CartItem / Orders / Seckill |
| ★★★ | **G3.4 CartItem → order** | 等 G1 |
| ★★ | G2 接 RabbitMQ | 阻塞 Orders 超时关闭 + Seckill 异步落单 |
| ★★ | G3.5 Orders → order | 等 G1 + G2 |
| ★ | G3.6 Seckill → order (最难) | 等 G1 + G2 + Lua |
| ★ | 顺手补 MybatisPlusConfig (分页插件) | 等真要做"列表分页"那一刻 |
| ★ | 顺手补 MetaObjectHandler (自动填时间) | 等跨数据库或讨厌 DB default 那一刻 |

## 41.3  能力清单 (到本轮结束)

```
✅ 服务发现/注册      Nacos
✅ 配置中心 + 动态刷新  Nacos + @RefreshScope
✅ 限流/熔断/系统保护  Sentinel
✅ 规则持久化         NacosDataSource
✅ 网关鉴权透传        AuthGlobalFilter + X-User-Id header
✅ 链路日志           RequestLogFilter + traceId
✅ 业务: User 全套     登录/注册/me/getById
✅ 业务: Product       getById (其他待搬)
✅ 业务: Category 全套  list/get/create/update/delete
✅ 业务: Address 全套   list/get/create/update/delete + 越权三态
✅ 业务: Order 空壳     /order/hello

❌ Redis             (G1 待办)
❌ RabbitMQ          (G2 待办)
❌ Favorite          (依赖 Redis)
❌ CartItem          (依赖 Redis)
❌ Orders            (依赖 Redis + MQ)
❌ Seckill           (依赖 Redis + MQ + Lua)
```

---

**G 阶段第一波完毕。** 下一步建议 G1 接 Redis, 解锁后面所有"涉及缓存/分布式锁"的业务模块。


---


# 第 42 章  G1  Redis 接入 (order 服务先吃螃蟹)

## 42.1  目标 + 设计原则

```
目标:
  在 mini-mall-order 服务接入 Redis, 跑通【最简】set/get/del 三接口
  + redis-cli 验证 value 是 JSON 不是二进制乱码

设计原则 (feedback_concrete_first):
  ❌ 不建 common-redis 公共模块 (虽然 task #54 规划过)
  ✅ 每个用 Redis 的服务自己写一份 RedisConfig
  ⏰ 等【3 个】服务都用了再抽公共模块
```

## 42.2  Redis 已在本机跑

```bash
> "$REDIS_CLI" -h 127.0.0.1 -p 6379 PING
PONG

> "$REDIS_CLI" -h 127.0.0.1 -p 6379 INFO server
redis_version:5.0.10
redis_mode:standalone
os:Windows
```

→ 本机 Windows Redis 5.0.10 单机模式, 没装 Docker 容器。

## 42.3  Step 1 — order/pom.xml 加 Redis starter

```xml
<!-- ⭐ G1 新增: Redis 客户端 (lettuce, Spring Boot 默认)
     starter 自带:
       ① Lettuce 客户端 (基于 Netty, 线程安全, 性能比 Jedis 好)
       ② RedisAutoConfiguration (读 spring.data.redis.* 自动配 RedisTemplate)
       ③ StringRedisTemplate (专门处理 String, 不用我们手配)
     不引版本号, Boot BOM 已锁 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### 知识点 ① 为啥 Boot 默认选 Lettuce 不选 Jedis？

| 客户端 | 实现 | 线程安全 | 默认值 |
|---|---|---|---|
| Lettuce | Netty 异步 | ✅ 是 (**一个连接全应用共享**) | ⭐Boot 2.x 起默认 |
| Jedis | 同步 BIO | ❌ 否 (每个线程一个连接, 需要连接池) | 旧默认 |

→ Lettuce 一个 Netty 连接处理所有请求 → 同等并发下**资源占用更低**。

## 42.4  Step 2 — order/application.yml 加 redis 配置

```yaml
spring:
  # ─── G1 新增: Redis 连接配置 ────────────────────────────────
  # ⚠️ Boot 2.x 是 spring.redis.*, Boot 3.x 改成 spring.data.redis.*
  #    前缀写错根本不生效, 默认连 localhost 也能跑, 但一旦换地址就抓瞎
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0                   # Redis 默认 16 个 db (0~15), 我们用 0 号
      timeout: 3000ms               # 命令超时, 默认无限等
      # password:                   # 没设密码就注释掉
      lettuce:
        pool:
          max-active: 8             # 同时能借出的连接数上限
          max-idle: 8               # 空闲池中最多保留 8 个
          min-idle: 0               # 最少 0 个
          max-wait: 1000ms          # 池满了再借, 最多等 1 秒
```

### 知识点 ② Boot 2 → 3 的 Redis 配置前缀变更

```
Boot 2.x: spring.redis.host
Boot 3.x: spring.data.redis.host    ← 多了 .data
```

→ 直接 copy 旧项目 yml 进 Boot 3 项目, **redis 写在 `spring.redis.*` 不会报错也不会生效**, 默默连 localhost:6379, 改 host 都没用——是隐蔽性最高的坑之一。

## 42.5  Step 3 — 写 RedisConfig (核心)

```java
package com.minimall.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 两个核心序列化器
        StringRedisSerializer stringSer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSer = new GenericJackson2JsonRedisSerializer();

        // 4 个位置分别设
        template.setKeySerializer(stringSer);          // 大 key
        template.setValueSerializer(jsonSer);          // String/List/Set/ZSet 的 value
        template.setHashKeySerializer(stringSer);      // Hash 的 field 名
        template.setHashValueSerializer(jsonSer);      // Hash 的 field 值

        template.afterPropertiesSet();                 // 必调! 否则用默认
        return template;
    }
}
```

### 知识点 ③ "4 个序列化器"是什么

```
redisTemplate.opsForValue().set("user:1", new User(1, "alice"));
        │                       │              │
        │                       │              └─ value: 序列化成什么?
        │                       └─ key: 序列化成什么?
        └─ 模板自己

redisTemplate.opsForHash().put("cart:1", "p101", 2);
        │                       │         │      │
        │                       │         │      └─ hash value
        │                       │         └─ hash key (field 名)
        │                       └─ key (大 key)
        └─ 模板自己

总共 4 个独立的序列化位置:
  ① keySerializer        大 key
  ② valueSerializer      String/List/Set/ZSet 的 value
  ③ hashKeySerializer    Hash 的 field 名
  ④ hashValueSerializer  Hash 的 field 值
```

### 知识点 ④ 为啥不用 Spring 默认 RedisTemplate？

默认 Bean 用 `JdkSerializationRedisSerializer`:

```
redisTemplate.opsForValue().set("user:1", "alice");
              ↓ 默认 JDK 序列化
存进 Redis: \xac\xed\x00\x05t\x00\x05alice
              ↓ redis-cli 看
"\xac\xed\x00\x05t\x00\x05alice"   ← 乱码! 不可读
```

→ 必须自己配 `StringRedisSerializer` (key) + `GenericJackson2JsonRedisSerializer` (value)。

### 知识点 ⑤ `GenericJackson2JsonRedisSerializer` vs `Jackson2JsonRedisSerializer<T>`

| 维度 | `Generic...` ⭐我们用 | `Jackson2...<T>` |
|---|---|---|
| 类型支持 | **任意对象**, 取出来自动还原原类型 | **绑死 T**, 取出来只能是 T |
| 包装 | value 多一个 `@class` 字段 | 干净 JSON |
| 灵活性 | 高 (一个 Bean 走天下) | 低 (每种类型一个 Bean) |
| 暴露 | `@class` 暴露类全限定名 | 不暴露 |
| 用在 | 通用 RedisTemplate | 单一类型缓存 (如 RedisCacheManager) |

### 知识点 ⑥ 6 个内置序列化器全清单

| 序列化器 | value 长啥样 | 何时用 |
|---|---|---|
| `JdkSerializationRedisSerializer` ⭐默认 | `\xac\xed...` 二进制 | **从不**, 必须替换 |
| `StringRedisSerializer` | `hello` | key 永远用它 |
| `GenericJackson2JsonRedisSerializer` ⭐我们用 | `{"@class":"...","id":1}` | **value 通用首选** |
| `Jackson2JsonRedisSerializer<T>` | `{"id":1}` | 单一类型缓存 |
| `GenericToStringSerializer<T>` | `123` | 数字 (少用) |
| `OxmSerializer` | XML | 永不 |

### 知识点 ⑦ `afterPropertiesSet()` 必调

```java
template.afterPropertiesSet();
// 内部检查 initialized 标记, 应用所有 set 进去的序列化器
// 不调 → Spring 检查不到 initialized 为 true, 默认会回退用旧序列化器
```

## 42.6  Step 4 — 写 RedisTestController

```java
package com.minimall.order.controller;

import com.minimall.common.core.domain.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/order/redis")
public class RedisTestController {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/set/{key}/{value}")
    public Result<String> set(@PathVariable String key, @PathVariable String value) {
        redisTemplate.opsForValue().set(key, value);
        return Result.success("ok");
    }

    @GetMapping("/get/{key}")
    public Result<Object> get(@PathVariable String key) {
        return Result.success(redisTemplate.opsForValue().get(key));
    }

    @GetMapping("/del/{key}")
    public Result<Boolean> del(@PathVariable String key) {
        return Result.success(redisTemplate.delete(key));
    }
}
```

### 知识点 ⑧ RedisTemplate API 全景

```
redisTemplate
├── opsForValue()    String / 普通 value 操作 (SET/GET/INCR/DECR)
├── opsForHash()     Hash 操作 (HSET/HGET/HDEL/HGETALL)         ← 购物车会用
├── opsForList()     List 操作 (LPUSH/RPUSH/LPOP/LRANGE)
├── opsForSet()      Set 操作 (SADD/SREM/SMEMBERS/SISMEMBER)    ← 收藏会用
├── opsForZSet()     ZSet 操作 (ZADD/ZRANGE/ZINCRBY)            ← 排行榜
│
├── delete(k)        DEL k        (返 Boolean)
├── delete(keys)     DEL k1 k2... (返 Long)
├── expire(k, t)     EXPIRE k t
├── hasKey(k)        EXISTS k
└── keys(pattern)    KEYS pattern (⚠️ 生产慎用, 会阻塞)
```

### 知识点 ⑨ 5 种 Template 用法 (顺便回答"为啥只用 RedisTemplate")

| 方式 | 何时用 | 缺点 |
|---|---|---|
| `RedisTemplate<String, Object>` ⭐**通用** | 大部分场景 | 要自己配序列化器 |
| `StringRedisTemplate` | 纯字符串 (计数器) | **不能存对象** |
| `ReactiveRedisTemplate` | WebFlux (gateway 用) | 阻塞式服务不合适 |
| `@Cacheable` 注解 | 简单读多写少接口 | 粒度粗, 难调试 |
| `@RedisHash + CrudRepository` | 几乎没人用 | 把 Redis 当 DB 反模式 |

## 42.7  Step 5 — 联调验证

```bash
curl http://127.0.0.1:9003/order/redis/set/test:hello/world
# → {"code":200,"message":"操作成功","data":"ok"}

curl http://127.0.0.1:9003/order/redis/get/test:hello
# → {"code":200,"message":"操作成功","data":"world"}

# ⭐ 关键: redis-cli 直读, 看 value 是 JSON 字符串还是二进制乱码
"$REDIS_CLI" -h 127.0.0.1 -p 6379 GET "test:hello"
# → "world"   ← 漂亮的 JSON 字符串! (带引号是 JSON 规范)
#               不是 \xac\xed... 二进制 → 证明序列化器生效

curl http://127.0.0.1:9003/order/redis/del/test:hello
# → {"code":200,"message":"操作成功","data":true}
```

### 知识点 ⑩ redis-cli GET 看到 `"world"` (带引号) 的含义

```
JSON 规范: 字符串前后必须加双引号
  → "world" 表示 字符串"world"
  → world  没引号是 JSON 关键字 (true/false/null) 或数字

GenericJackson2JsonRedisSerializer 序列化 String 时:
  "world" → JSON → "world"  (字符串带引号)
```

→ 这就是 JSON 序列化生效的铁证。


---


# 第 43 章  G3.5  Favorite 搬到 product 服务 (Redis Set 实战)

## 43.1  搬迁清单 + 关键差异

| 文件 | 单体 | 微服务 |
|---|---|---|
| 接口 IFavoriteService | `com.minimall.minimall.service` | `com.minimall.product.service` |
| 实现 FavoriteServiceImpl | 同上 | `com.minimall.product.service.impl` |
| Controller | 同上 | `com.minimall.product.controller` |
| product/pom.xml | — | ⭐ 加 Redis starter (**第二份**) |
| product/RedisConfig | — | ⭐ 新建 (**第二份**, 跟 order 一样) |

⚠️ **没有 entity / mapper**——Favorite **数据全在 Redis**, 不沾 MySQL!

## 43.2  Redis 数据结构选 Set 的理由

```
key   = "favorite:user:{userId}"    (一个用户一个 key)
value = Set<Long> productIds       (Set 天然去重 + O(1) 判成员)

为啥选 Set 不选 List?
  ✅ 去重: 同一商品收藏多次还是一份
  ✅ 成员判断快: SISMEMBER O(1), List 要 LRANGE 全扫
  ✅ 集合运算: SINTER 求"两人共同收藏" / SUNION 求"猜你喜欢"

为啥 value 不存 Product 对象?
  ❌ 商品信息会变 (改价/改名), 存快照会脏数据
  ✅ 只存 id, 用时实时去查【最新】商品
```

## 43.3  product 接 Redis (第二份, feedback_concrete_first 实战)

`product/pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

`product/application.yml`:
```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
      timeout: 3000ms
      lettuce:
        pool: { max-active: 8, max-idle: 8, min-idle: 0, max-wait: 1000ms }
```

`product/RedisConfig.java`:
```java
// 跟 order 那份完全一样, 唯一区别: 包名 com.minimall.product.config
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        // ... 同 order
    }
}
```

### 知识点 ① 为啥不立刻抽公共 common-redis？

按 `feedback_concrete_first` 原则:

```
"重复 N 次"判断:
  1️⃣ order 写 RedisConfig (第一次, 不抽)
  2️⃣ product 写 RedisConfig (第二次, 还不抽! 因为只复制粘贴, 0 思考成本)
  3️⃣ user 也要 RedisConfig (第三次, 此时再抽 common-redis 痛感真实)

为啥不在第一次就抽?
  → 没用过的东西不知道边界在哪, 抽得太早往往抽错
  → 教学需要先"被痛过", 才能理解抽象的价值
```

## 43.4  IFavoriteService 接口

```java
public interface IFavoriteService {
    /** 收藏 (Set 天然去重, 重复 add 不报错) */
    void add(Long userId, Long productId);

    /** 取消收藏 */
    void remove(Long userId, Long productId);

    /** 我的收藏列表 (返完整商品详情) */
    List<Product> listMy(Long userId);

    /** 是否已收藏 */
    boolean isFavorited(Long userId, Long productId);
}
```

### 知识点 ② 为啥不 `extends IService<Favorite>` ？

Address/Category 都 extends, Favorite 不 extends——因为:

```
Favorite【没有 entity】, 数据全在 Redis
没有 entity → 没有 Mapper → IService<T> 的 T 不存在
所以裸接口, 全部方法自己定义
```

## 43.5  FavoriteServiceImpl 核心代码

```java
@Service
public class FavoriteServiceImpl implements IFavoriteService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 复用本服务的 ProductService, 同进程直 @Autowired, 0 Feign */
    @Autowired
    private IProductService productService;

    private String key(Long userId) {
        return "favorite:user:" + userId;
    }

    @Override
    public void add(Long userId, Long productId) {
        // SADD k v...
        redisTemplate.opsForSet().add(key(userId), productId);
    }

    @Override
    public void remove(Long userId, Long productId) {
        // SREM k v
        redisTemplate.opsForSet().remove(key(userId), productId);
    }

    @Override
    public List<Product> listMy(Long userId) {
        // ① SMEMBERS 取所有
        Set<Object> productIds = redisTemplate.opsForSet().members(key(userId));

        List<Product> result = new ArrayList<>();
        if (productIds == null || productIds.isEmpty()) return result;

        // ② 循环调本服务 ProductService 查详情
        for (Object pidObj : productIds) {
            // ⭐ productIds 元素类型 Object, 必须 toString → Long
            Long pid = Long.valueOf(pidObj.toString());
            Product p = productService.getById(pid);
            if (p != null) result.add(p);
        }

        return result;
    }

    @Override
    public boolean isFavorited(Long userId, Long productId) {
        // SISMEMBER k v
        Boolean is = redisTemplate.opsForSet().isMember(key(userId), productId);
        return Boolean.TRUE.equals(is);
    }
}
```

### 知识点 ③ 为啥要 `Long.valueOf(obj.toString())`？

```
存进 Redis 时:
  set.add(key, 1L)
  → JSON 序列化: 1L → "1"  (序列化器存的是字符串数字)

取出来时:
  Set<Object> set = redisTemplate.opsForSet().members(key)
  → set 里元素类型是 Integer / Long (JSON 反序列化数字默认这俩)
  → 不能直接 (Long) cast (可能是 Integer ClassCastException)
  → 安全做法: obj.toString() 再 Long.valueOf

为啥 RedisTemplate 不告诉我们具体类型?
  → 因为它泛型是 <String, Object>, 设计上就是"任意对象"
  → 类型信息在序列化时丢失了 (Integer 1 / Long 1 都序列化成 "1")
```

### 知识点 ④ 为啥放 product 服务不放 user 服务？

```
Favorite 涉及【userId + productId + 商品详情】

放 user 服务: 调商品要 Feign → HTTP → 慢一截 + 复杂
放 product 服务: 直接 @Autowired ProductService → 同进程 → 0 网络成本

→ 哪个服务调依赖最频繁, 就放哪个服务
```

## 43.6  FavoriteController

```java
@RestController
@RequestMapping("/favorite")
public class FavoriteController {

    @Autowired private IFavoriteService favoriteService;

    @PostMapping("/{productId}")
    public Result<Void> add(@PathVariable Long productId,
                            @RequestHeader("X-User-Id") Long userId) {
        favoriteService.add(userId, productId);
        return Result.success();
    }

    @DeleteMapping("/{productId}")
    public Result<Void> remove(@PathVariable Long productId,
                               @RequestHeader("X-User-Id") Long userId) {
        favoriteService.remove(userId, productId);
        return Result.success();
    }

    @GetMapping("/my")
    public Result<List<Product>> listMy(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(favoriteService.listMy(userId));
    }

    @GetMapping("/{productId}/exists")
    public Result<Boolean> exists(@PathVariable Long productId,
                                  @RequestHeader("X-User-Id") Long userId) {
        return Result.success(favoriteService.isFavorited(userId, productId));
    }
}
```

## 43.7  网关路由 + 验证

```yaml
# gateway/application.yml
- id: favorite-route
  uri: lb://mini-mall-product
  predicates:
    - Path=/favorite/**
```

**验证关键 4 步:**
```bash
# ⭐ POST /favorite/1 (重复 3 次)
# → 第 2 次返成功不报错 (Set 去重)

# ⭐ GET /favorite/my
# → 返完整商品 JSON (含 name/price)

# ⭐ redis-cli SMEMBERS favorite:user:1
# → "1" "2"   ← 漂亮的 Set 成员, 不是 JDK 二进制

# ⭐ DELETE /favorite/1
# → 再 SMEMBERS 只剩 "2"
```


---


# 第 44 章  G3.4  CartItem 搬到 order (⭐ Feign 跨服务调用首次实战)

## 44.1  这一章是【整轮搬迁的难度顶点】

| 维度 | Category/Address | Favorite | CartItem ⭐ |
|---|---|---|---|
| 存储 | MySQL | Redis Set | MySQL |
| 跨服务依赖 | 无 | 无 | **Feign 调 product** |
| 越权校验 | ✅ | 不需要 (Redis key 已隔离) | ✅ |
| 容错处理 | 简单 | 简单 | **Feign 失败要 fallback** |
| 拼装 VO | 单表 | 单表 + 本地 service | **跨服务 + 单表** |

## 44.2  改 7 个文件 + 改启动类

```
mini-mall-order/
├── pom.xml                                ← +MP +MySQL
├── MiniMallOrderApplication.java          ← +@MapperScan +@EnableFeignClients
├── application.yml                        ← +datasource +mybatis-plus 配置
└── src/main/java/com/minimall/order/
    ├── client/ProductFeignClient.java     ← 新建 (第二份)
    ├── entity/CartItem.java               ← 搬
    ├── mapper/CartItemMapper.java         ← 搬
    ├── dto/AddCartDTO.java                ← 搬
    ├── vo/CartItemVO.java                 ← 搬
    ├── service/ICartItemService.java      ← 搬
    ├── service/impl/CartItemServiceImpl.java ← 搬 + 改 Feign
    └── controller/CartItemController.java ← 搬 + 改 @RequestHeader
```

## 44.3  Step 1 — order/pom.xml 加 MP + MySQL

```xml
<!-- ⭐ G3.4 新增: MyBatis-Plus -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
</dependency>

<!-- ⭐ G3.4 新增: MySQL 驱动 -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- 注: Feign 依赖在 common-core 里, 不用单独引 -->
```

### 知识点 ① Feign 依赖在 common-core, 不在子模块

```
common-core/pom.xml:
  <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-openfeign</artifactId>
  </dependency>

→ 所有引 common-core 的子模块自动得到 Feign
→ 子 pom 不用写 (RuoYi-Cloud 标准玩法)
```

## 44.4  Step 2 — order/application.yml 加 datasource

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/mini_mall?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: 123456

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: isDeleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

### 知识点 ② 数据库 url 几个关键参数

| 参数 | 含义 |
|---|---|
| `useUnicode=true&characterEncoding=utf-8` | 字符集 UTF-8 |
| `useSSL=false` | 关 SSL (本机开发不用) |
| `serverTimezone=Asia/Shanghai` | **必加!** 不加 MySQL 8 报时区错 |
| `allowPublicKeyRetrieval=true` | MySQL 8 公钥获取 (caching_sha2_password) |

## 44.5  Step 3 — 启动类加 @MapperScan + @EnableFeignClients

```java
@SpringBootApplication
@ComponentScan("com.minimall")
@MapperScan("com.minimall.order.mapper")              // ⭐ MP 扫 Mapper
@EnableFeignClients(basePackages = "com.minimall.order.client")  // ⭐ Feign 扫 @FeignClient
public class MiniMallOrderApplication { ... }
```

### 知识点 ③ 4 个注解 4 件事

| 注解 | 作用 |
|---|---|
| `@SpringBootApplication` | `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan` (默认本类包) |
| `@ComponentScan("com.minimall")` | 扩到 com.minimall 整层, 能扫到 common-core |
| `@MapperScan("...mapper")` | MP 给 mapper 接口生成代理 Bean |
| `@EnableFeignClients(basePackages="...client")` | Feign 给 @FeignClient 接口生成代理 Bean |

→ **两个 Scan 都是给"接口生成动态代理"**, 玩法一模一样, 因为 Mapper 和 FeignClient 都是接口没实现类。

## 44.6  Step 4 — 写 ProductFeignClient (核心)

```java
package com.minimall.order.client;

import com.minimall.common.core.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "mini-mall-product")
public interface ProductFeignClient {

    @GetMapping("/product/{id}")
    Result<Map<String, Object>> getById(@PathVariable("id") Long id);
}
```

### 知识点 ④ Feign 的"魔法"

```
你写: 一个接口 + @FeignClient 注解
启动: @EnableFeignClients 触发, Spring 用 JDK 动态代理生成实例放容器
调用: productFeignClient.getById(1L)
       │
       ▼
    动态代理拦截
       │
       ▼
    把方法 + 参数 → 编织成 HTTP 请求
       │
       ▼
    Nacos 查 mini-mall-product 健康实例列表
       │
       ▼
    LoadBalancer 挑一个 (默认轮询)
       │
       ▼
    发 HTTP GET http://实例:9002/product/1
       │
       ▼
    解析返回 JSON 成 Result<Map<String, Object>>
       │
       ▼
    返给业务代码
```

### 知识点 ⑤ 为啥返 `Result<Map<String, Object>>` 不返 `Result<Product>`？

```
Product 类在 mini-mall-product 模块
order 模块依赖里【没有 product】 → 引不到 Product 类

简单方案 (本项目): 用 Map 接, 业务自己 get("name") get("price") 强转
正经方案: 抽 mini-mall-product-api 模块只放 DTO 共用 (后续要做)
```

### 知识点 ⑥ Feign 跟 RestTemplate / WebClient 比

| 工具 | 写法 | 优 | 劣 |
|---|---|---|---|
| **Feign** ⭐ | 写接口, 全靠注解 | 声明式, 跟本地调用一样 | 学习成本一开始有 |
| RestTemplate | `restTemplate.getForObject(...)` | 简单直观 | 字符串拼 URL, 易错 |
| WebClient | `webClient.get().uri(...).retrieve()...` | 响应式 | WebFlux 才合适 |

→ Spring Cloud 微服务**一律用 Feign**, 声明式声明远程接口, 写起来跟调本地 Service 没区别。

## 44.7  Step 5 — CartItemServiceImpl 核心改动

```java
@Service
public class CartItemServiceImpl
        extends ServiceImpl<CartItemMapper, CartItem>
        implements ICartItemService {

    @Autowired
    private ProductFeignClient productFeignClient;     // ⭐ Feign 注入

    @Override
    public void addToCart(Long userId, Long productId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(400, "数量必须大于 0");
        }

        // 查 (user_id, product_id) 是否已存在 (MP 自动加 is_deleted=0)
        QueryWrapper<CartItem> w = new QueryWrapper<>();
        w.eq("user_id", userId).eq("product_id", productId);
        CartItem existing = this.getOne(w);

        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
            this.updateById(existing);
        } else {
            CartItem item = new CartItem();
            item.setUserId(userId);
            item.setProductId(productId);
            item.setQuantity(quantity);
            this.save(item);
        }
    }

    @Override
    public List<CartItemVO> listMyCart(Long userId) {
        // 1) 查购物车项 (一条 SQL)
        QueryWrapper<CartItem> w = new QueryWrapper<>();
        w.eq("user_id", userId).orderByDesc("create_time");
        List<CartItem> items = this.list(w);

        List<CartItemVO> result = new ArrayList<>();
        if (items.isEmpty()) return result;

        // 2) 循环调 Feign 查商品 (N 次 HTTP, 性能差但简单)
        for (CartItem item : items) {
            Result<Map<String, Object>> resp = productFeignClient.getById(item.getProductId());

            // 商品不存在 / product 服务挂了 → 跳过
            if (resp == null || resp.getCode() != 200 || resp.getData() == null) continue;

            Map<String, Object> p = resp.getData();

            // ⭐ Map.get 强转
            BigDecimal price = new BigDecimal(p.get("price").toString());

            CartItemVO vo = new CartItemVO();
            vo.setCartItemId(item.getId());
            vo.setProductId(item.getProductId());
            vo.setProductName((String) p.get("name"));
            vo.setProductImage((String) p.get("coverImage"));
            vo.setPrice(price);
            vo.setQuantity(item.getQuantity());
            // ⭐ BigDecimal 必须 .multiply, 不能 *
            vo.setSubtotal(price.multiply(BigDecimal.valueOf(item.getQuantity())));

            result.add(vo);
        }

        return result;
    }
}
```

### 知识点 ⑦ 为啥用 `new BigDecimal(toString())` 不直接 cast？

```
单价 4999.00 在 MySQL 是 DECIMAL(10,2)
Jackson 反序列化 JSON 数字时:
  - 整数 → Integer / Long
  - 小数 → Double (默认!) ⚠️

p.get("price") 是 Double 4999.0
直接 (BigDecimal) cast 报 ClassCastException!

安全方案: new BigDecimal(toString())
  Double.toString() → "4999.0"
  new BigDecimal("4999.0") → 精确 BigDecimal 4999.00
```

### 知识点 ⑧ 为啥 BigDecimal 不能用 `*`？

```
Java 不支持运算符重载
BigDecimal 是【对象】不是基本类型, * 报编译错

只能用方法:
  bd1.multiply(bd2)
  bd1.add(bd2)
  bd1.subtract(bd2)
  bd1.divide(bd2)
```

## 44.8  Step 6 — CartItemController

```java
@RestController
@RequestMapping("/cart")
public class CartItemController {

    @Autowired private ICartItemService cartItemService;

    @GetMapping
    public Result<List<CartItemVO>> myCart(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(cartItemService.listMyCart(userId));
    }

    @PostMapping
    public Result<Void> add(@RequestBody AddCartDTO dto,
                            @RequestHeader("X-User-Id") Long userId) {
        cartItemService.addToCart(userId, dto.getProductId(), dto.getQuantity());
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> updateQuantity(@PathVariable Long id,
                                       @RequestBody Map<String, Integer> body,
                                       @RequestHeader("X-User-Id") Long userId) {
        CartItem item = getAndCheckOwn(id, userId);
        Integer quantity = body.get("quantity");
        if (quantity == null || quantity <= 0) {
            cartItemService.removeById(id);
        } else {
            item.setQuantity(quantity);
            cartItemService.updateById(item);
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestHeader("X-User-Id") Long userId) {
        getAndCheckOwn(id, userId);
        cartItemService.removeById(id);
        return Result.success();
    }

    private CartItem getAndCheckOwn(Long id, Long currentUserId) {
        CartItem item = cartItemService.getById(id);
        if (item == null) throw new BusinessException(404, "购物车项不存在");
        if (!item.getUserId().equals(currentUserId))
            throw new BusinessException(403, "无权操作该购物车项");
        return item;
    }
}
```

## 44.9  Step 7 — 网关路由 + 验证

```yaml
- id: cart-route
  uri: lb://mini-mall-order
  predicates:
    - Path=/cart/**

- id: order-route       # 顺手补 G3.0 漏的
  uri: lb://mini-mall-order
  predicates:
    - Path=/order/**
```

**验证 8 步关键证据:**

| # | 验证点 |
|---|---|
| ① POST 加购商品 1×2 | ✅ 写库 |
| ③ POST 加购商品 1+3 | ✅ **累加** quantity=5 |
| ④ **GET /cart** | ⭐⭐⭐ 看到 "小米 14 Pro" 4999 subtotal=24995 |
| ⑥ PUT 改数量 7 | ✅ |
| ⑦ DELETE 不存在 999 | ✅ 404 |
| ⑧ DELETE 自己的 | ✅ + 列表空 |

→ ④ 是核心证据: `productName="小米 14 Pro"` 这个字段**是 product:9002 返回的**, 证明 Feign 跨服务调用工作。


---


# 第 45 章  踩坑实录: 单体留下的 cart 唯一索引 bug

## 45.1  现象

```bash
② POST /cart {"productId":2,"quantity":1}
→ {"code":500,"message":"系统繁忙","data":null}
```

但 ①③ POST 商品 1 完全正常, 怪。

## 45.2  排查日志

```
Caused by: java.sql.SQLIntegrityConstraintViolationException:
    Duplicate entry '1-2' for key 'cart_item.uk_user_product'
```

→ 数据库唯一约束 `(user_id, product_id)` 撞了。

## 45.3  根因

查 alice 购物车 (含逻辑删除):
```sql
SELECT id, user_id, product_id, quantity, is_deleted
FROM cart_item WHERE user_id=1;

 id |  user_id |  product_id | quantity | is_deleted
----+----------+-------------+----------+-----------
 17 |        1 |           2 |        1 |          1  ← 单体留下的脏数据
 18 |        1 |           1 |        5 |          0
```

**矛盾点:**

```
表设计: UNIQUE INDEX uk_user_product (user_id, product_id)
        ⚠️ 不包含 is_deleted

业务逻辑 (MP @TableLogic):
  查询: WHERE user_id=1 AND product_id=2 AND is_deleted=0  ← 查不到! (id=17 is_deleted=1)
  插入: INSERT INTO cart_item ...                          ← MySQL 唯一约束认为重复! 炸!

后果:
  任何用户把"已删过的商品"再加购 = 永远炸 500
```

## 45.4  3 个修法

| 方案 | 改动 | 优 | 劣 |
|---|---|---|---|
| ① 改唯一索引 | `UNIQUE (user_id, product_id, is_deleted)` | 简单 | 删除/恢复多次累计逻辑删行 |
| ② addToCart 兼容老脏数据 | 查时不过滤 is_deleted, 找到删除的就反激活 (set is_deleted=0) | 不积累垃圾行 | 业务逻辑复杂 |
| ③ 改物理删除 | 删 @TableLogic, removeById 真删 | 最简单 | 失去"购物车历史" |

**生产推荐 ②** —— 这是真正符合"购物车"语义的设计。

## 45.5  本次绕过方法 (教学用)

```sql
DELETE FROM cart_item WHERE id=17;   -- 物理删脏数据
-- 然后重试 ②, 一切正常
```

→ 这个修法**不在本轮搬迁范围**, 单体里也会炸——但**作为知识点必须记**, 这就是"搬迁过程中暴露老 bug"的真实写照。


---


# 第 46 章  本轮章节地图 + 累计能力 + 待办

## 46.1  v10 → v11 (本轮新增) 章节地图

| 章 | 内容 |
|---|---|
| 1~31 | 工程基础 + A/B/C/D 阶段 (v8) |
| 32 | E 阶段 Nacos 注册中心 (v8) |
| 33 | F1 Nacos 配置中心 (v9) |
| 34 | F2 Sentinel 全套 (v9) |
| 35~41 | G3.0~G3.3 + MP 解析 (v10) |
| **42** | **G1 Redis 接入 + 6 序列化器 + 5 Template 用法** |
| **43** | **G3.5 Favorite → product (Redis Set 实战)** |
| **44** | **G3.4 CartItem → order (⭐ Feign 跨服务实战)** |
| **45** | **踩坑实录: 单体 cart 唯一索引 bug** |
| 46 | 本章 (索引) |

## 46.2  累计能力清单

```
✅ 服务注册发现       Nacos discovery
✅ 配置中心 + 动态刷新  Nacos config + @RefreshScope
✅ 限流/熔断/系统保护  Sentinel
✅ 规则持久化         NacosDataSource
✅ 网关鉴权透传        AuthGlobalFilter + X-User-Id
✅ 链路日志           RequestLogFilter
✅ Redis 集成 + 自定义序列化器  GenericJackson2JsonRedisSerializer
✅ Feign 跨服务调用    order→product 真用上了
✅ 业务: User 全套
✅ 业务: Product (CRUD + Favorite)
✅ 业务: Category 全套
✅ 业务: Address 全套
✅ 业务: Favorite 全套 (Redis Set 实战)
✅ 业务: CartItem 全套 (MySQL + Feign 实战)
✅ 业务: Order 空壳

❌ RabbitMQ          (G2 待办)
❌ Orders            (G3.7 待办, 依赖 G2)
❌ Seckill           (G3.8 待办, 依赖 G2 + Lua)
```

## 46.3  待办清单 (按优先级)

| 优先 | 任务 | 阻塞 |
|---|---|---|
| ★★★ | **修单体 cart 唯一索引 bug** (方案 ②) | 用户体验 |
| ★★ | G2 接 RabbitMQ | 阻塞 Orders/Seckill |
| ★★ | G3.7 Orders → order | 等 G2 |
| ★ | G3.8 Seckill → order | 等 G2 + Lua |
| ★ | 抽 common-redis 公共模块 (现在 2 份, 等 3 份再抽) |
| ★ | 抽 common-product-api 模块 (DTO 共用, 避免 Map<String, Object>) |
| ★ | 顺手补 MybatisPlusConfig (分页插件) |
| ★ | 顺手补 MetaObjectHandler (自动填时间) |

## 46.4  搬迁实战经验沉淀

| 经验 | 来源 |
|---|---|
| 跨服务上下文 = HTTP Header (不能 ThreadLocal) | G3.2 Address |
| Redis value 必须自定义序列化器, 不然乱码 | G1 |
| 业务模块放哪个服务 = 看它依赖谁最多 | G3.5 Favorite |
| Feign 接口返 `Map<String, Object>` (跨模块引不到对方 Entity) | G3.4 CartItem |
| BigDecimal 不能 cast Double, 用 `new BigDecimal(toString())` | G3.4 |
| BigDecimal 不能用 `*`, 只能 `.multiply()` | G3.4 |
| 单体的设计 bug 在搬迁时会暴露 (cart 唯一索引) | G3.4 第 45 章 |
| `feedback_concrete_first`: 同样代码重复 2 次还不抽公共模块 | G3.5 + G3.4 |

---

**G 阶段第二波完毕。** 微服务从"治理就绪 + 业务覆盖低" 走到 "**业务覆盖大半 + 跨服务调用通**"。下一步建议 G2 接 RabbitMQ, 解锁订单/秒杀。

---

# Chapter 47 · G2 RabbitMQ 5 大核心概念

> 这一章把 RabbitMQ 的 5 个核心抽象**讲透**, 后面所有 MQ 代码都基于这 5 个概念展开.
> 学完能回答: "为啥不直接 Producer 怼 Consumer? 为啥要拐一道 Exchange?"

## 47.1  四个角色: Producer / Consumer / Broker / Channel

```
┌──────────┐    发消息     ┌─────────────┐    投递    ┌──────────┐
│ Producer │────────────▶│   Broker    │──────────▶│ Consumer │
│  生产者  │              │  RabbitMQ   │            │  消费者  │
└──────────┘              │   服务进程  │            └──────────┘
                          └─────────────┘
   你的 Service                                         你的 @RabbitListener
   调 rabbitTemplate                                    方法
   .convertAndSend()
```

| 角色 | 是什么 | 我们项目里对应 |
|---|---|---|
| **Producer** | 发消息的代码 | `MqTestController.sendCloseNow()` 调 `rabbitTemplate.convertAndSend(...)` |
| **Consumer** | 收消息的代码 | `MqTestListener.onCloseMessage()` 标了 `@RabbitListener` |
| **Broker** | RabbitMQ 服务进程本身 | Docker 容器 `mini-mall-rabbitmq`, 监听 5672 |
| **Channel** | 一条 Connection 里的多路通道, 真正发消息的通路 | Spring AMQP 帮你管, 不用关心 |

**关键设计思想**: Producer / Consumer **不直接通讯**, 全经过 Broker 中转, 换来 3 个特性:
- **解耦**: Producer 不用知道有几个 Consumer
- **异步**: Producer 发完立即返回, 不等 Consumer 处理
- **削峰**: Consumer 慢时消息堆队列里, 不会把 Producer 拖垮

## 47.2  Queue (队列): 排队的地方

```
       ┌────────────────────────────────────┐
       │  Queue: order.close.queue          │
       │                                    │
       │  [msg5] [msg4] [msg3] [msg2] [msg1]│  ← 消费者从队头取
       │                                    │
       └────────────────────────────────────┘
              FIFO 先进先出 + 持久化
```

**特性**:
- **FIFO**: 先进先出
- **持久化** (`durable=true`): 即使 Broker 重启, 队列定义和消息都不丢
- **轮询分发**: 多个 Consumer 共监听一个队列, 一条消息只投递给一个 (不重复)
- **消息堆积容忍**: 消费者挂了, 重启后继续消费

**用 Spring 创建**:
```java
new Queue("order.close.queue", true);
// 参数: name, durable
```

## 47.3  Exchange (交换机): 路由器

**为啥要 Exchange? —— 朴素做法的痛**

❌ Producer 直接发到 Queue:
```
Producer ──── Queue A ──── Consumer 1
Producer ──── Queue B ──── Consumer 2
```
新增 Consumer 3 时, **Producer 代码要改** (多发一份给 Queue C). 强耦合.

✅ RabbitMQ 做法: Producer 只发到 Exchange, Exchange 负责按规则分发:
```
Producer ─msg─▶ Exchange ─┬─▶ Queue A → Consumer 1
                          ├─▶ Queue B → Consumer 2
                          └─▶ Queue C → Consumer 3  ← 新加 Consumer 时 Producer 0 改动
```

**用 Spring 创建** (DirectExchange):
```java
new DirectExchange("order.close.exchange", true, false);
// 参数: name, durable, autoDelete
//   autoDelete=false → 即使没 Queue 绑定它也不自动删
```

## 47.4  RoutingKey (路由键): 消息的"地址标签"

**Producer 发消息时贴的标签**, Exchange 看它决定送哪些队列.

```java
rabbitTemplate.convertAndSend(
    "order.close.exchange",   // ① 发到哪个 Exchange
    "close",                  // ② RoutingKey: 消息上的"地址标签"
    orderId                   // ③ 消息体
);
```

**寄快递类比**:
- Exchange = 快递公司
- RoutingKey = 邮编 / 地址
- Queue = 收件人

## 47.5  Binding (绑定): "RoutingKey → Queue" 的路由规则

**Exchange 怎么知道哪个 RoutingKey 该去哪个 Queue? 答: 靠 Binding 提前声明.**

```java
@Bean
public Binding closeBinding() {
    return BindingBuilder.bind(closeQueue())    // 哪个 Queue
            .to(closeExchange())                // 绑到哪个 Exchange
            .with("close");                     // 接收哪个 RoutingKey
}
```

**翻译成白话**:
> 嗨, Exchange `order.close.exchange`, 凡是 RoutingKey 等于 "close" 的消息, 都丢进 `order.close.queue`.

## 47.6  四种 Exchange 类型 (路由"算法")

| 类型 | 路由算法 | 用法 | 我们用了吗 |
|---|---|---|---|
| **Direct** | RoutingKey **完全等于**就送 | 精确匹配, 最常用 | ✅ 3 个都是 |
| **Topic** | RoutingKey **模糊匹配**:<br>`order.*.created` (一段通配)<br>`#.error` (多段通配) | 多 Consumer 监听不同维度 | ❌ |
| **Fanout** | **广播**给所有绑定的 Queue (RoutingKey 忽略) | 一条消息要多人都收<br>(如: 用户注册→发邮件+发短信+发推送) | ❌ |
| **Headers** | 按消息 header 字段匹配 (不用 RoutingKey) | 罕见 | ❌ |

**记忆口诀**:
- Direct = 你说 "close" → 我送 close 队列
- Topic = 你说 "order.*.paid" → `order.normal.paid` `order.seckill.paid` 都送
- Fanout = 不管你说啥, 我所有绑定的队列都送一份
- Headers = 忽略

**我们项目全用 Direct**, 因为业务"一对一精确路由", 不需要广播/模糊.

---

# Chapter 48 · TTL + DLX 延迟队列 (单体最骚的设计)

> **RabbitMQ 原生不支持"延迟 N 秒后发"**.
> 用 TTL (消息存活时间) + DLX (死信交换机) 拼出来这个效果, 是面试 + 真业务的高频考点.

## 48.1  为啥要延迟队列? (业务背景)

**典型场景**: 订单 30 分钟未付款自动关闭.

❌ 朴素做法: 起一个定时任务每分钟扫一次 `orders` 表, 找出超时未付款的关.
- 问题 1: 浪费数据库 IO (大部分扫描都没结果)
- 问题 2: 关单延迟最大 1 分钟
- 问题 3: 任务节点挂了关单就停了

✅ RabbitMQ 方案: 创建订单时发一条**延迟 30 分钟**的消息, 30 分钟后这条消息自动出现在 close.queue, Listener 收到就关单.
- 0 轮询, 时间精确, 自然分布式.

## 48.2  原理 (TTL + DLX 二合一)

```
[OrdersServiceImpl] 创建订单
     │
     │ rabbitTemplate.convertAndSend("delay.exchange", "delay", orderId)
     ▼
┌─ delay.exchange ─┐                ┌─ delay.queue ─────────────────────┐
│  DirectExchange  │── "delay" ───▶│  args:                            │
└──────────────────┘                │   x-message-ttl: 30000  (30秒)    │
                                    │   x-dead-letter-exchange: close   │
                                    │   x-dead-letter-routing-key: close│
                                    │                                    │
                                    │  ⏱ 消息在这里待 30 秒, 没人消费   │
                                    │  → TTL 到期, 变成"死信"           │
                                    └────────────┬───────────────────────┘
                                                 │ 自动转发
                                                 ▼
                                    ┌─ close.exchange ─┐    "close"    ┌─ close.queue ─┐
                                    │  DirectExchange  │──────────────▶│               │
                                    └──────────────────┘                └──────┬────────┘
                                                                               │
                                                                               ▼
                                                            🎧 OrderCloseListener
                                                                  → 关单 SQL
```

**关键点**:
- `delay.queue` **没有消费者**, 消息进去就死等 30 秒
- TTL 到期是"死信"的一种 (还有 NACK+不 requeue, 队列满)
- 死信会被 Broker **自动转发**到 `x-dead-letter-exchange` 指定的交换机
- 我们设的死信目标是 `close.exchange`, 它绑着 `close.queue`, Listener 在那等着

## 48.3  代码 (args Map 配置)

```java
@Bean
public Queue delayQueue() {
    Map<String, Object> args = new HashMap<>();
    args.put("x-message-ttl", 30000);                        // ⏱ 30 秒 (上线后改 30 分钟 = 1800000)
    args.put("x-dead-letter-exchange", CLOSE_EXCHANGE);      // 💀 死信转发到关单交换机
    args.put("x-dead-letter-routing-key", CLOSE_ROUTING_KEY);// 💀 死信带的 routingKey

    // Queue 构造: name, durable, exclusive, autoDelete, args
    return new Queue(DELAY_QUEUE, true, false, false, args);
}
```

**args 是 `Map<String, Object>`** —— 这些"特殊 key" (`x-*`) 是 RabbitMQ 协议里约定的, 不是 Spring 的:
- `x-message-ttl`: 消息存活时间 (ms)
- `x-dead-letter-exchange`: 死信去哪个 Exchange
- `x-dead-letter-routing-key`: 死信带的 RoutingKey
- 还有 `x-max-length` (队列最大长度), `x-expires` (队列空闲超时自动删) 等

## 48.4  实测验证 (G2.6 联调数据)

我们 G2.6 真实跑出的时间戳:
```
10:44:26.429  [MQ-CLOSE]   orderId=99    ← 立即关单 (走 close.exchange 直送)
10:44:26.478  [MQ-SECKILL] userId=1      ← 立即秒杀
10:44:56.523  [MQ-CLOSE]   orderId=100   ← ⏱ 30 秒 TTL + DLX 自动死信! (deliveryTag=2)
              ━━━━━━━━━━━━
              比立即关单晚 30.094 秒
```

**30.094 秒 ≈ TTL 设的 30 秒**. TTL+DLX 链路精准命中.

---

# Chapter 49 · G2 代码全集 + 详细解释

## 49.1  pom.xml — 一行 starter 拉一切

```xml
<!-- ⭐ G2 新增: RabbitMQ 客户端 (spring-rabbit) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

**这一行 starter 帮我们做的 5 件事**:
| # | 干啥 |
|---|---|
| ① | 拉 `spring-rabbit` + 底层 `amqp-client` (AMQP 0-9-1 协议库) |
| ② | 自动装配 `RabbitTemplate` Bean (Producer 用它发消息) |
| ③ | 自动启用 `@RabbitListener` 扫描 (Consumer 注解监听用) |
| ④ | 启动时按 `spring.rabbitmq.*` 连 5672, 连不上立即报错 (防误配) |
| ⑤ | 默认序列化用 `SimpleMessageConverter` (Serializable 走 Java native 序列化) |

**版本号不写**, Boot BOM 已锁好.

## 49.2  application.yml — 4 类配置

```yaml
spring:
  rabbitmq:
    host: 127.0.0.1
    port: 5672                        # ⭐ AMQP 协议端口 (15672 是管理台 HTTP, 不是这里)
    username: admin
    password: YOUR_RABBITMQ_PASSWORD
    virtual-host: /

    listener:
      simple:
        acknowledge-mode: manual      # ⭐ 手动 ACK
        prefetch: 1                    # ⭐ 公平分发
        retry:
          enabled: false                # 出错不自动重试, 业务手动 NACK
```

**4 类配置的含义**:

| 配置 | 默认值 | 我们的值 | 解释 |
|---|---|---|---|
| `host/port` | 127.0.0.1:5672 | 同 | AMQP 协议端口 (**不是 15672 管理台**) |
| `username/password` | guest/guest | admin/YOUR_RABBITMQ_PASSWORD | 单体 docker-compose 自定义的, 见踩坑 ③ |
| `virtual-host` | / | / | 多租户隔离, 默认 / 就行 |
| `acknowledge-mode` | auto | **manual** | 我们手动 `basicAck()` 才算消费成功. 见下面 ACK 章节 |
| `prefetch` | 250 | **1** | 公平分发: 一次只拿 1 条, 处理完再拿. 防慢消费者囤消息 |
| `retry.enabled` | false | false | 出错不自动重试 (业务 catch 里手动 NACK 决定 requeue 与否) |

## 49.3  RabbitMQConfig.java — 3 组队列基建

完整结构 (代码见项目源文件, 这里讲思路):

```
┌──────────────────────────────────────────────────────────┐
│  3 组 [Exchange + Queue + Binding] = 9 个 @Bean          │
│                                                          │
│  组 1: delay  (TTL 30s + DLX 指向 close)                 │
│        ├─ delayExchange    (DirectExchange)             │
│        ├─ delayQueue       (durable, args 加 TTL/DLX)   │
│        └─ delayBinding     (with "delay")               │
│                                                          │
│  组 2: close  (死信接收方, 真业务消费者)                 │
│        ├─ closeExchange                                  │
│        ├─ closeQueue                                     │
│        └─ closeBinding     (with "close")               │
│                                                          │
│  组 3: seckill (秒杀异步下单)                            │
│        ├─ seckillExchange                                │
│        ├─ seckillQueue                                   │
│        └─ seckillBinding   (with "seckill")             │
└──────────────────────────────────────────────────────────┘
```

**为啥这 9 个东西要 @Bean 化?**
- Spring 启动时执行 `@Bean` 把 Queue/Exchange/Binding 三个对象注册到容器
- `RabbitAdmin` (amqp-starter 自动装配的) 一看到这些 Bean, **主动连 Broker 把队列/交换机声明出去**
- 启动后 Broker 上就有这些 queue/exchange 了, **不用手动在管理台点**

**为啥常量要 `public static final`?**
```java
public static final String CLOSE_QUEUE = "order.close.queue";
```
- 因为 Listener 要这样写: `@RabbitListener(queues = RabbitMQConfig.CLOSE_QUEUE)`
- 注解的参数必须是**编译期常量**, 所以是 `public static final String`
- 全项目共享一份字符串, 改名时一处搞定

## 49.4  MqTestController.java — Producer 三连

```java
@Autowired
private RabbitTemplate rabbitTemplate;

@PostMapping("/close/{orderId}")
public Result<String> sendCloseNow(@PathVariable Long orderId) {
    rabbitTemplate.convertAndSend(
            RabbitMQConfig.CLOSE_EXCHANGE,      // 哪个交换机
            RabbitMQConfig.CLOSE_ROUTING_KEY,   // routingKey, 必须和 binding 里的 with("close") 对上
            orderId                              // 消息体
    );
    return Result.success("已发到 close 队列, orderId=" + orderId);
}
```

**`rabbitTemplate.convertAndSend(exchange, routingKey, payload)` 内部 3 步**:
1. 通过 `MessageConverter` 把 `payload` 转成 `byte[]`
   - 默认 `SimpleMessageConverter`: String/byte[]/Serializable 直转, 其他对象抛异常
   - 推荐切换 `Jackson2JsonMessageConverter` (后续学), 发 JSON 跨语言友好
2. 走 Channel 发到 Broker 的指定 Exchange
3. Broker 按 RoutingKey 找匹配的 Queue 投递 (按 Binding 规则)

**`send` vs `convertAndSend` 的区别**:
| 方法 | 入参 | 用途 |
|---|---|---|
| `send(exchange, routingKey, Message)` | 已构造好的 `Message` 对象 | 你自己控制 MessageProperties (deliveryMode, headers) |
| `convertAndSend(exchange, routingKey, Object)` | 业务对象 | Spring 帮你做序列化, 99% 场景用这个 |

## 49.5  MqTestListener.java — Consumer 全套

```java
@Component   // ⭐ 必须 @Component, Spring 启动扫描时才发现里面的 @RabbitListener
public class MqTestListener {

    @RabbitListener(queues = RabbitMQConfig.CLOSE_QUEUE)
    public void onCloseMessage(Long orderId, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            log.info("[MQ-CLOSE] 收到关单消息 orderId={}, deliveryTag={}", orderId, deliveryTag);

            // 真业务在这里调 ordersService.closeOrderByMQ(orderId)

            channel.basicAck(deliveryTag, false);    // ⭐ 手动 ACK

        } catch (Exception e) {
            log.error("[MQ-CLOSE] 处理失败", e);
            channel.basicNack(deliveryTag, false, false);  // ⭐ NACK 不 requeue
        }
    }
}
```

**5 个考点详解**:

### ① `@RabbitListener(queues = ...)` 的扫描时机
- Spring 启动时扫描所有 `@Component` 类
- 发现方法上有 `@RabbitListener`, 启动一个 **SimpleMessageListenerContainer**
- 这个 Container 在后台线程持续从指定队列拉消息

### ② 方法参数的自动注入 (按类型匹配)
```java
public void onCloseMessage(Long orderId, Message message, Channel channel)
```
| 参数 | 类型 | 注入的是 |
|---|---|---|
| `Long orderId` | Long | 消息体, 反序列化后的对象 |
| `Message message` | `org.springframework.amqp.core.Message` | 原始消息封装 (含 deliveryTag, headers, body byte[]) |
| `Channel channel` | `com.rabbitmq.client.Channel` | RabbitMQ 通道, 用来手动 ACK/NACK |

**坑**: 第一个参数的类型必须跟 **Producer 发的类型对上**.
- Producer 发 `Long orderId`, Listener 必须接 `Long orderId`.
- 我们 G2.5 一开始写错成 `String orderId`, 反序列化炸了, 后来改回 `Long`.

### ③ `deliveryTag` 是啥, 为啥必传
- Broker 给每条投递分配的**自增 long**, 唯一标识"这次投递"
- ACK/NACK 时必须告诉 Broker 你处理的是哪个 deliveryTag
- 取法: `message.getMessageProperties().getDeliveryTag()`

### ④ ACK / NACK 的 3 种姿势

| 操作 | 调用 | 语义 |
|---|---|---|
| **ACK 成功** | `channel.basicAck(deliveryTag, false)` | "处理完了, 从队列删掉" |
| **NACK 丢弃** | `channel.basicNack(deliveryTag, false, false)` | "处理失败, 别再发了" (避免死循环) |
| **NACK 重投** | `channel.basicNack(deliveryTag, false, true)` | "处理失败, 重新入队等下次" (慎用, 可能死循环) |

`basicAck(tag, multiple)` 参数 2:
- `false`: 只 ACK 这一条
- `true`: 批量 ACK 所有 <= deliveryTag 的 (优化场景, 90% 用 false)

`basicNack(tag, multiple, requeue)` 参数 3:
- `false`: 不 requeue, 消息丢弃 (有死信交换机会进死信)
- `true`: 重新入队头, 下次 Consumer 还会收到

### ⑤ 必须 throws IOException
- `basicAck` / `basicNack` 走的是 RabbitMQ Channel 的网络 IO
- 网络异常时会抛 `IOException`, 不能吞掉

---

# Chapter 50 · G2 踩坑实录

## 50.1  坑 ① Windows PATH 里 JDK 8 干扰

**现象**: `Start-Process java -jar ...` 后端口不监听, order.log + order.err.log 全是 0 字节, 进程 PID 立刻死.

**排查过程**:
1. 先怀疑 jar 损坏 → 重打 jar 仍然 0 字节输出
2. 怀疑端口被占 → netstat 看 9003 没监听 (排除冲突)
3. 跑 `(Get-Command java).Source` 一看:
   ```
   C:\Program Files (x86)\Common Files\Oracle\Java\java8path\java.exe
   ```
   PATH 里第一个 java 是 **JDK 8**! 跑 Spring Boot 3 (要 17+) 立刻报 `UnsupportedClassVersionError`, 但是奇葩的是错误连一个字节都没输出 (可能 Oracle 的 java8path 是个特殊代理).

**修法**: 直接用 JDK 21 绝对路径:
```powershell
Start-Process -FilePath "D:\jdk-21.0.11\bin\java.exe" -ArgumentList "-jar","..."
```

**经验沉淀**: Windows 多 JDK 共存环境, 启动业务用 `$env:JAVA_HOME\bin\java.exe` 或绝对路径最稳, 别依赖 PATH.

## 50.2  坑 ② Docker 容器 Paused 状态

**现象**: 5672 端口监听着, 但 Spring AMQP 启动报:
```
Caused by: com.rabbitmq.client.ShutdownSignalException: connection error
Caused by: java.io.EOFException: null
```

**排查**:
```bash
$ docker ps
mini-mall-rabbitmq    rabbitmq:3-management    Up 24 minutes (Paused)
```
**Paused** 状态: Docker Desktop 的代理仍然监听端口 (端口映射没断), 但容器内的 rabbitmq 进程被冻结, AMQP 握手立刻 EOF.

**修法**:
```bash
docker unpause mini-mall-rabbitmq
```

**经验沉淀**: 容器"Paused" ≠ "Down"; 端口 LISTEN 不代表服务真活. 应用日志报 EOF / ShutdownSignal 时优先查容器状态.

## 50.3  坑 ③ guest/guest 不是万能默认

**现象**:
```
Caused by: com.rabbitmq.client.AuthenticationFailureException:
  ACCESS_REFUSED - Login was refused using authentication mechanism PLAIN.
```

**根因**: 我惯性写 `username: guest / password: guest`, 但单体 docker-compose 明确写过:
```yaml
RABBITMQ_DEFAULT_USER: admin
RABBITMQ_DEFAULT_PASS: YOUR_RABBITMQ_PASSWORD
```
容器启动时把默认账号改了, guest 用户都被禁用.

**修法**: yml 改 admin/YOUR_RABBITMQ_PASSWORD, 重打 jar + 重启.

**经验沉淀**:
- 接外部资源 (DB/Redis/MQ) 时, 别假设默认账号. 先 grep 单体或 compose 看实际配的.
- RabbitMQ 默认 guest 也只能 localhost 登录, 远程登录会被拒.

## 50.4  3 坑共通: 排查"启动失败"的标准动作

| 顺序 | 动作 | 命令 |
|---|---|---|
| ① 看进程在不在 | `Get-Process -Id $pid` | 死了 = 启动炸 |
| ② 看端口监听 | `netstat -ano \| findstr LISTENING \| findstr ":9003"` | 没监听 = 没起 |
| ③ 看应用日志 | `tail -50 order.log` 找 `Caused by` | 真错误根因 |
| ④ 看依赖服务 | `docker ps`, `redis-cli ping` 等 | 依赖挂了应用启不来 |
| ⑤ 检查 PATH/JAVA | `java -version`, `(Get-Command java).Source` | 跑错版本 |

---

# Chapter 51 · G2 累计能力清单 + 待办

## 51.1  G2 完成后能干啥

| 能力 | 怎么用 |
|---|---|
| 服务间异步通讯 | order 发 MQ, 别的服务监听 (后续阶段) |
| 订单延迟关单 | 创建订单时发 delay 消息, 30 分钟后自动关单 (G3.7 用) |
| 秒杀异步下单 | 扣库存后发 seckill 消息, Listener 慢慢生成订单 (G3.8 用) |
| 消息持久化 + 重启不丢 | `durable=true` 全开 |
| 手动 ACK + 失败处理 | catch 里 NACK, 业务侧决定 requeue / 丢弃 |
| 死信链路 | delay.queue + DLX → close.queue, 完整跑通 |

## 51.2  G2 没做但已铺路的 (后续阶段补)

| 待办 | 何时做 |
|---|---|
| 真业务 Producer (`OrdersServiceImpl` 发 delay 消息) | G3.7 搬 Orders 时 |
| 真业务 Consumer (`OrderCloseListener` 真关单) | G3.7 |
| Seckill Producer/Consumer | G3.8 |
| 切 Jackson2JsonMessageConverter (发 POJO/JSON) | 真业务发 OrderEvent DTO 时 |
| Publisher confirms (确认 Broker 收到) | 生产环境必加 |
| 消费幂等 (避免重复消费导致重复关单) | G3.7 关单时加 status 判断 |

## 51.3  G2 决策沉淀

| 决策 | 选了啥 | 为啥 |
|---|---|---|
| MQ 模块归属 | 全在 **order 服务** | 订单业务都在 order, 队列归属订单, 单服务搞定 |
| Listener 写哪 | `com.minimall.order.listener` | 跟单体一致 |
| 队列声明方式 | Spring `@Bean` (代码声明) | 跟代码走版本控制, vs 管理台手点不持久 |
| ACK 模式 | 手动 ACK | 业务可控, 失败时手动 NACK |
| prefetch | 1 (公平分发) | 防慢消费者囤消息, 简单可靠 |
| Exchange 类型 | 全 Direct | 业务一对一精确路由, 不需要广播/模糊 |
| TTL 时长 | 30 秒 (测试) | 上线改 30 分钟 (1800000) |
| 默认账号 | admin/YOUR_RABBITMQ_PASSWORD | 沿用单体 docker-compose 配置, 一致 |

## 51.4  G2 搬迁经验沉淀

| 经验 | 来源 |
|---|---|
| 一个 starter 解决 5 件事 (Template + Listener + 自动连 + 序列化 + 配置读取) | G2.1 |
| `spring.rabbitmq.*` 是 Boot 2/3 统一前缀 (不像 redis 在 Boot 3 改成 `spring.data.redis.*`) | G2.2 |
| `@Bean` 化 Queue/Exchange/Binding 比管理台手建更靠谱 (跟版本控制) | G2.3 |
| `@RabbitListener` 第一参数类型必须跟 Producer 发的类型对齐 | G2.5 |
| 手动 ACK 是业务可控的关键, NACK 时 requeue 慎用 (易死循环) | G2.5 |
| TTL+DLX 拼出延迟队列的精准度 = ms 级 | G2.6 |
| Windows 多 JDK 用绝对路径启动 | 踩坑 ① |
| Docker 容器 Paused ≠ Down, 端口在但服务死 | 踩坑 ② |
| 接外部资源别假设默认账号 | 踩坑 ③ |

---

**G2 完毕**. RabbitMQ 通路打通: 3 组 Exchange/Queue/Binding + 立即关单 + 延迟关单 (30s 精准) + 秒杀异步. 下一步 G3.7 搬 Orders 用上真业务 MQ, G3.8 搬 Seckill 用 Lua + MQ.

---

# Chapter 52 · G3.7 Orders 搬迁 - 总述 + 架构图

> 这一章是【MQ 真业务通路】+【订单状态机】+【跨服务事务】的实战.
> 学完能回答: "为啥 createOrder 比 Category 复杂 10 倍? MQ 怎么从 demo 走到真业务? 不扣库存怎么搞?"

## 52.1  G3.7 是啥, 凭啥这么重

**目标**: 把单体的 Orders 业务搬到 mini-mall-order 微服务, 实现:
- 创建订单 (走完 7 步事务 + 跨服务 Feign + 发延迟 MQ)
- 查我的订单 + 查详情
- 用户付款 / 取消
- ⭐ **30 分钟未付款自动关单** (用 G2 通路实现真业务)

**为啥它是 G 阶段最复杂的一块**:
- 唯一一个【横跨 3 个服务】的业务: order 服务 + user (查地址) + product (查商品)
- 唯一一个【真用 RabbitMQ 业务通路】: 不是 demo 发个消息, 而是 30 秒延迟关单
- 唯一一个有【完整状态机】: 待付/已付/已发/已完/已取消 5 态 + 3 条迁移路径
- 唯一一个【幂等设计】: MQ 消息可能重复投递, service 必须保护

## 52.2  端到端全景图 (服务交互)

```
                           客户端 (curl / 前端)
                                │
                                │ X-User-Id: 1
                                │ POST /order
                                ▼
                ┌─────────  网关 (9080) ─────────┐
                │  路由 /order/** → lb://mini-mall-order
                └──────────────┬────────────────┘
                               │
                               ▼
        ┌──────────────────────────────────────────────────────────┐
        │             mini-mall-order (9003)                        │
        │                                                           │
        │   OrdersController.create()                              │
        │           │                                               │
        │           ▼                                               │
        │   OrdersServiceImpl.createOrder()                        │
        │     │                                                     │
        │     ├─ ① RedisLock (防重复下单)                          │
        │     ├─ ② 进 TransactionTemplate                          │
        │     │     │                                               │
        │     │     ├─ ③ userFeignClient.getAddress() ──────HTTP──▶│ mini-mall-user (9001)
        │     │     │                                               │     ↓ /user/address/{id}
        │     │     │                                               │     越权校验
        │     │     │                                               │
        │     │     ├─ ④ cartItemService (同服务) 查购物车           │
        │     │     │                                               │
        │     │     ├─ ⑤ for each: productFeignClient.getById() ──HTTP──▶│ mini-mall-product (9002)
        │     │     │                                               │       /product/{id}
        │     │     │                                               │
        │     │     ├─ ⑥ 算总价 + 构造 OrderItem (快照)            │
        │     │     ├─ ⑦ this.save(order) + saveBatch(items)        │
        │     │     └─ ⑧ removeByIds(cartItemIds)                  │
        │     │                                                     │
        │     ├─ ⑨ 事务提交 ◀━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓ │
        │     │                                                  ┃ │
        │     └─ ⑩ rabbitTemplate.convertAndSend(delay.exchange) ━┛ │
        │             │                                             │
        │             ▼                                             │
        │     ┌─ delay.queue (TTL=30s) ────────────────────┐         │
        │     │                                            │         │
        │     │  消息死等 30 秒 (没消费者)                  │         │
        │     │                                            │         │
        │     └─────────┬──────────────────────────────────┘         │
        │               │ TTL 到期 → 死信                            │
        │               ▼                                            │
        │     ┌─ close.queue ──┐                                    │
        │     └────────┬───────┘                                    │
        │              │ @RabbitListener                            │
        │              ▼                                            │
        │     🎧 OrderCloseListener.onOrderClose(orderId=10)        │
        │              │                                            │
        │              ▼                                            │
        │     ordersService.closeOrderByMQ(10)                      │
        │              │                                            │
        │              ▼                                            │
        │     UPDATE orders SET status=4 WHERE id=10 AND status=0   │
        │              │   ⭐ 幂等关键: AND status=0                 │
        │              ▼                                            │
        │     基础设施: MySQL + Redis (锁) + RabbitMQ                │
        └──────────────────────────────────────────────────────────┘
```

## 52.3  对比一下: Category (G3.1) vs Orders (G3.7) 的复杂度

| 维度 | Category (G3.1 搬) | Orders (G3.7 搬) | 倍数 |
|---|---|---|---|
| 文件数 | 4 (entity/mapper/service/controller) | 17 (含 listener / VO×3 / DTO / 工具类) | 4× |
| 跨服务依赖 | 0 | 2 (Feign 调 user + product) | 新增 |
| 用 MQ? | 否 | 是 (发延迟 + 收死信) | 新增 |
| 事务? | 单方法 @Transactional | TransactionTemplate (事务边界精控) | 升级 |
| 锁? | 否 | RedisLock (分布式锁防重复下单) | 新增 |
| 状态机? | 否 | 5 态 + 3 迁移 | 新增 |
| 幂等? | 不需要 | MQ 消费者必须幂等 | 新增 |

---

# Chapter 53 · createOrder 9 步深度解析

> 这一章把 OrdersServiceImpl.createOrder() 这个【最复杂的方法】每一步讲透.
> 学完能回答: "为啥锁要包在事务外? 为啥 MQ 要在事务提交后发? 跨服务事务怎么办?"

## 53.1  全貌 (代码骨架)

```java
public Map<String, Object> createOrder(Long userId, CreateOrderDTO dto) {
    String lockKey = "lock:order:user:" + userId;
    String owner = redisLockUtil.tryLock(lockKey, 10);   // ① 抢锁 (事务【外】)
    if (owner == null) throw new BusinessException(429, "操作太频繁");
    try {
        Map<String, Object> orderResult = transactionTemplate.execute(status -> {
            // ② ~ ⑧ 这 7 步在事务内
            return result;
        });

        // ⑨ 事务提交后才发 MQ (事务【外】)
        rabbitTemplate.convertAndSend(DELAY_EXCHANGE, DELAY_ROUTING_KEY, orderId);

        return orderResult;
    } finally {
        redisLockUtil.unlock(lockKey, owner);    // ⑩ 释放锁 (事务【外】, finally)
    }
}
```

**3 层嵌套**: `try-finally` (锁) > `transactionTemplate.execute` (事务) > 7 步业务.

## 53.2  为啥锁要包在事务外? (经典面试题)

**朴素做法 (错的)**:
```java
@Transactional
public Map<String, Object> createOrder(...) {
    String owner = redisLockUtil.tryLock(...);    // 锁在事务内
    try {
        // 业务...
    } finally {
        redisLockUtil.unlock(...);                // 释放锁也在事务内
    }
}
```

**问题**: 业务跑完 → 释放锁 → 事务还没提交! → 别的请求抢到锁 → 它读 DB 时还看不到这个订单 (因为还没提交) → **同一用户产生 2 个订单** = 锁失效!

**正确做法 (这里写的)**:
```
锁外  →  事务  →  业务  →  事务提交  →  释放锁
        ↑                    ↑
        这两步都在锁内, 别的请求拿不到锁, 安全
```

**规律**: **锁的范围 > 事务的范围**.

## 53.3  为啥 MQ 要在事务提交后发?

**朴素做法 (错的)**:
```java
@Transactional
public void createOrder(...) {
    save(order);                              // SQL: INSERT INTO orders
    rabbitTemplate.convertAndSend(orderId);   // 同时发 MQ
    // 如果这之后抛异常 → @Transactional 回滚 INSERT
    //                  → 但 MQ 消息【已经发出去了, 不会撤回】!
    //                  → 30 秒后 Listener 收到 → 关一个【不存在的订单】
}
```

这叫【**幽灵消息**】.

**正确做法 (这里写的)**:
```java
Map<String, Object> result = transactionTemplate.execute(status -> {
    save(order);    // 事务内
    return ...;
});
// ── 到这里事务已提交, 订单一定在 DB 里 ──
rabbitTemplate.convertAndSend(orderId);  // 事务外, 安全发出
```

**反过来想**: 如果 INSERT 失败抛异常, `execute()` 直接抛, 后面 `convertAndSend` 都到不了, MQ 也不会发. 完美.

**规律**: **写库 + 发消息** 永远不要原子. 让它们【一前一后】, 中间用事务边界隔开.

## 53.4  7 步业务流程逐一解析

### 第 1 步: 参数校验
```java
if (dto.getCartItemIds() == null || dto.getCartItemIds().isEmpty()) {
    throw new BusinessException(400, "请选择要购买的商品");
}
if (dto.getAddressId() == null) {
    throw new BusinessException(400, "请选择收货地址");
}
```
- 早抛早返, 后面才有意义
- BusinessException 由 GlobalExceptionHandler 接, 返 400 给前端

### 第 2 步: 地址校验 (Feign 调 user 服务)
```java
Result<Map<String, Object>> addrResp = userFeignClient.getAddress(dto.getAddressId(), userId);
if (addrResp == null || addrResp.getCode() != 200 || addrResp.getData() == null) {
    throw new BusinessException(403, "收货地址无效");
}
Map<String, Object> address = addrResp.getData();
```
- ⭐ 显式传 userId 给 Feign, 让 user 服务做越权校验 ("这个地址是不是你的")
- ⭐ 返 `Map<String, Object>` 因为 order 服务引不到 user 的 Address entity
- Feign 失败的兜底: 返 null 或 code!=200, 当无效地址处理

### 第 3 步: 购物车校验 (同服务)
```java
List<CartItem> cartItems = cartItemService.listByIds(dto.getCartItemIds());
if (cartItems.size() != dto.getCartItemIds().size()) {
    throw new BusinessException(400, "购物车项不存在");
}
for (CartItem ci : cartItems) {
    if (!ci.getUserId().equals(userId)) {
        throw new BusinessException(403, "无权操作他人购物车");
    }
}
```
- cart 在 order 服务里 (G3.4 搬过来的), 同服务直接 @Autowired ICartItemService
- 校验 1: 传进来的 IDs 都存在 (size 对得上)
- 校验 2: 每一项都是当前用户的 (防越权)

### 第 4 步: 批量查商品 (Feign × N)
```java
Map<Long, Map<String, Object>> productMap = new HashMap<>();
for (CartItem ci : cartItems) {
    Result<Map<String, Object>> pResp = productFeignClient.getById(ci.getProductId());
    if (pResp == null || pResp.getCode() != 200 || pResp.getData() == null) {
        throw new BusinessException(400, "商品不存在: id=" + ci.getProductId());
    }
    productMap.put(ci.getProductId(), pResp.getData());
}
```
- ⚠ 性能差 - **N 次 HTTP** (每个商品一次 Feign)
- 生产推荐做法: 给 product 服务加 `POST /product/batch` 接口, 一次拿全
- 教学场景接受, 简单直接

### 第 5 步: 算总价 + 构造 OrderItem (含商品快照)
```java
BigDecimal totalAmount = BigDecimal.ZERO;
List<OrderItem> orderItems = new ArrayList<>();
for (CartItem ci : cartItems) {
    Map<String, Object> p = productMap.get(ci.getProductId());

    // 商品状态校验 (status=0 已下架)
    if (Integer.parseInt(p.get("status").toString()) == 0) {
        throw new BusinessException(400, "商品已下架: " + p.get("name"));
    }

    BigDecimal price = new BigDecimal(p.get("price").toString());
    BigDecimal subtotal = price.multiply(BigDecimal.valueOf(ci.getQuantity()));
    totalAmount = totalAmount.add(subtotal);

    OrderItem oi = new OrderItem();
    oi.setProductId(ci.getProductId());
    oi.setProductName((String) p.get("name"));        // ⭐ 快照
    oi.setProductImage((String) p.get("coverImage"));  // ⭐ 快照
    oi.setPrice(price);                                 // ⭐ 快照
    oi.setQuantity(ci.getQuantity());
    oi.setSubtotal(subtotal);
    orderItems.add(oi);
}
```

**快照的意义**:
- 用户下单时商品 A 卖 100 块
- 第二天店家改成 80 块
- 用户看历史订单, 应该看到的是【下单时】的 100 块, 不是【现在】的 80 块
- → 所以 `productName / image / price` 都【拷】到 order_item 表里, 不再去 product 表查

**BigDecimal 注意点 (G3.4 学过)**:
- `new BigDecimal(p.get("price").toString())` 不要 `cast Double` (精度问题)
- 乘法用 `.multiply(...)`, 不能用 `*`

### 第 6 步: 生成订单号 + 保存
```java
String orderNo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        + userId + String.format("%04d", new Random().nextInt(10000));

Orders order = new Orders();
order.setOrderNo(orderNo);
order.setUserId(userId);
order.setTotalAmount(totalAmount);
order.setStatus(OrderStatus.UNPAID);
order.setReceiver((String) address.get("receiver"));
order.setPhone((String) address.get("phone"));
order.setAddress("" + address.get("province") + address.get("city")
        + address.get("district") + address.get("detail"));
order.setRemark(dto.getRemark());
this.save(order);   // ⭐ MP 自动回填 id

for (OrderItem oi : orderItems) {
    oi.setOrderId(order.getId());   // 此时 order.id 已被 MP 回填
}
orderItemService.saveBatch(orderItems);
```

**订单号格式**: `yyyyMMddHHmmss + userId + 4位随机` = `2026062011055118690`
- 时间戳 (14 位) → 防同一秒撞号
- userId → 同一秒不同用户不会撞
- 4 位随机 → 防同一秒同一用户撞号 (基本不会发生)

**地址快照**: 拼一个长字符串塞 `address` 字段, 后续地址改了也不影响.

### 第 7 步: 清购物车
```java
cartItemService.removeByIds(dto.getCartItemIds());
```
- ⚠ 单体踩坑 (G3.4 修过): 这里调 `removeByIds` 在 @TableLogic 模式下是逻辑删, 不是物理删
- G3.7 又踩了一次坑 (踩坑 ②): yml 全局配置反扑

### 第 ⑧ 步 (单体有, 我们【不做】): 扣库存
```java
// 单体:
for (CartItem ci : cartItems) {
    int rows = productMapper.deductStock(ci.getProductId(), ci.getQuantity());
    if (rows == 0) throw new BusinessException(400, "库存不足");
}
```

**为啥微服务做不了**:
- order 服务的事务只能管自己的 DB
- 扣库存要改 product DB, 不在同一个事务里
- 强一致需要分布式事务 (TCC / Saga / Seata), 复杂度爆炸

**G3.7 决策**: 跳过扣库存. 教学聚焦 MQ 真业务通路, 跨服务事务留到 G4 单独学.

**生产做法**:
- 方案 1: 付款时扣库存 (本服务的 SQL UPDATE), 简单
- 方案 2: 引入 Seata AT 模式做分布式事务
- 方案 3: 本地消息表 + 异步对账 (最终一致)

---

# Chapter 54 · 真业务 MQ 通路 (G2 延伸到这里才闭环)

## 54.1  从 demo 走到真业务

```
G2 (demo 版):                          G3.7 (真业务版):
  MqTestController                       OrdersServiceImpl.createOrder()
   POST /mq/test/delay/{orderId}    →   rabbitTemplate.convertAndSend
   rabbitTemplate.convertAndSend         (delay 队列)
                                              │
   ┌─ delay.queue (TTL=30s) ──┐                ▼
   │                          │     ┌─ delay.queue (TTL=30s) ──┐
   └────────┬─────────────────┘     │   真业务消息: orderId=10  │
            │                       └────────┬─────────────────┘
            ▼                                │
   close.queue                               ▼
            │                       close.queue
            ▼                                │
   MqTestListener.onCloseMessage              ▼
   (空方法, 只打日志)                  OrderCloseListener.onOrderClose
                                      ordersService.closeOrderByMQ(10)
                                      UPDATE orders SET status=4 WHERE id=10 AND status=0
                                      → 真改了 DB!
```

**G3.7.5 切换的瞬间**:
- 写 OrderCloseListener (新)
- 改 MqTestListener: 删掉 `onCloseMessage` 方法 (避免双 listener 抢)
- 删 MqTestController (G3.7.9 最后做)

## 54.2  closeOrderByMQ 的幂等 (核心代码 + 解析)

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void closeOrderByMQ(Long orderId) {
    Orders order = ordersMapper.selectById(orderId);
    if (order == null) {
        return;   // 订单不存在, 跳过
    }

    if (!order.getStatus().equals(OrderStatus.UNPAID)) {
        return;   // ⭐ 幂等核心: 只有"待付款"才关
    }

    order.setStatus(OrderStatus.CANCELLED);
    ordersMapper.updateById(order);
}
```

**幂等三态保护**:

| 进来时 status | 进来时含义 | 怎么处理 | 为啥 |
|---|---|---|---|
| 0 UNPAID | 还没付款 | 改成 4 (关单) | 这就是正常路径 |
| 1 PAID | 已付款 | return 跳过 | 用户已付钱, 不能关 |
| 2 SHIPPED | 已发货 | return 跳过 | 同上 |
| 3 COMPLETED | 已完成 | return 跳过 | 同上 |
| 4 CANCELLED | 已取消 (用户或前次 MQ 关的) | return 跳过 | 防消息重复投递 |

**G3.7.8 实测的 3 个证据**:

| 订单 | 进 MQ 时 status | 出 MQ 后 status | update_time | 证明 |
|---|---|---|---|---|
| 10 | 0 | 4 | 11:06:22 (MQ 到达时刻) | 正常关单 |
| 11 | 1 (已付款) | 1 不变 | 11:10:17 (付款时刻) | 幂等跳过 |
| 12 | 4 (已取消) | 4 不变 | 11:10:30 (取消时刻) | 幂等跳过 |

`update_time` 是 MySQL 自动维护的 `ON UPDATE CURRENT_TIMESTAMP`. 11 和 12 的 `update_time` 是付款/取消时刻而不是 MQ 时刻, **证明 MQ 进来时没有跑 UPDATE SQL** = 幂等保护生效.

## 54.3  Listener 完整代码 + 解析

```java
@Component   // 必须 @Component, Spring 才能扫描 @RabbitListener
public class OrderCloseListener {

    @Autowired
    private IOrdersService ordersService;

    @RabbitListener(queues = RabbitMQConfig.CLOSE_QUEUE)
    public void onOrderClose(Long orderId, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            ordersService.closeOrderByMQ(orderId);     // 调真业务
            channel.basicAck(deliveryTag, false);       // ACK
        } catch (Exception e) {
            channel.basicNack(deliveryTag, false, false);  // NACK 不 requeue
        }
    }
}
```

**对比 G2 的 MqTestListener**:
- 一样: 方法签名 (3 参数自动注入) + ACK/NACK 套路
- 升级: 加了 `ordersService.closeOrderByMQ(orderId)` 这一行调真业务

---

# Chapter 55 · G3.7 代码全集

## 55.1  17 个新文件 (按职责分类)

| 分类 | 文件 | 大小 | 关键点 |
|---|---|---|---|
| Entity | Orders.java | 65 行 | 加 @TableName("orders") 防 MySQL 保留字 |
| Entity | OrderItem.java | 50 行 | 无 @TableLogic (表里没该列) |
| Mapper | OrdersMapper / OrderItemMapper | 10 行 ×2 | 纯 BaseMapper, 无 XML |
| Client | UserFeignClient.java | 35 行 | ⭐ 显式 @RequestHeader("X-User-Id") |
| Constant | OrderStatus.java | 15 行 | 5 个 byte 常量 |
| Util | RedisLockUtil.java | 60 行 | SET NX + Lua DEL |
| DTO | CreateOrderDTO.java | 12 行 | addressId + cartItemIds + remark |
| VO | OrderItemVO / OrderListVO / OrderDetailVO | 12+15+20 | 3 个层级的视图 |
| Service | IOrdersService.java | 25 行 | 6 方法签名都带 userId |
| Service | IOrderItemService.java | 5 行 | 空接口 |
| ServiceImpl | OrdersServiceImpl.java | **300 行** | ⭐ 项目最大方法 createOrder |
| ServiceImpl | OrderItemServiceImpl.java | 5 行 | 空类 |
| Listener | OrderCloseListener.java | 35 行 | 替代 MqTestListener.onCloseMessage |
| Controller | OrdersController.java | 60 行 | 5 端点, 全部 @RequestHeader |

## 55.2  跨服务调用矩阵

| 谁调谁 | 干啥 | Feign 接口 | 返回类型 |
|---|---|---|---|
| order → user | 查地址 | UserFeignClient.getAddress(id, userId) | `Result<Map<String, Object>>` |
| order → product | 查商品 | ProductFeignClient.getById(id) | `Result<Map<String, Object>>` |
| MQ → OrderCloseListener | 关单触发 | (RabbitMQ 投递) | (Long orderId) |

## 55.3  状态机迁移 (5 态 + 3 路径)

```
                    ┌────────────────┐
                    │  UNPAID (0)    │
                    │  待付款         │
                    └──┬─────┬───┬───┘
                       │     │   │
              POST     │     │   │ PUT /order/{id}/cancel
              /pay     │     │   │       (user 主动)
                       │     │   │
                       ▼     │   ▼
              ┌─────────┐    │   ┌──────────────┐
              │  PAID (1)│   │   │ CANCELLED (4)│◀── 30s TTL MQ
              │  已付款  │   │   │  已取消       │     (delay queue
              └─┬───────┘   │   └──────────────┘     timeout)
                │           │
          ship  │           │ 自动 (未来阶段)
                ▼           │
        ┌────────────┐      │
        │ SHIPPED (2)│      │
        │  已发货     │      │
        └─┬──────────┘      │
          │                 │
          done              │
          ▼                 │
        ┌──────────────┐    │
        │ COMPLETED (3)│    │
        │  已完成       │    │
        └──────────────┘    │
```

G3.7 实现的 3 条路径:
- ① UNPAID → PAID (用户付款 payOrder)
- ② UNPAID → CANCELLED (用户取消 cancelOrder)
- ③ UNPAID → CANCELLED (30 秒 TTL → MQ closeOrderByMQ)

未做: ship / done 没接入 (没物流/客服业务), CANCELLED 不还库存 (因为下单也没扣库存).

---

# Chapter 56 · G3.7 踩坑实录 + 累计能力 + 待办

## 56.1  踩坑 ① PowerShell + curl + 中文 → UTF-8 解析炸

**现象**:
```
POST /order body 含中文 remark "G3.7 端到端测试"
→ 500 系统繁忙
→ order.log: HttpMessageNotReadableException
              Invalid UTF-8 start byte 0xb6
```

**根因**: PowerShell 默认用 GBK 把中文编码进 HTTP body, Spring 用 UTF-8 解析, 0xb6 不是有效的 UTF-8 起始字节.

**修法 (绕过)**: remark 改英文 `"G3.7 e2e test"`.

**正经修法 (没做)**:
- PowerShell 切 UTF-8: `chcp 65001 && [Console]::OutputEncoding = [System.Text.Encoding]::UTF8`
- 或用 `curl --data-binary @file.json`, 中文写到文件里, 文件存 UTF-8

## 56.2  踩坑 ② yml 全局 logic-delete-field 反扑 cart bug

**现象**:
- G3.4 修了 CartItem 删 @TableLogic 让它物理删除
- G3.7 重复加购同一商品时, 又出现 `Duplicate entry '1-1' for key 'cart_item.uk_user_product'`

**根因**: application.yml 里有【全局】配置:
```yaml
mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: isDeleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```
这让所有 entity (包括没标 @TableLogic 的 CartItem) 都按 isDeleted 字段做逻辑删除!

**修法**: 注释掉 yml 里这一段, 让 @TableLogic **注解** 单独说了算:
- Orders 标了 @TableLogic → 仍逻辑删 ✓
- CartItem 没标 @TableLogic → 物理删 ✓

**教训**: 【全局配置】和【注解配置】有时会打架. 注解优先, 但全局配置会"补刀". 删全局配置最干净.

## 56.3  G3.7 累计能力清单

| 能力 | 怎么用 |
|---|---|
| ⭐ MQ 真业务通路 | createOrder 发延迟 + OrderCloseListener 收 + 改 DB |
| ⭐ 分布式锁防重复提交 | RedisLockUtil + try/finally + 锁外事务 |
| ⭐ 事务边界精控 | TransactionTemplate.execute lambda + 事务外发 MQ |
| ⭐ 跨服务 Feign 调 user/product | UserFeignClient + ProductFeignClient |
| ⭐ 完整订单状态机 | 5 态 + 3 迁移 + 幂等保护 |
| 商品/地址快照 | 下单瞬间冻结, 后续变化不影响历史 |

## 56.4  G3.7 没做但已铺路的 (后续)

| 待办 | 何时做 |
|---|---|
| 扣库存 (跨服务事务) | G4 引入 Seata / TCC / Saga |
| 给 product 加 batch 接口 (替代 N 次 Feign) | 性能优化时 |
| 全局 Feign RequestInterceptor (统一转发 X-User-Id) | 出现第 3 个 Feign Client 时 |
| OrderTimeoutTask 兜底定时任务 | MQ 万一漏掉时的保险, 真生产推荐加 |
| 支付集成 (微信/支付宝) | 真业务才做, 教学跳过 |

## 56.5  G3.7 决策沉淀

| 决策 | 选了啥 | 为啥 |
|---|---|---|
| 扣库存 | **跳过** | 跨服务事务复杂, 教学聚焦 MQ 通路 |
| 商品/地址数据格式 | `Map<String, Object>` | order 引不到 Product/Address entity |
| 性能 (查商品) | 循环 N 次 Feign | 简单直接, 教学场景接受 |
| 锁的范围 | 大于事务范围 (包在事务外) | 防锁释放后事务未提交导致重复 |
| MQ 发送时机 | 事务【提交后】 | 防幽灵消息 (事务回滚但 MQ 已发) |
| 用户上下文 | @RequestHeader X-User-Id + Service 显式参数 | 不用 ThreadLocal, 支持异步/MQ 消费者 |
| 订单号格式 | `yyyyMMddHHmmss + userId + 4 位随机` | 防撞号 + 可读性 |
| 幂等保护 | service 层 `if (status != 0) return` | 简单可靠, 不依赖外部去重 |

## 56.6  G3.7 搬迁经验沉淀

| 经验 | 来源 |
|---|---|
| 微服务复杂业务 = 锁 + 事务 + Feign + MQ 四件套, 顺序不能乱 | createOrder |
| 锁范围大于事务范围, 避免锁释放后事务未提交 | 53.2 |
| MQ 在事务提交后发, 避免幽灵消息 | 53.3 |
| MQ 消费者必须幂等, "状态机 + 早返回"是最简幂等 | closeOrderByMQ |
| 跨服务调用返 `Map<String, Object>`, 避免引对方 Entity | UserFeignClient |
| Feign 需要 header 时显式声明 @RequestHeader 参数 | UserFeignClient.getAddress |
| 真业务 Listener 替代 demo Listener 时, 删旧的避免双消费 | G3.7.5 |
| 全局 MP 配置会反扑注解配置, 注解+全局别共存 | 踩坑 ② |
| Windows PowerShell + curl 中文 = UTF-8 炸 | 踩坑 ① |

---

**G3.7 完毕**. 微服务从"业务覆盖大半" 走到 "**真业务 MQ 通路 + 状态机 + 跨服务 + 锁 + 事务全栈接通**". 这是 G 阶段最复杂的一块. 下一步 G3.8 Seckill (Lua 原子扣库存 + MQ 异步下单) 或者收尾 commit.

---

# Chapter 57 · G3.8 Seckill 搬迁 - 总述 + 架构图

> 这一章是【Lua 原子操作】+【MQ 异步下单】+【前端轮询】的实战.
> 学完能回答: "为啥秒杀要 Lua? 抢到不直接写库为啥还要 MQ? 轮询怎么知道生成了没?"

## 57.1  G3.8 是啥, 它解决的问题

**目标**: 高并发抢购场景的标准技术方案.

**痛点**: 10000 人抢 3 件商品, 怎么保证:
- ① 不超卖 (多余的人必须抢不到)
- ② 不让 DB 挂掉 (10000 个并发请求直接打 DB → 数据库爆炸)
- ③ 用户体验快 (不让用户等几秒)
- ④ 一人一单 (同一用户不能抢多次)

**解决方案 (本章的核心架构)**:
- ① 防超卖 → **Lua 原子脚本** (Redis 单线程 + 5 行串成一次操作)
- ② 不打挂 DB → **MQ 异步**: Lua 抢到的人先进 MQ 队列, 慢慢消费写 DB
- ③ 体验快 → 用户 100ms 就知道"抢到了" (Lua 返回快), 然后前端轮询查结果
- ④ 一人一单 → Lua 里 `SISMEMBER` 检查 bought 集合

## 57.2  端到端架构图

```
   ┌─────────────────────────────────────────────────────────────┐
   │           高并发请求 (10000 个用户同时抢)                       │
   └────────────────────────────┬────────────────────────────────┘
                                │
                                │ POST /seckill/{activityId}
                                │ X-User-Id: N
                                ▼
   ┌─ 网关 (9080) ─┐
   └──────┬────────┘
          │ lb://mini-mall-order
          ▼
   ┌─────────  mini-mall-order (9003) ─────────┐
   │                                            │
   │  SeckillController.seckill()              │
   │           │                                │
   │           ▼                                │
   │  SeckillActivityServiceImpl.seckill()     │
   │     │                                      │
   │     ├─ ① 校验活动时间窗口                  │
   │     ├─ ② Redis 库存懒加载 (首次访问灌进)    │
   │     │                                      │
   │     ├─ ③ ⭐ 调 Lua 原子脚本 ──────────▶ Redis (5672)
   │     │     KEYS=[stock,bought], ARGV=[uid]   │   单线程跑 5 行:
   │     │                                      │   EXISTS + SISMEMBER
   │     │                                      │   + GET + DECR + SADD
   │     │     返回:                             │
   │     │       1=抢到 / 0=售罄                 │
   │     │       -1=已参与 / -2=未预热           │
   │     │                                      │
   │     └─ ④ 抢到 → 发 MQ "actId:uid" ────▶ RabbitMQ
   │              return "抢购成功"              │   seckill.exchange
   │                                            │   → seckill.queue
   │  ↓ HTTP 响应立即返回 (100ms 级)            │           │
   │                                            │           │
   └────────────────────────────────────────────┘           │
                                                            ▼
                                ┌─── 异步 (跟用户无关) ─────┐
                                │                            │
                                │   SeckillOrderListener    │
                                │     ① 解析 "actId:uid"     │
                                │     ② 幂等查 DB           │
                                │     ③ 回查 activity        │
                                │     ④ INSERT seckill_order │
                                │     ⑤ ACK                 │
                                │                            │
                                └────────────┬───────────────┘
                                             │
                                             ▼
                                       MySQL seckill_order

   ┌── 前端轮询 ───────────────────────────────────────────────┐
   │  GET /seckill/result/{id}    每 1 秒一次                  │
   │  ├─ 查 DB: 有订单? → SUCCESS + orderNo                    │
   │  ├─ 查 Redis bought: 在? → PROCESSING                     │
   │  └─ 都没有 → NOT_FOUND                                    │
   └───────────────────────────────────────────────────────────┘
```

## 57.3  vs G3.7 Orders 复杂度对比

| 维度 | G3.7 Orders | G3.8 Seckill |
|---|---|---|
| 文件数 | 17 | 11 (含 Lua 资源) |
| 跨服务调用 | 2 (Feign user + product) | 1 (Feign product) |
| 数据库事务 | TransactionTemplate 精控 | 各方法独立, 无大事务 |
| 锁 | Redis 分布式锁 (键级别) | **Lua 脚本** (Redis 单线程原子) |
| MQ | 30 秒延迟 + DLX 关单 | 立即送达 + 幂等下单 |
| 状态机 | 5 态 + 3 迁移 + 幂等 | 简单 (订单 status=0 即可) |
| 高并发支持 | 中 (锁串行化) | ⭐⭐⭐ 强 (Lua 单线程, 5W QPS) |

---

# Chapter 58 · Lua 脚本深度解析 (G3.8 最炫的 5 行)

> 这一章把 `seckill_stock.lua` 5 行【逐行讲透】.
> 学完能回答: "为啥不能用 Java 代码做这件事? Lua 多原子? EVALSHA 是啥?"

## 58.1  为啥要 Lua? 朴素 Java 做不到啥?

**朴素 Java 方案 (错的)**:
```java
public boolean trySeckill(Long activityId, Long userId) {
    // ① 检查用户已购
    if (redisTemplate.opsForSet().isMember("bought:" + activityId, userId)) {
        return false;  // 已抢过
    }
    // ② 检查库存
    Integer stock = Integer.parseInt(redisTemplate.opsForValue().get("stock:" + activityId));
    if (stock <= 0) {
        return false;  // 售罄
    }
    // ③ 扣库存
    redisTemplate.opsForValue().decrement("stock:" + activityId);
    // ④ 加入已购
    redisTemplate.opsForSet().add("bought:" + activityId, userId.toString());
    return true;
}
```

**问题: 4 个 Redis 操作之间会被打断** (并发请求穿插):
```
线程 A: ② 看到 stock=1
线程 B: ② 看到 stock=1  ← 同时!
线程 A: ③ DECR → stock=0
线程 B: ③ DECR → stock=-1  ← 超卖!
```

→ 哪怕你加 Java 的 `synchronized`, 它只锁本 JVM 的线程, 多实例时 A 服务器和 B 服务器同时看 → 同样超卖.

→ 加 Redis 分布式锁? 性能差 (每个请求都要先 SET NX 抢锁, 再做 4 个操作, 再 DEL).

**Lua 方案的本质**: Redis 是【单线程的】, Lua 脚本执行期间**不接其他客户端命令**.
- 5 个 Redis 操作打包 → 像【一个原子操作】
- 没有"穿插"的可能性
- 性能比"分布式锁 + 多步"高 N 倍

## 58.2  5 行 Lua 逐行讲

```lua
-- ① 检查活动是否预热 (库存 key 不存在 = 活动还没开始)
if redis.call('EXISTS', KEYS[1]) == 0 then
    return -2
end
```
- `redis.call` 是 Lua 调 Redis 命令的方式
- `EXISTS` 返回 0 / 1
- 库存 key 不在 = 活动 service 还没把库存灌进 Redis (懒加载未触发) → -2

```lua
-- ② 检查用户是否已抢过 (Set 里有 userId 就是抢过了)
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -1
end
```
- `SISMEMBER` 检查元素是否在 Set 里, 返回 0 / 1
- KEYS[2] 是 `seckill:bought:{actId}` (一个 Set)
- ARGV[1] 是当前 userId
- 在里面 → 已抢过 → -1

```lua
-- ③ 检查库存 (Redis GET 返字符串, 必须 tonumber)
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock <= 0 then
    return 0
end
```
- ⚠ **Redis GET 返字符串, Lua 不会自动转数字**, 必须 `tonumber()`
- `local stock` 是 Lua 局部变量声明
- 库存 ≤ 0 → 已售罄 → 0

```lua
-- ④ 原子扣库存 + 记录已购
redis.call('DECR', KEYS[1])         -- 库存 - 1
redis.call('SADD', KEYS[2], ARGV[1]) -- 把 userId 加进已购集合

return 1
```
- `DECR` 原子减 1 (Redis 单条命令也是原子的, 但和 GET 之间无法保证)
- `SADD` 加进 Set (Set 内自动去重)
- 抢到 → 1

## 58.3  KEYS 和 ARGV 的设计为啥分开?

Redis 协议规定: Lua 脚本传参分 KEYS 和 ARGV.
- KEYS = 所有【会操作的 key】, Redis Cluster 模式下要用它来检查"是不是都在同一个 slot"
- ARGV = 普通参数 (不是 key)

我们这里:
- KEYS[1] = stock key, KEYS[2] = bought key
- ARGV[1] = userId

Cluster 部署时, 这俩 key 要保证在同一节点, 我们给 key 加 `{activityId}` hashtag:
```
seckill:stock:{1}    ← 都用 {1}, 强制路由到同一节点
seckill:bought:{1}
```
单机 Redis (我们的情况) 不用管.

## 58.4  返回值约定 (面试高频)

| Lua 返回 | 含义 | service 层处理 |
|---|---|---|
| **1** | 抢到了 | 发 MQ + 返"抢购成功" |
| **0** | 已售罄 | 抛 400 已售罄 |
| **-1** | 已参与过 | 抛 400 您已参与 |
| **-2** | 未预热 | 抛 400 活动未开始 |

为啥用 `-1 / -2` 表示错误而不是 throw? Lua 错误传递成本高, 数字返回最简单.

## 58.5  Spring 怎么注册 Lua 脚本 (EVALSHA 优化)

```java
@Bean
public DefaultRedisScript<Long> seckillStockScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptSource(
            new ResourceScriptSource(new ClassPathResource("lua/seckill_stock.lua"))
    );
    script.setResultType(Long.class);
    return script;
}
```

**Spring 帮你做的优化**:
- ① 启动时读 lua 文件内容
- ② 算它的 SHA1 hash
- ③ 第一次调用时 Redis 已经知道这个 hash 对应啥脚本
- ④ 后续调用走 `EVALSHA` (只传 hash) 而不是 `EVAL` (传整段脚本) → 网络省 200+ 字节

每个 Spring Boot 服务进程只编译一次, 后续都是 EVALSHA. 别担心性能.

---

# Chapter 59 · MQ 异步下单 + 轮询 3 态查询

## 59.1  为啥 Lua 抢到了不直接写 DB?

**朴素方案**: Lua 返回 1 → service 直接 INSERT seckill_order.

**问题**: 假设 stock=10000, 全部抢光:
- 10000 个并发 INSERT 同时打 DB
- DB CPU 飙升, 锁表
- 接口响应时间从 100ms 涨到 5 秒
- 用户体验差, 数据库可能挂

**MQ 方案 (本章)**: Lua 抢到 → 发 MQ → 立即返回响应 → 慢慢消费 INSERT.
- Lua 抢到 100ms 就返回, 用户感觉快
- MQ 削峰: 10000 条消息排队, Listener 每秒消费 100 条 → DB 压力 10K/s 变成 100/s
- 用户**异步轮询**等结果

## 59.2  消息体设计 "activityId:userId"

```java
String msg = activityId + ":" + userId;
rabbitTemplate.convertAndSend(SECKILL_EXCHANGE, SECKILL_ROUTING_KEY, msg);
```

**为啥用字符串拼接而不是 POJO?**
- 简单, Spring AMQP 默认 SimpleMessageConverter 直接发 String
- 跨语言友好 (即使消费者是 Python, 也能 split(':'))
- 消息体小 (POJO 序列化大概率多花 50+ 字节)

**消费端解析**:
```java
String[] parts = msg.split(":");
Long activityId = Long.valueOf(parts[0]);
Long userId     = Long.valueOf(parts[1]);
```

**生产推荐**: 业务复杂时切 JSON + Jackson2JsonMessageConverter, 发 POJO. 这里教学场景简单消息够用.

## 59.3  幂等设计: SeckillOrderListener 的核心

```java
// 第 2 步: 幂等检查
QueryWrapper<SeckillOrder> w = new QueryWrapper<>();
w.eq("user_id", userId).eq("seckill_activity_id", activityId);
Long count = seckillOrderMapper.selectCount(w);
if (count > 0) {
    log.warn("[MQ-Seckill] 重复消息跳过 userId={} activityId={}", userId, activityId);
    channel.basicAck(deliveryTag, false);
    return;
}
```

**幂等的 3 道防线**:
- ① Lua 层 SISMEMBER: 同一 user 同一活动只能 return 1 一次 → 一般不会发重复消息
- ② 这里 DB count: 即使万一发了 2 次 (Lua 客户端重试、MQ 重新投递等), 这里也跳过
- ③ DB 主键 + (user_id, activity_id) 唯一索引: 终极兜底 (本项目 schema 没加, 教学省略)

## 59.4  3 态轮询查询 (前端用户体验关键)

```java
public Map<String, Object> querySeckillResult(Long userId, Long activityId) {
    // 态 1: DB 有订单 → SUCCESS + orderNo
    SeckillOrder order = seckillOrderMapper.selectOne(
        new QueryWrapper<SeckillOrder>().eq("user_id", userId).eq("seckill_activity_id", activityId)
    );
    if (order != null) return SUCCESS;

    // 态 2: DB 没有 + Redis bought 集合里有 → PROCESSING
    if (Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember("seckill:bought:" + activityId, userId.toString()))) {
        return PROCESSING;
    }

    // 态 3: 都没 → NOT_FOUND
    return NOT_FOUND;
}
```

**3 态的物理含义**:

| 态 | 说明 | 前端展示 |
|---|---|---|
| **SUCCESS** | DB 已落库, MQ 已消费完 | "下单成功, orderNo=..., 请支付" |
| **PROCESSING** | Lua 已抢到 (Redis bought 有), 但 MQ 还没消费 (DB 没数据) | "下单中, 请稍后, 加载动画" |
| **NOT_FOUND** | Lua 都没抢到 | "未抢到, 下次再来" |

**为啥这 3 态? — 时间窗口的物理表达**:
```
T+0ms     用户按 "抢" 按钮 → Lua 抢到 → SADD bought (此时态变 PROCESSING)
T+100ms   HTTP 响应返回 "抢购成功"
T+100ms~  前端开始轮询 GET /seckill/result/{id}, 每秒 1 次
T+500ms   MQ Listener 消费, INSERT 完成 (此时态变 SUCCESS)
T+1000ms  前端下次轮询 → 看到 SUCCESS → 跳支付页
```

---

# Chapter 60 · G3.8 代码全集

## 60.1  11 个新文件 + 1 Lua 资源 (按职责分类)

| 分类 | 文件 | 大小 | 关键点 |
|---|---|---|---|
| Entity | SeckillActivity | 50 行 | productId 跨服务用 Feign |
| Entity | SeckillOrder | 55 行 | seckill_activity_id 反查活动, isDeleted=Integer |
| Mapper | SeckillActivityMapper / SeckillOrderMapper | 8 行 ×2 | 纯 BaseMapper |
| DTO | SeckillActivityDTO | 18 行 | 不含 id / status / createTime |
| VO | SeckillActivityVO | 22 行 | 含商品快照 + statusDesc |
| Config | LuaScriptConfig | 30 行 | DefaultRedisScript<Long> Bean |
| **Lua** | **lua/seckill_stock.lua** | **35 行** | **5 行原子核心** |
| Service | ISeckillActivityService | 25 行 | 4 方法签名 |
| ServiceImpl | SeckillActivityServiceImpl | **230 行** | ⭐ seckill 50 行核心 |
| Listener | SeckillOrderListener | 90 行 | 5 步消费 + 幂等 |
| Controller | SeckillController | 50 行 | 4 端点 |
| Gateway | application.yml | +5 行 | 加 seckill-route |

## 60.2  Lua + Java 调用 (核心 5 行代码)

```java
// Java 调 Lua
Long result = stringRedisTemplate.execute(
        seckillStockScript,                  // 之前 @Bean 注入的脚本对象
        Arrays.asList(stockKey, boughtKey),  // KEYS list, Lua 里 KEYS[1] KEYS[2]
        userId.toString()                    // ARGV[1], 可变参 (可传多个)
);
```

**注意**:
- 必须 `StringRedisTemplate` 不能用普通 `RedisTemplate` (Lua 只认 String 序列化)
- ARGV 都要先 `.toString()` (Long 不能直接传)
- KEYS 是 List, ARGV 是可变 Object...

## 60.3  跨服务调用矩阵 (vs G3.7 比)

| 谁调谁 | 干啥 | G3.7 | G3.8 |
|---|---|---|---|
| order → product | 查商品 | 用了 (createOrder 算总价) | 用了 (publishActivity 校验 + listActivities 拿原价) |
| order → user | 查地址 | 用了 | **没用** (秒杀订单不要地址) |
| order → Redis Lua | 原子操作 | 没用 | **核心** |
| order → MQ | 异步事件 | 延迟关单 (TTL+DLX) | 即时下单 (普通 queue) |

## 60.4  端到端实测时间线 (G3.8.9 真实数据)

```
T+0      POST /seckill/3, alice          → 200 OK "抢购成功, 订单生成中"
         Lua return 1 → SADD bought:3 [1] → DECR stock:3 = 2
         rabbitTemplate 发 MQ "3:1"

T+~10ms  SeckillOrderListener.onSeckillMessage("3:1")
         解析 → 幂等查 (无重复) → 回查 activity → INSERT seckill_order
         id=17 orderNo=2026062011283819343

T+3s     curl GET /seckill/result/3       → SUCCESS + orderNo (因为 DB 已有)

T+30s    POST /seckill/3, alice (重复)     → 400 您已参与过此次秒杀
         Lua: SISMEMBER bought:3 alice (1) → return -1
```

**注意 ⭐**: T+0 到 SUCCESS 全程 < 100ms (MQ 消费快), 用户感觉"秒到".
真生产高并发场景, MQ Listener 慢一点, 用户会看到 PROCESSING 中间态.

---

# Chapter 61 · G3.8 决策 + 累计能力 + 待办

## 61.1  G3.8 关键决策

| 决策 | 选了啥 | 为啥 |
|---|---|---|
| 文件归属 | **全放 order 服务** | SeckillOrder 写 order DB; SeckillActivity 跟它耦合; 简化 |
| 防超卖 | **Lua 原子脚本** | Redis 单线程, 5 行打包, 性能强 |
| 抢到后 | **MQ 异步写 DB** | 削峰填谷, 不让 DB 直接挨 10K QPS |
| 消息体 | **String "actId:userId"** | 简单, 跨语言, 体积小 |
| 幂等 | DB count + Lua SISMEMBER 双保险 | 即使 MQ 重投也只生成 1 单 |
| 用户体验 | 立即返回 + **前端轮询 3 态** | 100ms 看到响应, 1 秒拿到结果 |
| 库存灌入 Redis | **懒加载** (第一次 seckill 调用时灌) | 简单, 缺点是首次 race 风险 |
| Lua 返回值 | **数字 1/0/-1/-2** | 不抛异常, 简单清晰 |
| 路由 | 网关加 `/seckill/**` | 同 G3.7 套路 |

## 61.2  G3.8 累计能力

| 能力 | 怎么用 |
|---|---|
| ⭐ Lua 原子操作 | 高并发抢购 / 限流 / 计数等场景的标准方案 |
| ⭐ MQ 削峰填谷 | 突发流量场景 (秒杀/抢券/活动通知) |
| ⭐ 异步下单 + 轮询 | 任何"长操作 + 用户等待"的场景 (大文件上传, AI 任务等) |
| Redis 库存懒加载 | 把 DB 库存映射到 Redis 时用 |
| @Bean 化 Lua 脚本 | EVALSHA 优化, 启动时一次性加载 |
| 跨服务调用 (Feign 调 product) | 跟 G3.7 一致 |

---

# Chapter 77 · SEC 阶段 · common-security 抽取

> 📎 **完整源码+逐行解释见同目录 docx**:[`Chapter77_common-security_抽取.docx`](./Chapter77_common-security_抽取.docx)

## 77.1  这次做了什么

把 JWT / 拦截器 / Feign 透传抽成共享 jar `mini-mall-common-security`,业务代码 0 配置接入。
业务代码再也不出现 `@RequestHeader("X-User-Id")`, 也不出现 "Feign 调用要传 userId" 这种 HTTP/RPC 细节。

**改造前 ❌:**
```java
@PostMapping("/order/create")
public Result<Long> createOrder(
        @RequestHeader("X-User-Id") Long userId,   // 业务侵入了 HTTP 细节
        @RequestBody OrderCreateDTO dto) { ... }

// Feign 接口
@GetMapping("/user/address/{id}")
Result<...> getAddress(@PathVariable Long id,
                       @RequestHeader("X-User-Id") Long userId);   // 形参绑死

// 调用方
userFeignClient.getAddress(addressId, userId);   // 多传一个参数
```

**改造后 ✅:**
```java
@PostMapping("/order/create")
public Result<Long> createOrder(@RequestBody OrderCreateDTO dto) {
    Long userId = SecurityContextHolder.getUserId();   // 业务只关心"当前用户"
    ...
}

// Feign 接口
@GetMapping("/user/address/{id}")
Result<...> getAddress(@PathVariable Long id);   // 干净

// 调用方
userFeignClient.getAddress(addressId);   // FeignAuthInterceptor 自动塞 X-User-Id 头
```

## 77.2  一次完整跨服务调用的链路

```
① 前端发 Authorization: Bearer xxx → gateway:9080
     gateway.AuthGlobalFilter (WebFlux GlobalFilter):
         解 JWT → 写入 X-User-Id HTTP 头 → 转发 (旧逻辑)

② HTTP 请求带 X-User-Id 头 → user-service:9001 (MVC)
     HeaderInterceptor.preHandle():
         读 X-User-Id 头 → SecurityContextHolder.setUserId()   ★ 新增
     UserController.xxxMethod():
         Long uid = SecurityContextHolder.getUserId();         ★ 业务直接拿
     UserService 内部调 Feign:
         someFeignClient.method(args)
     FeignAuthInterceptor.apply(template):                     ★ 新增
         SecurityContextHolder.getUserId() → template.header("X-User-Id")
     HeaderInterceptor.afterCompletion():
         SecurityContextHolder.remove()   (防 ThreadLocal 内存泄漏)

③ Feign 出去的请求自带 X-User-Id 头 → product-service:9002
     重复 ② 的流程
```

## 77.3  common-security 4 大组件

| 组件 | 位置 | 职责 |
|---|---|---|
| `JwtUtil` | `util/JwtUtil.java` | 生成/解析 token (合并自 user+gateway 两份) |
| `JwtProperties` | `properties/JwtProperties.java` | `@ConfigurationProperties` 绑 yml `jwt.*` |
| `HeaderInterceptor` | `interceptor/HeaderInterceptor.java` | 进站: `X-User-Id` 头 → `SecurityContextHolder` |
| `FeignAuthInterceptor` | `interceptor/FeignAuthInterceptor.java` | 出站: `SecurityContextHolder` → `X-User-Id` 头 |

加上自动装配 3 件套:`SecurityAutoConfiguration` + `WebMvcConfig` + `AutoConfiguration.imports`。

## 77.4  HeaderInterceptor 源码 (进站)

```java
@Slf4j
public class HeaderInterceptor implements HandlerInterceptor {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_NAME = "X-User-Name";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 读 X-User-Id 头 → 塞 ThreadLocal
        String userId = request.getHeader(HEADER_USER_ID);
        if (StringUtils.hasText(userId)) {
            SecurityContextHolder.setUserId(userId);
        }
        String userName = request.getHeader(HEADER_USER_NAME);
        if (StringUtils.hasText(userName)) {
            SecurityContextHolder.setUserName(userName);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // ⭐⭐⭐ 必须清, 否则 Tomcat 线程池复用时下一个请求会看到上一个的 userId
        SecurityContextHolder.remove();
    }
}
```

**关键点:**
- `implements HandlerInterceptor`:Spring MVC 标准拦截器接口
- `StringUtils.hasText(...)`:Spring 工具,"非 null 且去空白后非空"
- `return true`:不返 true Spring 会拦下请求
- `afterCompletion + remove()`:不清就会出 "看到别人订单" 这种灵异 bug

## 77.5  FeignAuthInterceptor 源码 (出站)

```java
@Slf4j
public class FeignAuthInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        Long uid = SecurityContextHolder.getUserId();
        String uname = SecurityContextHolder.getUserName();
        if (uid != 0L) {
            template.header(HeaderInterceptor.HEADER_USER_ID, String.valueOf(uid));
        }
        if (StringUtils.hasText(uname)) {
            template.header(HeaderInterceptor.HEADER_USER_NAME, uname);
        }
    }
}
```

**关键点:**
- `implements feign.RequestInterceptor`:OpenFeign 自己的拦截器接口(注意包名,跟 Spring 的 HandlerInterceptor 完全两套)
- Feign 发 HTTP 之前调 `apply()`,给我们改 `RequestTemplate` 的机会
- `uid != 0L`:`SecurityContextHolder.getUserId()` 没登录返回 0(common-core 约定)
- 用 `HeaderInterceptor.HEADER_USER_ID` 常量,跟入站读的是同一个头名

## 77.6  ⭐ 最深的坑 — @ConditionalOnClass + @Import 不能混用

**错误版** (gateway 启动爆 `NoClassDefFoundError: WebMvcConfigurer`):

```java
@AutoConfiguration
@Import({WebMvcConfig.class, JwtUtil.class})   // Class 字面量 → 强制 JVM 加载
public class SecurityAutoConfiguration { ... }

@Configuration
@ConditionalOnClass(name = "...WebMvcConfigurer")   // 来不及拦, 类已经被加载
public class WebMvcConfig implements WebMvcConfigurer { ... }
```

**报错原因:**
1. JVM 加载 `SecurityAutoConfiguration` 字节码
2. 看到 `@Import(WebMvcConfig.class)` ← 强制把 `WebMvcConfig` 加载进 ClassLoader
3. 加载 `WebMvcConfig` 时发现 `implements WebMvcConfigurer`
4. JVM 要找 `WebMvcConfigurer` 这个接口
5. gateway 没有 spring-webmvc → **找不到 → NoClassDefFoundError**
6. `@ConditionalOnClass` 是"是否实例化 Bean"的检查,**类加载比这更早**,根本来不及拦

**正确版** (走 `AutoConfiguration.imports` + ASM 字节码扫描):

```java
// SecurityAutoConfiguration: 删掉对 WebMvcConfig 的 @Import
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
@Import(JwtUtil.class)
public class SecurityAutoConfiguration {
    @Bean
    public FeignAuthInterceptor feignAuthInterceptor() {
        return new FeignAuthInterceptor();
    }
}

// WebMvcConfig: 升级成独立 @AutoConfiguration
@AutoConfiguration                                         // ← @AutoConfiguration 不是 @Configuration
@ConditionalOnClass(name = "...WebMvcConfigurer")          // ← 字符串名, 不是 class
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HeaderInterceptor());
    }
}
```

```
# META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.minimall.common.security.config.SecurityAutoConfiguration
com.minimall.common.security.config.WebMvcConfig
```

**为什么能避免 NoClassDefFoundError:**
- Spring 解析 imports 文件时**用 ASM 字节码扫描**预检 `@ConditionalOnClass`
- `name="..."` 字符串形式让 ASM 在 classpath 字节码里**搜类名**,**完全不加载 WebMvcConfig 类本身**
- gateway 找不到 `WebMvcConfigurer` → 条件不满足 → **直接跳过**,JVM 根本不碰 `WebMvcConfig`
- 业务服务找得到 → 条件满足 → 真正加载并实例化

## 77.7  Controller 改造范围 (SEC.10)

总共 8 个 Controller, 25 处 `@RequestHeader` 改 `SecurityContextHolder.getUserId()`:

| 服务 | Controller | 处数 |
|---|---|---|
| user | UserController | 1 |
| user | CouponController | 2 |
| user | AddressController | 5 |
| product | FavoriteController | 4 |
| order | CartItemController | 4 |
| order | OrdersController | 5 (sign 改, ship 不改) |
| order | SeckillController | 2 |
| review | ReviewController | 2 |

改造模板(情况 A — 唯一参数):
```java
// 之前
public Result<List<XxxVO>> xxx(@RequestHeader("X-User-Id") Long userId) { ... }
// 之后
public Result<List<XxxVO>> xxx() {
    Long userId = SecurityContextHolder.getUserId();
    ...
}
```

改造模板(情况 B — 跟 @PathVariable / @RequestBody 共存):
```java
// 之前
public Result<X> create(@RequestHeader("X-User-Id") Long userId, @RequestBody Dto dto) { ... }
// 之后 (删 @RequestHeader 那一行 + 多余的逗号)
public Result<X> create(@RequestBody Dto dto) {
    Long userId = SecurityContextHolder.getUserId();
    ...
}
```

**例外:** admin 端点(如 `OrdersController.ship`)本来就不取 userId,**不要改**。

## 77.8  Feign Client 改造范围 (SEC.11)

⭐ 三处对齐:**接口 / Fallback / 所有调用方**,缺一不可!

| 服务 | 文件 | 改动 |
|---|---|---|
| order | `client/UserFeignClient` | 接口删 `@RequestHeader` 形参 |
| order | `client/fallback/UserFeignClientFallback` | `getAddress` 改成单参 |
| order | `service/impl/OrdersServiceImpl` line 124 | 调用删 `userId` 实参 |
| review | `client/OrdersFeignClient` | 接口删 `@RequestHeader` 形参 |
| review | `client/fallback/OrdersFeignClientFallback` | `getOrderDetail` 改成单参 |
| review | `service/impl/ReviewsServiceImpl` line 67 | 调用删 `userId` 实参 |

如果只改 Feign 接口忘了 Fallback,**编译报 "未覆盖抽象方法"**;
忘了调用方,**编译报 "实际参数列表长度不同"**。

## 77.9  端到端验证 (SEC.12)

启动 4 个服务(gateway + user + product + order),用 `alice/123456` 登录:

| # | 端点 | 验证项 | 结果 |
|---|---|---|---|
| ① | POST /user/login | 白名单跳过 + JWT 生成 | ✅ token |
| ② | GET /user/me | gateway 解 JWT → 塞 X-User-Id → user 服务 HeaderInterceptor → SecurityContextHolder.getUserId()=1 | ✅ userId=1 |
| ③ | GET /user/address | user 服务 SecurityContextHolder 工作 + 查 alice 2 地址 | ✅ |
| ④ | GET /order/my | order 服务【独立】走通同样链路 + 查 alice 14 订单 | ✅ |
| ⑤ | GET /user/1/with-product/1 | user → Feign 出站调 product, FeignAuthInterceptor 不破坏链路 | ✅ |

## 77.10  踩坑实录

1. **`@ConditionalOnClass + @Import` 不能混用** — 详见 77.6
2. **`provided` scope 不传递** — common-security pom 必须显式加 `jakarta.servlet-api` (`provided`)
3. **Windows 多 JDK PATH 顺序坑** — JAVA_HOME=Java 21 但 PATH 里 Java 8 在前,要用绝对路径 `& "D:\jdk-21.0.11\bin\java.exe"`

## 77.11  本轮累计能力

| 能力 | 适合场景 |
|---|---|
| 共享 jar 自动配置 (`@AutoConfiguration` + imports) | 抽任何"业务无关的横切机制"(认证/限流/日志/链路追踪) 成 starter |
| `@ConditionalOnClass(name="...")` 按 classpath 选激活 | 一个 jar 兼容 MVC + WebFlux 等场景, 按宿主项目自动选合适部分 |
| `ThreadLocal` (`SecurityContextHolder`) 替代显式参数透传 | 解耦业务代码与 HTTP/RPC 细节, 业务层只关心"当前用户是谁" |
| 进站 + 出站拦截器对称设计 | 认证信息在跨服务调用链里自动跟随, 业务代码 0 改动 |
| 分清 MVC (HandlerInterceptor) 与 WebFlux (GlobalFilter) | 网关用 GlobalFilter, 业务服务用 HandlerInterceptor, 两套不互通 |

## 61.3  G3.8 没做但已铺路的 (后续)

| 待办 | 何时做 |
|---|---|
| 高并发压测 (10000 并发) | 验证 Lua 真原子 + MQ 削峰效果 |
| seckill_order 加 (user_id, activity_id) UNIQUE | 终极幂等防线 |
| 库存非懒加载, 改 activity 启动时定时灌 | 避免首次 race |
| Lua 脚本扩展: 限速 (令牌桶), 风控 | 防机器人 |
| 真支付接入 (微信/支付宝) | 秒杀订单走真支付 |
| 秒杀订单超时关单 | 跟 G3.7 同款 delay+DLX |
| seckill_order 用普通 Orders 表 | 而不是单独一张 (架构简化讨论) |

## 61.4  G3.8 搬迁经验沉淀

| 经验 | 来源 |
|---|---|
| Lua 是 Redis 单线程的礼物, 5 行串成 1 行原子 | 章 58 |
| Lua 必须 tonumber() 把 GET 的字符串转数字 | seckill_stock.lua |
| Lua 用 KEYS / ARGV 分开, Cluster 模式下 keys 要在同 slot | 章 58.3 |
| Spring @Bean 化 Lua → EVALSHA 优化 | LuaScriptConfig |
| 高并发抢购 = Lua 原子 + MQ 异步 + 轮询 3 态, 是标准方案 | 章 57 |
| 用户体验: 立即响应 + 前端轮询, 比同步等数据库快 | 章 59.4 |
| 消息体用 String 拼接简单可靠, 不必上 JSON | 章 59.2 |
| 幂等多道防线 (Lua + DB count + UK) | 章 59.3 |
| MQ 异步 Listener 跟 Web 端流程解耦, 是削峰核心 | 章 59.1 |

---

**G3.8 完毕**. 微服务从"业务覆盖大半 + MQ 真业务" 走到 "**Lua 原子操作 + MQ 异步下单 + 3 态轮询 全栈接通**".
G 阶段所有业务模块搬迁完毕: User / Address / Product / Category / Favorite / CartItem / Orders / Seckill 全部就位.
下一步可以: ① commit + push 留里程碑, ② G4 引分布式事务 (Seata/TCC) 补扣库存功能, ③ 压测验证.

---

# 62. G3.9 — 补 Product 模块缺失的 5 个方法

## 62.1  背景: 全面 diff 发现的洞

G 阶段搬完后做了一次单体 vs 微服务**最严谨的全面对比**, 发现 Product 服务**只搬了壳**:

| 单体 IProductService | 微服务 IProductService |
|---|---|
| 5 个业务方法 (getProductDetail / updateProduct / deleteProduct / searchProducts / getHotSearch) | **0 个** (空接口) |

| 单体 ProductController | 微服务 ProductController |
|---|---|
| 6 端点 (分页搜+详情+POST/PUT/DELETE+热搜) | 3 端点 (getById + list 前 10 条 + flaky) |

**意味着**: 当前微服务版用户**根本搜不了商品**, 只能 `getById(123)` 一个个看. 这是核心电商功能的断点.

## 62.2  搬的 5 个方法逐个解释

### ① getProductDetail (详情, 带 Redis 缓存)

```java
@Override
public Product getProductDetail(Long id) {
    String key = "product:detail:" + id;

    // 1) 先查 Redis
    Object cached = redisTemplate.opsForValue().get(key);
    if (cached != null) {
        log.info("缓存命中 key={}", key);
        return (Product) cached;
    }

    // 2) 没中查 MySQL
    Product product = productMapper.selectById(id);
    if (product == null) return null;

    // 3) 回写 Redis, 10 分钟过期
    redisTemplate.opsForValue().set(key, product, 10, TimeUnit.MINUTES);
    return product;
}
```

**核心模式**: **Cache-Aside Pattern** (旁路缓存)
- 读: 先 Cache, 没中查 DB, 回写 Cache
- 写: 改 DB, 删 Cache (下次读自动回填新值)

为啥不"改 DB + 改 Cache"? 因为并发改时 Cache 跟 DB 顺序难保证. **改 DB + 删 Cache** 是行业标准.

### ② updateProduct (改 + 删缓存)

```java
@Override
public boolean updateProduct(Product product) {
    boolean ok = updateById(product);                                   // 改 MySQL
    if (ok) {
        redisTemplate.delete("product:detail:" + product.getId());      // 删缓存
    }
    return ok;
}
```

下次有人来查这个商品, getProductDetail 走未命中分支, 重查 DB 看到新值再回写缓存. **缓存最终一致**.

### ③ searchProducts (核心搜索 + 顺手记录热搜)

```java
public IPage<Product> searchProducts(Integer page, Integer size,
                                     Long categoryId, String keyword,
                                     BigDecimal minPrice, BigDecimal maxPrice) {
    Page<Product> pageObj = new Page<>(page, size);             // MP 分页对象

    QueryWrapper<Product> w = new QueryWrapper<>();             // 动态条件
    if (categoryId != null) w.eq("category_id", categoryId);
    if (StringUtils.hasText(keyword)) {
        w.like("name", keyword);

        // ⭐ 顺手把搜索词扔进 Redis ZSet 当热搜
        redisTemplate.opsForZSet().incrementScore("hot:search", keyword, 1);
        redisTemplate.expire("hot:search", 24, TimeUnit.HOURS);
    }
    if (minPrice != null) w.ge("price", minPrice);
    if (maxPrice != null) w.le("price", maxPrice);
    w.orderByDesc("create_time");

    return this.page(pageObj, w);                               // MP 自动分页 SQL
}
```

**关键知识点**:

1. **MP 分页插件** —— 父 pom 加 `mybatis-plus-jsqlparser` (3.5.9+ 的坑, 见 62.4), Config 类注册 `PaginationInnerInterceptor`, 一行 `this.page(pageObj, w)` 自动改写 SQL 加 LIMIT + 跑 COUNT
2. **Redis ZSet 当热搜** —— `ZINCRBY` 原子自增分数, 后续 `ZREVRANGE 0 N-1` 拿排行
3. **24h TTL** —— 防止历史关键词永远占榜
4. **动态条件** —— null 不加 WHERE, 调用方可灵活组合筛选

### ④ getHotSearch (取 ZSet Top N)

```java
public List<Map<String, Object>> getHotSearch(int topN) {
    Set<ZSetOperations.TypedTuple<Object>> tuples =
            redisTemplate.opsForZSet().reverseRangeWithScores("hot:search", 0, topN - 1);

    List<Map<String, Object>> result = new ArrayList<>();
    if (tuples != null) {
        for (ZSetOperations.TypedTuple<Object> t : tuples) {
            Map<String, Object> item = new HashMap<>();
            item.put("keyword", t.getValue());
            item.put("count", t.getScore());
            result.add(item);
        }
    }
    return result;
}
```

`reverseRangeWithScores(key, 0, N-1)` = "倒序拿前 N 个 + 它们的分数", 这是 ZSet 排行榜的标准用法.

## 62.3  MybatisPlusConfig (分页插件)

```java
@Configuration
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

**没这个 Bean**: `ServiceImpl.page()` 不走分页 SQL, 返全量数据然后自己截.
**有这个 Bean**: 自动改写 SQL 加 LIMIT, 多跑一条 count SQL 拿 total.

## 62.4  G3.9 大坑: MP 3.5.9 把分页插件拆包了

直接 import 编译报"找不到符号"`PaginationInnerInterceptor`. 排查发现 .m2 仓库里 `mybatis-plus-extension-3.5.9.jar` 真的没这个类 (3.5.7 还在).

**原因**: MP 3.5.9+ 把 `PaginationInnerInterceptor` 拆到了 **独立包 `mybatis-plus-jsqlparser`** (因为这个拦截器依赖 jsqlparser 解析 SQL, 体积大).

**解法**: 父 pom + product pom 都加这个独立依赖

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>
    <version>${mybatis-plus.version}</version>
</dependency>
```

## 62.5  G3.9 第二个大坑: Jackson 默认不会序列化 LocalDateTime

Product entity 的 `createTime` 是 `java.time.LocalDateTime`. 首次 getProductDetail 触发"写 Redis" 时炸:

```
SerializationException: Java 8 date/time type `java.time.LocalDateTime` not supported by default:
add Module "com.fasterxml.jackson.datatype:jackson-datatype-jsr310" to enable handling
```

**原因**: `GenericJackson2JsonRedisSerializer` 用的 ObjectMapper 没注册 `JavaTimeModule`, Jackson 默认不认 LocalDateTime / Instant / ZonedDateTime 等 JDK 8 时间类型.

**解法**: RedisConfig 自定义 ObjectMapper 注册模块

```java
ObjectMapper om = new ObjectMapper();
om.registerModule(new JavaTimeModule());          // ⭐ 关键: 注册 JDK 8 时间模块
om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
        ObjectMapper.DefaultTyping.NON_FINAL);    // 反序列化要带 @class
GenericJackson2JsonRedisSerializer jsonSer = new GenericJackson2JsonRedisSerializer(om);
```

## 62.6  G3.9 累计能力

| 维度 | 状态 |
|---|---|
| 商品搜索 (分页 + 多条件) | ✅ |
| 商品详情 (Redis 缓存 + 10 分钟 TTL) | ✅ |
| 商品 CRUD (新建 / 改 / 删) | ✅ |
| 热搜 (Redis ZSet 24h 排行) | ✅ |
| 改商品自动失效缓存 | ✅ |
| MP 分页插件 | ✅ |
| LocalDateTime Redis 序列化 | ✅ |

---

# 63. G3.10 — 补扣库存断点 (业务真闭环)

## 63.1  背景: G3.7 留的债

读 G3.7 搬过来的 OrdersServiceImpl, 类注释明写:

```java
// ④ productMapper.deductStock / restoreStock 全部去掉
//    = 简化版【不扣库存】, 后续 G4 上分布式事务再补
```

**实际状态**: 用户下 100 单也不扣 1 件库存. 业务有断点.

但 product 表的 stock 字段还在, ProductMapper 的 deductStock/restoreStock SQL 也搬过来了, **缺的只是 order 跨服务的调用**.

## 63.2  方案: order 通过 Feign 调 product 扣库存

```
┌─ order 服务 ─────────────┐    ┌─ product 服务 ──────────┐
│ OrdersServiceImpl       │    │ ProductController       │
│   createOrder()         │    │   deductStock(id, qty)  │
│     ...                 │    │     │                   │
│     productFeignClient ─┼────┼─►  │ productService     │
│       .deductStock()    │    │     .deductStock(id,qty)│
│     ...                 │    │       │                 │
│                         │    │       └─ productMapper  │
│                         │    │           .deductStock  │
│                         │    │           (原子 SQL)    │
└─────────────────────────┘    └─────────────────────────┘
```

## 63.3  product 服务暴露 2 个内部端点

```java
/** 扣库存 (内部端点, 给 order 服务 Feign 调) */
@PutMapping("/{id}/stock/deduct")
public Result<Integer> deductStock(@PathVariable Long id, @RequestParam Integer qty) {
    int rows = productService.deductStock(id, qty);
    if (rows == 0) {
        throw new BusinessException(400, "库存不足");
    }
    return Result.success(rows);
}

/** 回库存 (cancel/close 时调) */
@PutMapping("/{id}/stock/restore")
public Result<Integer> restoreStock(@PathVariable Long id, @RequestParam Integer qty) {
    return Result.success(productService.restoreStock(id, qty));
}
```

ProductServiceImpl 转发给原子 SQL:

```java
@Override
public int deductStock(Long productId, Integer quantity) {
    int rows = productMapper.deductStock(productId, quantity);
    if (rows > 0) {
        redisTemplate.delete("product:detail:" + productId);  // ⭐ 失效缓存
    }
    return rows;
}
```

**注意**: 扣完库存**主动删缓存**, 防止下次详情接口返回旧的 stock 值.

## 63.4  ProductFeignClient 加 2 方法

```java
@FeignClient(name = "mini-mall-product", fallback = ProductFeignClientFallback.class)
public interface ProductFeignClient {

    @GetMapping("/product/{id}")
    Result<Map<String, Object>> getById(@PathVariable("id") Long id);

    /** G3.10 扣库存 */
    @PutMapping("/product/{id}/stock/deduct")
    Result<Integer> deductStock(@PathVariable("id") Long id, @RequestParam("qty") Integer qty);

    /** G3.10 回库存 */
    @PutMapping("/product/{id}/stock/restore")
    Result<Integer> restoreStock(@PathVariable("id") Long id, @RequestParam("qty") Integer qty);
}
```

**关键**: Feign 方法签名要跟对方 Controller 完全一致 (HTTP 方法 / 路径 / 注解), Spring Cloud 看注解编织 HTTP 请求.

## 63.5  createOrder 加扣库存循环

```java
// 第 7 步: 扣库存
for (CartItem ci : cartItems) {
    try {
        Result<Integer> deductResp = productFeignClient.deductStock(ci.getProductId(), ci.getQuantity());
        if (deductResp == null || deductResp.getCode() != 200) {
            Map<String, Object> p = productMap.get(ci.getProductId());
            throw new BusinessException(400, "库存不足: " + p.get("name"));
        }
    } catch (BusinessException e) {
        throw e;
    } catch (Exception feignEx) {
        Map<String, Object> p = productMap.get(ci.getProductId());
        throw new BusinessException(400, "库存不足: " + p.get("name"));
    }
}
```

抛 BusinessException → `transactionTemplate.execute` 自动回滚 **order 库的事务** (orders + order_item 不入库).

⚠ **但 product 已扣的库存不会自动回滚** —— 这就是后面 G5 Seata 要解的问题.

## 63.6  cancelOrder + closeOrderByMQ 加回库存

```java
order.setStatus(OrderStatus.CANCELLED);
ordersMapper.updateById(order);

// G3.10 回库存
QueryWrapper<OrderItem> itemW = new QueryWrapper<>();
itemW.eq("order_id", orderId);
List<OrderItem> items = orderItemMapper.selectList(itemW);
for (OrderItem item : items) {
    productFeignClient.restoreStock(item.getProductId(), item.getQuantity());
}
```

**为啥要查 order_item 再循环还**: 一个订单可能有多个商品, 每个都要还.

**为啥 cancel 不抛事务**: cancel 本身是补救动作, 即使回库存失败也不能让"订单状态停在 CANCELLED 之外". H1 的 Fallback 设计就是基于这个考虑.

## 63.7  原子扣库存 SQL 的一致性

```sql
UPDATE product 
SET stock = stock - #{quantity}, sales = sales + #{quantity} 
WHERE id = #{productId} AND stock >= #{quantity}
```

**为啥这条 SQL 防超卖**:
1. **InnoDB 行级锁** —— 单条 UPDATE 自动给目标行加 X 锁, 其他并发请求排队
2. **WHERE 守卫** —— 库存不够 WHERE 不命中, 影响行数 = 0
3. **Java 层判断** —— `rows == 0` 抛"库存不足"

并发 1000 个请求抢最后 1 件, **零超卖**.

## 63.8  G3.10 累计能力

| 维度 | 状态 |
|---|---|
| 下单真扣库存 | ✅ |
| 取消订单 / 自动关单 回库存 | ✅ |
| 库存不足拒绝下单 | ✅ |
| 单 product 内部强一致 | ✅ (SQL 守卫) |
| 跨服务事务一致性 | ❌ (留 G5 Seata 解) |

---

# 64. H1 — Feign Fallback 服务降级

## 64.1  问题: 下游挂了, 上游裸抛 5xx

H1 之前的链路:

```
product 挂了
   ↓
order createOrder → productFeignClient.deductStock(...)
   ↓
Feign 连不上 → 抛 RetryableException / FeignException
   ↓
@Transactional 接住 → 异常一路冒到 Controller
   ↓
GlobalExceptionHandler 兜底 → 500 "系统繁忙"
   ↓
用户体验: 不明所以的 500
```

**简历级别项目不能这样**, 要有友好降级.

## 64.2  Feign Fallback 是什么

为 @FeignClient 接口注册一个**实现类**, 当 Feign 调用失败时, **Spring 自动回退到这个实现类**返兜底响应:

```
正常: order → Feign 代理 → HTTP 请求 → product 返 200 → 拿到数据
降级: order → Feign 代理 → 调用失败 → 调 Fallback Bean → 返兜底 Result
                                       ↑
                                  Sentinel 接管
```

## 64.3  3 个 Fallback 类全部代码

### order/ProductFeignClientFallback

```java
@Component
public class ProductFeignClientFallback implements ProductFeignClient {

    private static final Logger log = LoggerFactory.getLogger(ProductFeignClientFallback.class);

    @Override
    public Result<Map<String, Object>> getById(Long id) {
        log.warn("[Feign-Fallback] product.getById 降级 id={}", id);
        return Result.error(503, "商品服务暂不可用");
    }

    @Override
    public Result<Integer> deductStock(Long id, Integer qty) {
        log.warn("[Feign-Fallback] product.deductStock 降级 id={} qty={}", id, qty);
        return Result.error(503, "库存服务暂不可用,请稍后再试");
    }

    @Override
    public Result<Integer> restoreStock(Long id, Integer qty) {
        // ⚠ 这里只 log, 不返 error
        //   cancel/close 流程已经把订单关了, 库存不还能补偿
        //   生产应该把"待补偿库存"记一张 retry 表, 后台 job 重试
        log.warn("[Feign-Fallback] product.restoreStock 降级(库存未还) id={} qty={}", id, qty);
        return Result.success(0);
    }
}
```

**关键设计**:
- `getById` / `deductStock` 返 503, **阻断业务**
- `restoreStock` 返 success(0), **不阻断 cancel 流程**, 仅 log warn 让人工补偿
- 这种"不同方法不同降级策略" 是 Fallback 的精髓

### @FeignClient 注解关联 Fallback

```java
@FeignClient(
        name = "mini-mall-product",
        fallback = com.minimall.order.client.fallback.ProductFeignClientFallback.class
)
public interface ProductFeignClient { ... }
```

`fallback` 属性指向 Fallback 类的 `.class`, Spring 启动时把它注册成 Bean.

## 64.4  关键开关: feign.sentinel.enabled

```yaml
feign:
  sentinel:
    enabled: true
```

**没这开关**: `@FeignClient(fallback=...)` **写了不生效**, 仍抛 FeignException.

**有这开关**: Spring Cloud Alibaba 用 `SentinelFeign` 替换默认 Feign 工厂, 把 Sentinel 接进 Feign 的代理过程, **Sentinel 触发熔断时调 fallback**.

注意: yml 顶级 `feign:`, **不是 `spring.cloud.openfeign.feign:`** (这是 SCA 历史包袱, 没改).

## 64.5  验证: 杀 product, 看 fallback 日志

```bash
$ curl http://127.0.0.1:9001/user/1/with-product/1
{"code":500,"message":"商品服务暂不可用","data":null}    ← message 来自 fallback
```

user 服务日志:

```
WARN c.m.u.c.f.ProductFeignClientFallback : [Feign-Fallback] (user→product).getById 降级 id=1
```

确认 Fallback **被自动调用**了, 链路完整.

## 64.6  H1 累计能力

| 场景 | 原状 (H1 前) | 现状 (H1 后) |
|---|---|---|
| product 挂 → 调演示接口 | 抛 FeignException → 500 | fallback → "商品服务暂不可用" |
| product 挂 → 创建订单 | 整个 createOrder 抛异常 | fallback → 友好提示, 不下单 |
| product 挂 → 取消订单回库存 | cancel 跟着炸 | fallback log warn, cancel 继续 |
| user 挂 → 创建订单查地址 | 抛 FeignException | fallback → "用户服务暂不可用" |

---

# 65. H2 — 过时注释纠错 (小但重要)

## 65.1  背景

G3.10 已经补完扣库存, H1 已经加 fallback, **但** OrdersServiceImpl 类顶部的注释还是 G3.7 时的:

```java
// 订单服务实现 (G3.7 - 微服务版, 简化版不扣库存)
// ④ productMapper.deductStock / restoreStock 全部去掉
//    = 简化版【不扣库存】, 后续 G4 上分布式事务再补
```

读者看了**会以为还没扣**, 跟代码冲突. **代码跟注释脱节** 是新人接手项目最常见的坑.

## 65.2  改成准确版

```java
/**
 * 订单服务实现 (G3.7 搬迁 + G3.10 补扣库存 + H1 Feign fallback)
 *
 * vs 单体差异:
 *   ④ 扣/回库存改成 Feign 跨服务调用 productFeignClient.deductStock / restoreStock
 *      ⚠ 跨服务事务局限: order 抛异常时 product 已扣的库存不会自动回滚
 *        (分布式事务问题, 等 Seata/MQ 补偿表解)
 *   ⑥ H1: 3 个 Feign Client 都有 fallback, product/user 挂了走兜底
 *
 * 6 个方法分布:
 *   createOrder  ★ 最复杂, 8 步 + 锁 + 事务 + 事务外发 MQ + 跨服务扣库存
 *   cancelOrder  中等, 锁 + 事务 + 状态机 + 回库存
 *   closeOrderByMQ  简单 + 幂等, 没用户上下文 + 回库存
 */
```

## 65.3  H2 经验

| 经验 | 解释 |
|---|---|
| 注释要跟代码同步, 不然误导维护者 | 班级里"代码即文档" 的现实版 |
| 每次跨阶段改造, 类顶部的"故事"也要更新 | 否则就是技术债 |

---

# 66. G5 — Seata AT 分布式事务

## 66.1  问题再现: 单体的招在微服务为啥失灵

### 单体 createOrder 的事务结构

```java
transactionTemplate.execute(status -> {
    save(order);          // ┐
    saveBatch(items);     // │ 全部 mini_mall 库
    deductStock(qty);     // │ 同一个 Connection
    rows == 0 抛异常 → 全部回滚 ┘
});
```

**为啥单体一致**:
- `orders` / `order_item` / `product` 三张表都在 **同一个库 mini_mall**
- Spring 一个事务 = MySQL 一个 Connection
- 所有 SQL 都在这一个 Connection 上执行 → MySQL 行级锁 + InnoDB 事务回滚保证一致

### 微服务的不一致

```
order 进程 Connection A:                product 进程 Connection B:
  TransactionTemplate.execute            @Transactional
        ▼                                       ▼
  save(order)                            deductStock SQL (单独 commit)
  save(items)
  Feign deductStock ──HTTP─►             ⭐ 此时 Connection B 已 COMMIT
  抛异常
  Connection A 回滚                       Connection B 早已 COMMIT
  → orders 没了                          → product 库存仍 -1
```

**根因**: 微服务即使复用同一个 MySQL 库, **两个 Java 进程 = 两个连接池 = 两个独立本地事务**, 单纯本地事务保证不了跨服务一致性.

## 66.2  Seata AT 模式核心原理

### 三个角色

| 角色 | 担任者 |
|---|---|
| **TC** Transaction Coordinator | 独立部署的 seata-server (8091 端口) |
| **TM** Transaction Manager | 标 `@GlobalTransactional` 的发起方 (order) |
| **RM** Resource Manager | 各分支服务里的 DataSource (order + product) |

### 两阶段提交流程

```
┌─ Phase 1 (业务执行) ───────────────────────────┐
│  ① TM 发起: 调 TC 申请全局 XID                  │
│  ② order Feign 调 product, XID 在 header 透传  │
│  ③ product RM 拿 XID 加入全局事务              │
│  ④ product 本地事务执行 UPDATE stock           │
│     ⭐ 同时由代理 DataSource 自动写 undo_log    │
│  ⑤ product 本地事务 COMMIT                     │
│     ⭐ 一阶段就 commit, 释放本地锁              │
└─────────────────────────────────────────────────┘

┌─ Phase 2 (异步决策) ──────────────────────────┐
│  正常: TM 通知 TC commit → TC 通知 RM           │
│        RM 异步删 undo_log (无操作 DB 数据)      │
│                                                  │
│  异常: TM 通知 TC rollback → TC 通知 RM         │
│        RM 拿 undo_log 的 rollback_info 反向 SQL │
│        把 stock 恢复回去                        │
└─────────────────────────────────────────────────┘
```

### 关键: undo_log 是啥

每个业务库 (mini_mall) 必须建一张 `undo_log` 表:

```sql
CREATE TABLE undo_log (
    branch_id BIGINT NOT NULL,         -- 分支事务 ID
    xid VARCHAR(128) NOT NULL,         -- 全局事务 ID
    context VARCHAR(128) NOT NULL,
    rollback_info LONGBLOB NOT NULL,   -- ⭐ 序列化的前镜像 (SQL 执行前的数据)
    log_status INT NOT NULL,
    log_created DATETIME(6) NOT NULL,
    log_modified DATETIME(6) NOT NULL,
    UNIQUE KEY ux_undo_log (xid, branch_id)
);
```

`rollback_info` 存的是**业务 SQL 执行前的数据快照** (前镜像). 回滚时 Seata 拿这个快照反推一条 INSERT/UPDATE/DELETE 把数据恢复.

## 66.3  Seata Server 部署 (Docker file 模式)

```bash
docker run -d --name seata-server \
  -p 8091:8091 \
  -p 7091:7091 \
  -e SEATA_IP=127.0.0.1 \
  seataio/seata-server:1.8.0
```

**关键参数**:
- 8091 = TC RPC 端口 (客户端连这)
- 7091 = Console UI 端口 (浏览器看事务状态)
- `SEATA_IP=127.0.0.1` = TC 对外宣告的 IP (客户端用这地址回连)
- **file 模式**: 默认配置, session/lock 都在文件里, **不需要建 Seata 元数据 4 表 (global_table 等)**

生产应该用 db 模式 + Nacos 注册中心, 但学习项目 file 模式够用.

## 66.4  客户端依赖 (BOM 锁版本)

`pom.xml`:

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-seata</artifactId>
</dependency>
```

版本由 `spring-cloud-alibaba-dependencies` BOM 锁定, 跟 SCA 2023.0.1.2 对齐 Seata 1.8.0.

**这个 starter 干的事**:
1. 引 `seata-spring-boot-starter` (Seata 客户端核心)
2. 自动装配 `GlobalTransactionScanner` (扫描 @GlobalTransactional 注解)
3. 自动代理 `DataSource` 成 `DataSourceProxy` (拦截 SQL 写 undo_log)
4. 注入 `RpcCustomizer` (Feign 自动透传 XID header)

## 66.5  客户端 yml 配置

```yaml
seata:
  enabled: true
  application-id: ${spring.application.name}     # 客户端在 TC 上的标识
  tx-service-group: default_tx_group              # ⭐ 事务组名
  enable-auto-data-source-proxy: true             # ⭐ 自动代理 DataSource
  service:
    vgroup-mapping:
      default_tx_group: default                    # 事务组 → cluster 映射
    grouplist:
      default: 127.0.0.1:8091                      # cluster=default 的 TC 物理地址
  registry:
    type: file                                     # 不接 Nacos, file 模式
  config:
    type: file
```

**两层抽象**:
- `tx-service-group` (逻辑事务组) → `vgroup-mapping` → cluster (TC 集群名) → `grouplist` (TC 物理地址)
- 这个绕弯设计是为了支持多机房 / 切换 TC 集群

## 66.6  @GlobalTransactional 注解

```java
@Override
@GlobalTransactional(name = "createOrder-tx", rollbackFor = Exception.class)
public Map<String, Object> createOrder(Long userId, CreateOrderDTO dto) {
    // ⭐ 进入方法时 Seata TM 分配全局 XID
    //   Feign 自动透传 XID 到 product 的 header
    //   product 扣库存写 undo_log, 一阶段本地提交
    //   本方法抛任何异常 → TC 反向通知 product 走 undo_log 回滚 stock
    //   内部 TransactionTemplate 不动 (它是 order 本地事务,
    //   作为全局事务的一个分支自动管理)
    ...
}
```

**注意**:
1. `@GlobalTransactional` **跟 `@Transactional` 不冲突** —— 内层本地事务还要保留, 它是分支事务
2. `rollbackFor = Exception.class` 覆盖默认只回滚 RuntimeException 的行为, 全部异常都触发回滚
3. `name` 是事务名, console 看事务列表时分辨用

## 66.7  SeataTestController (端到端验证神器)

实际跑 createOrder 太麻烦 (要准备购物车 + 地址等), 写一个独立测试端点:

```java
@RestController
@RequestMapping("/seata-test")
public class SeataTestController {

    @Autowired private ProductFeignClient productFeignClient;

    @PostMapping("/deduct/{productId}/{qty}")
    @GlobalTransactional(name = "seata-test-deduct", rollbackFor = Exception.class)
    public Result<String> deduct(
            @PathVariable Long productId,
            @PathVariable Integer qty,
            @RequestParam(defaultValue = "false") boolean throwError
    ) {
        String xid = RootContext.getXID();            // ⭐ 拿当前全局 XID (Seata 注入到 ThreadLocal)
        log.info("[Seata-Test] 进入全局事务 XID={}", xid);

        // 1. Feign 调 product 扣库存
        Result<Integer> deductResp = productFeignClient.deductStock(productId, qty);
        if (deductResp == null || deductResp.getCode() != 200) {
            throw new RuntimeException("扣库存失败");
        }

        // 2. throwError=true 故意抛, 看回滚
        if (throwError) {
            throw new RuntimeException("Seata-Test 故意抛异常");
        }

        return Result.success("扣库存成功 XID=" + xid);
    }
}
```

`RootContext.getXID()` 是 Seata 提供的, 拿当前线程的全局事务 ID. 没在 `@GlobalTransactional` 范围内调用会返 null.

## 66.8  端到端验证 5 步

```bash
$ curl product/1                       # ① 初始 stock=200
"stock":200

$ curl -X POST seata-test/deduct/1/5   # ② 正常扣 5, XID=...191425
{"code":200, "data":"扣库存成功 XID=172.17.0.4:8091:1045628007625191425"}

$ curl product/1                       # ③ 提交后 stock=195
"stock":195

$ curl -X POST "seata-test/deduct/1/5?throwError=true"  # ④ 扣 5 + 抛异常
{"code":500, "message":"系统繁忙，请稍后再试"}

$ curl product/1                       # ⑤ 异常后 stock=195 (没变 190!)
"stock":195
```

⑤ 等于 ③ 不等于 190 = **Seata 把 product 已扣的库存自动恢复了**.

### Order 日志关键证据

```
[Seata-Test] 故意抛异常 XID=172.17.0.4:8091:1045628007625191427
transaction 172.17.0.4:8091:1045628007625191427 will be rollback
[172.17.0.4:8091:1045628007625191427] rollback status: Rollbacked   ⭐
```

### undo_log 表证据

```sql
SELECT COUNT(*) FROM undo_log;
-- 结果: 0
```

回滚成功后 Seata 自动删 undo_log 行, 表保持空.

## 66.9  XID 怎么从 order 透传到 product

```
order 调 Feign:
  RpcCustomizer 拦截 → 把 XID 塞进 HTTP header "TX_XID"
  POST /product/1/stock/deduct
  Header: TX_XID: 172.17.0.4:8091:1045628007625191427

product 接收:
  SeataHandlerInterceptor 拦截 → 从 header 取 XID
  → RootContext.bind(xid) 塞 ThreadLocal
  → 业务方法走自己的 SQL → 代理 DataSource 看到 ThreadLocal 有 XID
  → 自动注册到 TC 当分支事务 + 写 undo_log
```

**整条链路对业务代码透明**, 这是 Seata AT 模式最大的优势.

## 66.10  G5 大坑回顾

| 坑 | 表现 | 解 |
|---|---|---|
| Spring Boot 3 + Seata WARN BeanPostProcessorChecker | 启动有一堆 WARN | 已知问题, 不影响功能, 忽略 |
| 配 `tx-service-group` 写错 | 启动报"can not get available servers" | 跟 yml 的 `vgroup-mapping` 必须键对得上 |
| undo_log 没建 | RM 启动报 "Table undo_log doesn't exist" | 每个业务库 (mini_mall) 都要建 |
| Seata 容器 IP 是 docker 内 IP | XID 里能看见 172.17.0.4 | 客户端连 127.0.0.1:8091 即可, XID 内含的 IP 不影响 |
| @GlobalTransactional 加在 service 内部方法 | 同类自调用不生效 | 必须加在 Controller 或 Service 公开入口方法 |

## 66.11  AT vs TCC vs SAGA 简表 (面试问到)

| 模式 | 原理 | 业务侵入 | 适用 |
|---|---|---|---|
| **AT** | 框架自动写 undo_log, 二阶段反推 SQL 回滚 | 0 (透明) | 一般 CRUD 业务, 我们就用这个 |
| **TCC** | 业务写 try / confirm / cancel 3 个方法 | 高 (3 倍代码) | 强一致 + 跨非 DB 资源 (如调外部支付) |
| **SAGA** | 长事务拆成多段, 每段定补偿动作 | 中 | 长流程 (旅游订单 N 个步骤) |
| **XA** | 二阶段提交协议, 资源管理器原生支持 | 0 | 性能差 (锁表整个二阶段) |

**简历可以聊**: 选 AT 是因为 "业务无侵入 + 性能合理 + 框架成熟 + 单库内的强一致" 综合最优.

## 66.12  G5 累计能力

| 维度 | 状态 |
|---|---|
| 跨服务事务一致性 | ✅ (Seata AT) |
| order 抛异常自动回滚 product 库存 | ✅ (rollback status: Rollbacked) |
| undo_log 自动清理 | ✅ |
| XID 自动透传 (Feign) | ✅ |
| 业务代码零侵入 (只加 @GlobalTransactional) | ✅ |
| Seata Console UI | ✅ http://127.0.0.1:7091 |

## 66.13  G5 经验沉淀

| 经验 | 来源 |
|---|---|
| 单体事务 = 单 Connection, 微服务 = 多 Connection, 一致性方案天然不同 | 66.1 |
| AT 模式靠 undo_log 表 + 代理 DataSource, 业务零侵入 | 66.2 |
| Seata Server file 模式部署最简, 不用建 4 张元数据表 | 66.3 |
| `@GlobalTransactional` 跟 `@Transactional` 不互斥, 全局含本地 | 66.6 |
| XID 通过 Feign header 自动透传, 不用手动塞 | 66.9 |
| 每个业务库都要建 undo_log (跟 RM 注册的 DataSource 一一对应) | 66.10 |
| @GlobalTransactional 必须加在公开入口, 同类自调用失效 | 66.10 |
| 简历级别项目 = 至少一个分布式事务实战 | 66.11 |

---

**G3.9 + G3.10 + H1 + H2 + G5 全部完毕**. 微服务从"业务搬完但有断点 + 裸跑" 走到 "**业务真闭环 + 服务降级 + 跨服务强一致**". 已经具备小型电商生产框架的基本素质.
下一步可选: README 收尾上 GitHub, 或继续 G6~G10 增量功能 (物流/评价/优惠券/ES/后台).

---

# 67. G6 — 物流 / 签收 (开篇 + 状态机图)

## 67.1  G6 是啥, 凭啥做

G6 给订单加上 "**发货 → 签收**" 这段闭环, 同时上 Spring 内置定时任务 (`@Scheduled`).

业务背景:
- 之前下单 → 付款 → 取消, 已经能跑了 (G3.7).
- 但订单付款后**没法发货**, 永远卡在 status=1.
- 真实电商: 1 付款 → 2 发货 → 3 签收 才叫完整链路.
- 简历亮点: 这一段让你**完整讲清状态机 + Spring @Scheduled + cron 表达式 + 单机调度局限**.

为啥 G6 不是搬迁而是真增量?
- 单体的 `OrderStatus` 常量定义了 SHIPPED = 2 / COMPLETED = 3, 但 **没有任何代码真的把状态推到 2 或 3**.
- 单体只有 `OrderTimeoutTask` 一个空壳, 而且 `@Scheduled` 注解被注释了, 从来没跑过.
- 所以 G6 是"前人挖了坑, 我们填上".

## 67.2  状态机文字版图

```
┌────────────┐  payOrder    ┌────────────┐  ship      ┌────────────┐  sign      ┌────────────┐
│ 0 待付款   │ ───────────> │ 1 已付款   │ ─────────> │ 2 已发货   │ ─────────> │ 3 已完成   │
└────────────┘              └────────────┘            └────────────┘            └────────────┘
       │                          │                          │                  ▲
       │ cancel                   │ cancel                   │                  │
       ▼                          ▼                          │  超时(7天)       │
┌────────────┐              ┌────────────┐                   └──────────────────┘
│ 4 已取消   │              │ 4 已取消   │                   LogisticsScheduledTask
└────────────┘              └────────────┘                   自动签收
```

**核心规则**: 状态只能向后流转, 不能倒退.
- `ship` 只接受 1 → 2 (其他状态拒)
- `sign` 只接受 2 → 3
- 这是简历高频考点: "怎么防止订单状态被乱改". 答案就是这套**前置状态校验**.

## 67.3  G6 整体决策 (3 个关键)

### 决策 ① 表结构: 加 2 列 vs 独立 logistics 表

**选择**: 直接在 orders 表加 `logistics_no` + `logistics_company` 两列.

**理由**:
- 当前只有 1 种物流场景 (N=1), 拆独立表是过度设计.
- 按 `feedback_concrete_first`: 同一痛点重复 3 次再抽.
- 真出现"多承运商 / 转运 / 跨境拆单 / 一单多包裹"再独立表.

### 决策 ② 不写 getLogistics 独立端点

**初始设计** 是写 `GET /order/{id}/logistics`. 真做的时候发现:
- `GET /order/{id}` 详情接口已经能返回所有字段.
- 给 `OrderDetailVO` 加 2 字段 → 物流自动跟着详情返回.
- 单独搞 `/logistics` 端点是**重复造端点**.

**最终**: 删 getLogistics, 详情接口顺带返回物流.

**教训**: 别为了对称 (有 ship / sign 就一定要有 getLogistics) 而造端点. 业务上能复用就复用.

### 决策 ③ Spring @Scheduled vs MQ 延迟队列

7 天自动签收用 **@Scheduled 扫表**, 不用 MQ 延迟队列.
原因详见第 70 章选型对比.

---

# 68. 有限状态机 FSM 工程实现

## 68.1  什么是有限状态机 (FSM)

正式定义: 一个系统在任意时刻只能处于**有限个状态**之一, 状态间通过**事件**触发**有规则的**转换.

举几个例子帮记:
- 红绿灯: 红 → 绿 → 黄 → 红 (循环), 不能跳过.
- 订单: 上面那张图.
- TCP 连接: CLOSED → SYN_SENT → ESTABLISHED → ... 一共 11 个状态.

## 68.2  FSM 在工程里就是 4 步前置校验

每个改状态的方法都遵循同样的骨架. 这是**死的模板**, 背下来:

```java
public void someStateTransition(Long userId, Long orderId, /*...*/) {
    // 第 1 步: 查订单, 不存在 → 404
    Orders order = ordersMapper.selectById(orderId);
    if (order == null) {
        throw new BusinessException(404, "订单不存在");
    }

    // 第 2 步 (用户类操作): 越权校验 — 不是你的单 → 403
    //   admin 操作 (shipOrder) 可以跳过这步
    if (!order.getUserId().equals(userId)) {
        throw new BusinessException(403, "无权操作");
    }

    // 第 3 步: 状态机前置校验 — 当前状态不对 → 400
    //   这一行是 FSM 的精髓
    if (!order.getStatus().equals(EXPECTED_PREVIOUS_STATUS)) {
        throw new BusinessException(400, "订单状态不可 XX");
    }

    // 第 4 步: 改字段 + updateById
    order.setStatus(NEXT_STATUS);
    order.setSomeTimestamp(LocalDateTime.now());
    ordersMapper.updateById(order);
}
```

## 68.3  为啥要 `.equals` 不用 `==`

```java
if (!order.getStatus().equals(OrderStatus.PAID)) { ... }   // ✅ 推荐
if (order.getStatus() != OrderStatus.UNPAID) { ... }       // ⚠ 当前能跑
```

- `OrderStatus.PAID` 是 `byte` 字面量 (=1).
- `order.getStatus()` 是 `Byte` 包装类对象 (MP entity 默认包装类).
- 用 `==` / `!=`: Byte 会自动拆箱再比, 当前能跑.
- **但**如果哪天有人把常量类型改成 `Integer`, `==` 就变成**引用比较**, 100% 出 bug.
- 所以**好习惯就是用 `.equals`**, 不依赖自动拆箱.

## 68.4  G6 用 FSM 的 2 个真实例子

### shipOrder (admin 操作)

```java
@Override
public void shipOrder(Long orderId, ShipOrderDTO dto) {
    Orders order = ordersMapper.selectById(orderId);
    if (order == null) throw new BusinessException(404, "订单不存在");

    // ⭐ 跳过第 2 步 — admin 操作不需要 userId 校验
    //    真生产: 应该挂在 admin 网关后面, 用 RBAC 鉴权

    // 第 3 步: 必须 PAID 才能发货
    if (!order.getStatus().equals(OrderStatus.PAID)) {
        throw new BusinessException(400, "订单状态不可发货");
    }

    // 第 4 步: 改状态 + 填物流字段
    order.setStatus(OrderStatus.SHIPPED);
    order.setShipTime(LocalDateTime.now());
    order.setLogisticsCompany(dto.getLogisticsCompany());
    order.setLogisticsNo(dto.getLogisticsNo());
    ordersMapper.updateById(order);
}
```

### signOrder (用户操作)

```java
@Override
public void signOrder(Long userId, Long orderId) {
    Orders order = ordersMapper.selectById(orderId);
    if (order == null) throw new BusinessException(404, "订单不存在");

    // ⭐ 第 2 步: 必须做! 防止 A 用户签 B 用户的单
    if (!order.getUserId().equals(userId)) {
        throw new BusinessException(403, "无权操作");
    }

    // 第 3 步: 必须 SHIPPED 才能签收
    if (!order.getStatus().equals(OrderStatus.SHIPPED)) {
        throw new BusinessException(400, "订单状态不可签收");
    }

    // 第 4 步: 改状态 + 填签收时间
    order.setStatus(OrderStatus.COMPLETED);
    order.setFinishTime(LocalDateTime.now());
    ordersMapper.updateById(order);
}
```

## 68.5  ship vs sign 差异对照表 (理解为啥这么设计)

| 区别点 | shipOrder | signOrder | 为啥不同 |
|--------|-----------|-----------|---------|
| userId 参数 | ❌ 没有 | ✅ 有 | admin 操作不需要锁定用户 |
| 越权校验 | ❌ 跳过 | ✅ 必须做 | 用户不能签别人的单 |
| 前置状态 | PAID (1) | SHIPPED (2) | 状态机方向 |
| 改成状态 | SHIPPED (2) | COMPLETED (3) | 状态机方向 |
| 时间字段 | shipTime | finishTime | 业务语义 |
| 物流字段 | 填 2 个 | 不填 | 发货才有物流 |

## 68.6  状态机要不要上 Spring StateMachine?

**不上!** 太重了.

- Spring StateMachine 是个完整框架, 自带 DSL 配置 + 持久化 + 监听器.
- 我们这套订单状态机才 5 个状态, 4 步前置校验就够.
- 上了反而难维护. 上 StateMachine 的合理场景: 状态数 >= 10 + 转换关系复杂 + 需要可视化.

简历讲法: "评估过 Spring StateMachine, 当前状态规模 (5 状态 4 转换) 不需要, 用 4 步前置校验即可."

---

# 69. @Scheduled 定时任务深度解析

## 69.1  Spring 定时调度 3 件套

```
1. 启动类加 @EnableScheduling     ← 开总开关
2. 方法上加 @Scheduled(cron=...)  ← 标记要调度的方法
3. 类上加 @Component             ← 让 Spring 扫到这个类
```

**关键陷阱**: Spring Boot 默认**不会**启用定时调度, 必须显式 `@EnableScheduling`. 新手常踩坑: 写了 `@Scheduled(cron="...")` 没生效, 找半天才发现忘了开总开关.

代码长这样:

```java
// 启动类
@SpringBootApplication
@EnableScheduling                          // ⭐ 不加这个 @Scheduled 全失效
public class MiniMallOrderApplication { ... }

// 定时任务类
@Component                                 // ⭐ 不加 Spring 扫不到
public class LogisticsScheduledTask {

    @Scheduled(cron = "0 0 * * * ?")       // 每小时整点
    public void autoSignTimeoutOrders() { ... }
}
```

## 69.2  cron 表达式 6 字段速记

Spring 用的 cron = **6 字段**, 比标准 Linux 多一个**秒**.

```
   ┌───── 秒 (0-59)
   │ ┌─── 分 (0-59)
   │ │ ┌─── 时 (0-23)
   │ │ │ ┌── 日 (1-31)
   │ │ │ │ ┌── 月 (1-12)
   │ │ │ │ │ ┌─ 周 (0-7, 0/7 都是周日)
   │ │ │ │ │ │
   0 0 * * * ?   ← 每小时整点 (0 秒 0 分 任意时)
   0 */5 * * * ? ← 每 5 分钟
   0 0 3 * * ?   ← 每天凌晨 3 点
   0 0 0 1 * ?   ← 每月 1 号 0 点
   0 0 9 ? * MON ← 每周一上午 9 点
```

符号:
- `*` = 任意值
- `?` = 占位 (日和周只能一个用具体值, 另一个必须 `?`)
- `*/N` = 每 N
- `1-5` = 范围 (周一到周五)
- `1,3,5` = 列表

为什么日和周不能同时具体? 因为 "每周一" 和 "每月 15 号" 是冲突的, 得选一个.

## 69.3  @Scheduled 4 种触发方式

| 方式 | 例子 | 适用场景 |
|------|------|---------|
| `cron` | `"0 0 * * * ?"` | **精确时间点** (每天 3 点) |
| `fixedRate` | `5000` 毫秒 | 上次**开始**后多久再跑 (不管上次跑完没) |
| `fixedDelay` | `5000` | 上次**结束**后多久再跑 (推荐, 防重叠) |
| `initialDelay` | 配合上面 | 启动后延迟 N 毫秒再跑第一次 |

```java
@Scheduled(cron = "0 0 * * * ?")           // 精确时间
@Scheduled(fixedRate = 60_000)             // 每分钟开始一次
@Scheduled(fixedDelay = 60_000)            // 上次完成后等 60 秒
@Scheduled(fixedDelay = 60_000, initialDelay = 30_000)  // 启动 30 秒后开始
```

**怎么选**:
- 业务有明确时间点 (整点 / 凌晨) → `cron`
- 任务不长 + 时间精度低 → `fixedRate`
- 任务可能跑很久 → `fixedDelay` (防重叠很重要)

## 69.4  单机调度的致命局限 ⚠️

```
order-service 实例1 (跑定时) ┐
order-service 实例2 (跑定时) ┤── 3 个实例都到点了同时跑
order-service 实例3 (跑定时) ┘
                              ↓
                同一个订单被签收 3 次! finishTime 被改 3 次!
```

**当前**: 微服务只跑 1 个 order 实例 → 没事.
**生产**: 多实例必须配合 **互斥机制**, 否则数据乱.

## 69.5  分布式调度 3 大方案 (面试常问)

### 方案 1: Redis 分布式锁 (轻量)

```java
@Scheduled(cron = "0 0 * * * ?")
public void doSomething() {
    String lockKey = "lock:scheduled:auto-sign";
    String owner = UUID.randomUUID().toString();
    Boolean got = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, owner, Duration.ofMinutes(50));
    if (!Boolean.TRUE.equals(got)) {
        log.info("已有实例在跑, 我跳过");
        return;
    }
    try {
        // 真业务
    } finally {
        // Lua 脚本保证 "我的锁才删, 别人的不删"
        if (owner.equals(redisTemplate.opsForValue().get(lockKey))) {
            redisTemplate.delete(lockKey);
        }
    }
}
```

**优点**: 代码改动小, 不引第三方组件.
**缺点**: Redis 挂了就裸跑, 锁超时设短了可能两个实例都跑, 设长了实例崩了任务卡死.

### 方案 2: 选主 (ZK / Nacos)

启动时选一个实例当 leader, 只有 leader 跑定时. 其他实例就算到点了也不跑.

代码里 `@Scheduled` 方法第一步检查自己是不是 leader, 不是就 return.

**优点**: 简单清晰.
**缺点**: leader 挂了得有选主机制 (Curator/Raft).

### 方案 3: 分布式调度框架 (重型)

XXL-Job / ElasticJob / Quartz Cluster.

**XXL-Job 最常用**, 大众点评开源:
- 一个独立的 admin 调度中心 (Web 控制台).
- 业务实例注册成 executor.
- 调度中心按策略分发任务 (轮询 / 一致性 hash / 分片).
- 自带失败重试 / 告警 / 日志.

**优点**: 功能全, 简历看着专业.
**缺点**: 引一个新组件, 多一个运维负担.

**简历讲法**: "评估过单机 @Scheduled、Redis 分布式锁、XXL-Job 三个方案. 当前 1 实例没必要上重的, 写了 Redis 锁的 TODO 注释方便扩展."

---

# 70. 单体 MQ 延迟队列 vs 微服务 @Scheduled (选型对比) ⭐⭐⭐

这一章是 G6 教学价值最高的部分, 简历可以专门讲这个对比.

## 70.1  两个场景, 选型截然不同

| 场景 | 用啥 | 落地代码 |
|------|------|---------|
| **下单 30 分钟没付款 → 关单** | RabbitMQ 延迟队列 (TTL+DLX) | `OrderCloseListener` (G3.7 已搬) |
| **发货 7 天没签收 → 自动签收** | Spring `@Scheduled` 扫表 | `LogisticsScheduledTask` (G6.7 刚写) |

## 70.2  选型决策表

| 维度 | MQ 延迟队列 | @Scheduled 扫表 |
|------|-----------|----------------|
| 触发精度 | **秒级精准** (每订单独立 timer) | 看 cron, 我们 1 小时一次 |
| 性能 | O(1) 每订单 | O(N) 每次扫全表 |
| 适用延迟 | 短 (秒~分钟级) | 长 (天级) |
| 业务规模 | 海量订单 | 状态批量推进 |
| 实现复杂度 | 中 (RabbitConfig + Listener + TTL 配置) | 低 (1 个方法 + cron 注解) |
| 用错代价 | 长延迟会堆百万消息在 MQ | 短延迟会被扫表性能拖垮 |

## 70.3  为啥 30 分钟关单选 MQ?

**业务要求**:
- 用户体验: 倒计时一到必须立刻关单 (前端会显示倒计时).
- 30 分钟差 5 秒, 用户看到的是 "倒计时到 0 了, 我还能付款" → 灾难.
- 海量订单: 双 11 期间 10 万订单同时倒计时, 不能扫表.

**MQ 延迟队列设计**:
- 下单时发一条"30 分钟后关单"的消息到 DLX 队列.
- TTL 一到, RabbitMQ 自动转发到主队列, Listener 消费.
- 每条订单一个独立 timer, 互不影响.
- 哪怕单消息处理慢, 别的订单照常关.

## 70.4  为啥 7 天签收选 @Scheduled?

**业务要求**:
- 7 天不是硬指标, 差 1 小时也行.
- 同时签收的数量不大 (7 天前发货的订单, 一小时内能扫完).
- 用户中间可能手动签收了, 也无所谓.

**用 MQ 反而坑**:
- 队列要堆 7 天的消息, MQ 内存占用大.
- 7 天后消费时, 订单可能早被用户手动签收 → 消费者还要做"无效消息过滤".
- 业务上太重.

**用 @Scheduled 简单**:
- 每小时扫一次 `SELECT * FROM orders WHERE status=2 AND ship_time <= NOW() - INTERVAL 7 DAY`.
- 命中的不多 (一般几十单, 不会成千上万).
- 改状态就行, 不存在"无效"问题, 因为 WHERE 子句已经过滤过.

## 70.5  错配的代价

### 错配 1: 7 天签收用 MQ

```
Day 0: 用户 A 发货, 发一条"7 天后签收"消息到 MQ DLX
Day 3: 用户 A 手动签收了 → status 改成 3
Day 7: MQ DLX 到期, 消费者拿到消息 → 看到 status=3 → 跳过
```

每条发货都要发延迟消息, MQ 队列堆 7 天的全国订单, **MQ 内存爆**.

### 错配 2: 30 分钟关单用 @Scheduled

```
每分钟扫: SELECT * FROM orders WHERE status=0 AND create_time <= NOW() - INTERVAL 30 MINUTE
```

- 10 万订单要扫 10 万行, 扫表时间可能 10+ 秒.
- 错过 1 分钟才关单, 用户能在倒计时 0 之后还能付款.
- 表越大越慢, 永远追不上.

## 70.6  万能选型法则 (记住这 3 句话)

1. **要精准 + 短延迟 → MQ 延迟队列**, 每事件独立 timer.
2. **不要精准 + 长延迟 → @Scheduled 扫表**, 批量推进状态.
3. **不要精准 + 短延迟 → @Scheduled (fixedDelay)**, 比 cron 简单.

**简历讲法**: "评估过两种方案. 30 分钟关单用 MQ 延迟队列 (倒计时业务要求秒级精准), 7 天签收用 @Scheduled (长延迟+批量推进, MQ 反而堆积). 关键看`延迟长短`和`精度要求`."

## 70.7  单体里这件事的真相

单体 `OrderTimeoutTask` 的 `@Scheduled` 是**注释掉的**:

```java
// @Scheduled(cron = "0 * * * * *")    // 每分钟整触发
public void closeTimeoutOrders() { ... }
```

为啥? **作者发现用 @Scheduled 做 30 分钟关单是错的选型**, 改用了 MQ 延迟队列. 但旧代码懒得删, 直接注释了.

我们微服务**没搬这个 task**, 因为:
1. 单体本身就没启用.
2. MQ 延迟队列已覆盖同样业务, 更精准.
3. 搬过来 = 两套兜底机制并存, 反而容易出 bug.

---

# 71. G6 代码全集 + 解释

## 71.1  改了哪些文件 (一图速览)

```
mini_mall DB:
  ALTER TABLE orders ADD COLUMN logistics_no, logistics_company

mini-mall-order/
├── entity/Orders.java                              ← G6.2 加 2 字段
├── dto/ShipOrderDTO.java                           ← G6.3 NEW
├── vo/OrderDetailVO.java                           ← G6.4a 加 2 字段
├── service/IOrdersService.java                     ← G6.3 加 ship/sign
├── service/impl/OrdersServiceImpl.java             ← G6.4b 加 ship/sign 实现
├── controller/OrdersController.java                ← G6.5 加 ship/sign 端点
├── task/LogisticsScheduledTask.java                ← G6.7 NEW
└── MiniMallOrderApplication.java                   ← G6.6 加 @EnableScheduling

mini-mall-common-core/exception/GlobalExceptionHandler.java   ← G6 修复 Seata 包装 bug
```

## 71.2  SQL DDL

```sql
USE mini_mall;

ALTER TABLE orders
  ADD COLUMN logistics_no VARCHAR(64) NULL COMMENT '物流单号(发货时填)',
  ADD COLUMN logistics_company VARCHAR(32) NULL COMMENT '物流公司(发货时填)';
```

讲解:
- `ALTER TABLE` = 改表结构 (不是改数据).
- `ADD COLUMN` 可一条 SQL 加多列, 逗号分隔.
- `VARCHAR(64)` 物流单号长度. 顺丰 13 位左右, 64 留余地.
- `NULL` 必须能空, 下单时没发货.
- `COMMENT` 存在元数据里, DBeaver / Navicat 能看到.

## 71.3  Orders entity 加字段

```java
/** 完成时间 (status=3 时填) */
private LocalDateTime finishTime;

// ─── G6 物流字段 ────
// 设计: 直接给 orders 表加 2 列 (而不是独立 logistics 表)
// 理由: 当前 N=1, 没必要拆表; 等真出现"多承运商/转运/跨境"再独立
// MP 映射: DB logistics_no → Java logisticsNo (yml 开了 map-underscore-to-camel-case)
/** 物流单号 (status=2 发货时填) */
private String logisticsNo;

/** 物流公司 (status=2 发货时填, 例: 顺丰/中通/京东) */
private String logisticsCompany;
```

**关键点**:
- 没加 `@TableField("logistics_no")`, 靠 yml 全局规则自动转 logisticsNo ↔ logistics_no.
- 字段插在 finishTime 后面 / remark 前面 — 业务时间线对齐 (付款→发货→完成→物流→备注).

## 71.4  ShipOrderDTO (NEW)

```java
@Data
public class ShipOrderDTO {

    @NotBlank(message = "物流公司不能为空")
    private String logisticsCompany;

    @NotBlank(message = "物流单号不能为空")
    private String logisticsNo;
}
```

**为啥要 DTO 不裸参数**:
- 2 个字段是阈值, 1 个字段裸参数, 2+ 才包 DTO.
- 加 @NotBlank 让 Spring Validation 帮校验, 比手写 if-else 干净.
- @NotBlank: 字段不能 null + 不能空串 + 不能全空格.
- ⚠ 配合 Controller 上的 `@Valid` 才生效, 没 @Valid = 摆设!

## 71.5  OrderDetailVO 加 2 字段

```java
@Data
public class OrderDetailVO {
    private Long orderId;
    // ... 现有字段省略 ...
    private LocalDateTime finishTime;
    private LocalDateTime createTime;
    // ─── G6 物流字段 (status>=2 时有值, 之前为 null) ────
    private String logisticsNo;
    private String logisticsCompany;
    private List<OrderItemVO> items;
}
```

**🎯 关键技巧 — BeanUtils 自动拷贝**:

OrderServiceImpl.getOrderDetail() 里有一行:
```java
BeanUtils.copyProperties(order, vo);
```

`BeanUtils.copyProperties` 按**字段名匹配**自动拷, entity 和 VO 字段名一样 → 自动包含进去, **不用再写代码**.

规则:
- entity 加 `logisticsNo` + VO 加同名 `logisticsNo` → 自动拷.
- 命名不一致 (entity 叫 `id`, VO 叫 `orderId`) → 手动 setter: `vo.setOrderId(order.getId())`.

## 71.6  IOrdersService 接口

```java
public interface IOrdersService extends IService<Orders> {
    // ... 现有方法省略 ...

    // ─── G6 物流: 状态机推进 ─────────────────────────────
    // 1 已付款 ──ship──> 2 已发货 ──sign──> 3 已完成

    /**
     * 发货 (admin/仓库系统调用, 无 userId)
     * 前置: status 必须 = 1 (PAID), 否则拒
     * 副作用: status 改 2, 填 shipTime + logisticsNo + logisticsCompany
     */
    void shipOrder(Long orderId, ShipOrderDTO dto);

    /**
     * 签收 (用户主动确认收货)
     * 前置: status 必须 = 2 (SHIPPED), 否则拒
     * 越权: orders.user_id 必须 = 入参 userId
     */
    void signOrder(Long userId, Long orderId);
}
```

## 71.7  OrdersServiceImpl 实现 (已在 68.4 节展示, 此处略)

详见 68.4 的 shipOrder + signOrder 完整代码.

## 71.8  OrdersController 加端点

```java
// G6.5 新增端点

@PutMapping("/{orderId}/ship")    // ⚠ 不要写 /order/{orderId}/ship, 类已经 @RequestMapping("/order")
public Result<Void> ship(
        @PathVariable Long orderId,
        @RequestBody @Valid ShipOrderDTO dto    // ← @Valid 触发 DTO 里的 @NotBlank
) {
    ordersService.shipOrder(orderId, dto);
    return Result.success();
}

@PutMapping("/{orderId}/sign")
public Result<Void> sign(
        @PathVariable Long orderId,
        @RequestHeader("X-User-Id") Long userId   // ← 不是 PathVariable! 从网关透传 header 拿
) {
    ordersService.signOrder(userId, orderId);
    return Result.success();
}
```

**容易踩的 3 个坑**:
1. ❌ 方法 @PutMapping 路径写成 `"/order/{orderId}/ship"` → 最终拼成 `/order/order/{orderId}/ship`. 类上已经 `@RequestMapping("/order")`.
2. ❌ userId 用 @PathVariable. 应该 `@RequestHeader("X-User-Id")`. 因为路径里没 userId, 是网关解 JWT 透传的 header.
3. ❌ 忘了 `@Valid`. DTO 上的 @NotBlank 形同虚设.

**为啥 PUT 不 POST**:
- POST = 创建新资源.
- PUT = 更新现有资源状态. ship / sign 是状态推进, 用 PUT 更符合 RESTful 语义.

## 71.9  启动类加 @EnableScheduling

```java
@SpringBootApplication
@ComponentScan("com.minimall")
@MapperScan("com.minimall.order.mapper")
@EnableFeignClients(basePackages = "com.minimall.order.client")
@EnableScheduling   // ⭐ G6 新增 — 不加 @Scheduled 全失效
public class MiniMallOrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(MiniMallOrderApplication.class, args);
    }
}
```

## 71.10  LogisticsScheduledTask (最炫的一段)

```java
@Component
public class LogisticsScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(LogisticsScheduledTask.class);
    private static final int AUTO_SIGN_DAYS = 7;

    @Autowired
    private OrdersMapper ordersMapper;

    /**
     * 每小时整点扫一次, 把 SHIPPED 且发货 >= 7 天的订单改 COMPLETED
     * cron "0 0 * * * ?" = 每小时整点
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void autoSignTimeoutOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(AUTO_SIGN_DAYS);
        log.info("[定时任务] 自动签收扫描开始, cutoff={}", cutoff);

        // LambdaQueryWrapper 链式写 WHERE, 类型安全
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .eq(Orders::getStatus, OrderStatus.SHIPPED)
                .le(Orders::getShipTime, cutoff);

        List<Orders> overdueOrders = ordersMapper.selectList(wrapper);

        if (overdueOrders.isEmpty()) {
            log.info("[定时任务] 无超时订单, 跳过");
            return;
        }

        // ⭐ 关键: 单个失败不能中断整体 — 批处理黄金法则
        int success = 0;
        for (Orders order : overdueOrders) {
            try {
                order.setStatus(OrderStatus.COMPLETED);
                order.setFinishTime(LocalDateTime.now());
                ordersMapper.updateById(order);
                success++;
                log.info("[定时任务] 自动签收 orderId={}", order.getId());
            } catch (Exception e) {
                // 一条脏数据不能卡死所有
                log.error("[定时任务] 自动签收失败 orderId={}", order.getId(), e);
            }
        }

        log.info("[定时任务] 完成, 总 {} 成功 {}", overdueOrders.size(), success);
    }
}
```

**逐行讲解**:

| 行 | 写法 | 为啥 |
|----|------|------|
| `@Component` | Spring 扫成 Bean | 不加 @Scheduled 不生效 |
| `@Scheduled(cron = "0 0 * * * ?")` | 每小时整点 | cron 6 字段 |
| `LocalDateTime.now().minusDays(7)` | Java 8 时间 API | **不可变** — 每次返回新实例 |
| `LambdaQueryWrapper<Orders>()` | MP 链式 WHERE | 类型安全, 方法引用 `Orders::getStatus` 编译期检查 |
| `.eq(getStatus, 2)` | = | WHERE status = 2 |
| `.le(getShipTime, cutoff)` | <= | WHERE ship_time <= ? |
| `try/catch 包单个订单` | 批处理黄金法则 | 一条脏数据不能拖垮全部 |

---

# 72. G6 踩坑实录 + 累计能力 + 待办

## 72.1  坑 ① Seata AOP 把 BusinessException 包成了 RuntimeException ⭐⭐⭐

### 症状

边界测试: 给已签收 (status=3) 的订单再调 `PUT /ship`, 期望返 `code=400 "订单状态不可发货"`, 实际返 `code=500 "系统繁忙"`.

### 排查

看 order 日志, 看到这个堆栈:

```
ERROR [系统异常]
java.lang.RuntimeException: try to proceed invocation error
    at io.seata.spring.annotation.AdapterInvocationWrapper.proceed(AdapterInvocationWrapper.java:59)
Caused by: com.minimall.common.core.exception.BusinessException: 订单状态不可发货
    at com.minimall.order.service.impl.OrdersServiceImpl.shipOrder(OrdersServiceImpl.java:485)
```

### 根因

Seata 全局事务的切面 (`AdapterInvocationWrapper`) 拦截了所有 Service 方法调用. 我们的 `BusinessException` 在 Service 抛出后, **被 Seata 切面捕获后重新包装成 `java.lang.RuntimeException("try to proceed invocation error")`**.

GlobalExceptionHandler 的匹配:
- `@ExceptionHandler(BusinessException.class)` ❌ 不匹配 (拿到的是 RuntimeException)
- `@ExceptionHandler(Exception.class)` ✅ 匹配 → 走 500 兜底

**所以业务异常变成了系统异常**.

### 为啥 cancelOrder / payOrder 没事?

它们内部用 `transactionTemplate.execute(lambda)`. BusinessException 在 lambda 内抛出, 被 lambda 的 return 拦截, transactionTemplate **重新抛出** — 这种情况 Seata 切面看到的就是原始 BusinessException, 不包装.

shipOrder / signOrder 是直接抛, 没经过 transactionTemplate, 被 Seata 切面直接抓住 + 包装.

### 修复

`GlobalExceptionHandler.handleException()` 在兜底前**递归解包 cause 链**, 找到原始 BusinessException 就按业务异常返:

```java
@ExceptionHandler(Exception.class)
public Result<Void> handleException(Exception e) {
    // G6 修复: Seata AOP 切面把 BusinessException 包装成 RuntimeException
    //   (堆栈见 io.seata.spring.annotation.AdapterInvocationWrapper.proceed)
    // 兜底前解包 cause 链, 找到原始 BusinessException 就按业务异常返
    Throwable cause = e;
    while (cause != null) {
        if (cause instanceof BusinessException be) {
            log.warn("[业务异常-解包] 错误码={}, 消息={}", be.getCode(), be.getMessage());
            return Result.error(be.getCode(), be.getMessage());
        }
        cause = cause.getCause();
    }
    log.error("[系统异常]", e);
    return Result.error(500, "系统繁忙，请稍后再试");
}
```

### 教训
- 引入 AOP / 代理框架 (Seata / Spring AOP / CGLIB) 都要警惕**异常被重新包装**.
- 全局异常处理器要做**好兜底**, 不能只信任直接类型匹配.
- 这个修复**自动惠及之前所有用 @GlobalTransactional 的方法**, 不只是 G6.

### 简历可讲法
"Seata 全局事务切面会包装业务异常导致 GlobalExceptionHandler 错走兜底分支. 通过给 handleException 加 cause 链解包逻辑修复, 一行 instanceof 模式匹配 (Java 17 语法) 解决了一类潜在 bug."

## 72.2  坑 ② Controller 路径前缀重复

### 症状

写新端点时, 习惯性写完整路径:
```java
@PutMapping("/order/{orderId}/ship")
```

启动后调 `PUT /order/11/ship` → 404 找不到.

### 根因

类上已经 `@RequestMapping("/order")`. Spring 拼接 → 实际路径变成 `/order/order/{orderId}/ship`. 调 `/order/11/ship` 自然找不到.

### 修复

方法上只写相对路径:
```java
@PutMapping("/{orderId}/ship")
```

### 教训
看一眼类上的 `@RequestMapping`, 方法上是**相对路径**.

## 72.3  坑 ③ @PathVariable / @RequestHeader 用错

### 症状

```java
public Result<Void> sign(@PathVariable Long useId, @PathVariable Long orderId) {
```

启动 → Spring 启动报错: "找不到路径参数 useId".

### 根因

- @PathVariable 要求 URL 路径里**有对应占位符**.
- URL `/{orderId}/sign` 只有 orderId, 没 useId → 找不到.
- userId 实际上是网关解 JWT 后塞到 HTTP header `X-User-Id` 的, 应该用 @RequestHeader.

### 修复

```java
public Result<Void> sign(
        @PathVariable Long orderId,
        @RequestHeader("X-User-Id") Long userId    // ← header 而不是 path
) {
```

### 教训
- 路径参数 (URL 里的 `{xxx}`) → @PathVariable
- HTTP header → @RequestHeader
- 请求 body JSON → @RequestBody
- 查询字符串 `?key=val` → @RequestParam

## 72.4  坑 ④ Java 8 path 干扰 (重复出现)

### 症状

启动 jar 报 exit 127 "command not found".

### 根因

Windows PATH 里 `C:\Program Files (x86)\Common Files\Oracle\Java\java8path\java.exe` 排在前面, 但这是 Java 8 (Spring Boot 3 要 17+). 而且这个目录有时候只有一个壳, 实际 java.exe 不存在.

### 修复

用绝对路径启动:
```powershell
Start-Process -FilePath "D:\jdk-21.0.11\bin\java.exe" -ArgumentList "-jar", "x.jar"
```

### 教训
- Windows 多 JDK 环境很常见, PATH 顺序坑大.
- 启动脚本里强制写绝对路径或先设 JAVA_HOME.
- Bash 子环境可能继承不到完整 PATH, 用 PowerShell 启动更可靠.

## 72.5  G6 端到端验证完整记录

```
0. 数据准备
   订单 id=11, user_id=1(alice), status=1(PAID), logistics 全空

1. Step1 PUT /order/11/ship  body={"logisticsCompany":"SF","logisticsNo":"SF1234567890"}
   → 200
   DB: status=2, logistics_no=SF1234567890, logistics_company=SF, ship_time=now

2. Step2 GET /order/11
   → status=2, logisticsNo=SF1234567890, logisticsCompany=SF, shipTime=2026-06-21T04:27:47

3. Step3 PUT /order/11/sign
   → 200
   DB: status=3, finish_time=now

4. Step4 GET /order/11
   → status=3, finishTime=2026-06-21T04:28:33

5. 边界 1: 重复 ship (此时 status=3)
   → 400 "订单状态不可发货"  ✅ 状态机拒绝

6. 边界 2: 重复 sign (此时 status=3)
   → 400 "订单状态不可签收"  ✅ 状态机拒绝
```

**6 步全过. 状态机 + GlobalExceptionHandler 解包 + Seata 兼容性全部 OK**.

## 72.6  G6 完成后能干啥

- ✅ 状态机的工程实现 (4 步前置校验模板)
- ✅ Spring @Scheduled 调度 + cron 表达式
- ✅ MQ 延迟队列 vs @Scheduled 选型 (业务核心能力)
- ✅ 单机调度的局限 + 3 种分布式调度方案对比
- ✅ Seata + 全局异常处理器的兼容性陷阱及修复方法
- ✅ BeanUtils.copyProperties 字段名匹配的自动拷贝能力
- ✅ DTO + @Valid + @NotBlank 校验闭环

## 72.7  G6 决策沉淀

| 决策 | 选什么 | 为啥 |
|------|--------|------|
| 物流表结构 | 加 2 列, 不独立表 | N=1, 符合 feedback_concrete_first |
| 物流查询 | 走详情接口, 不单独端点 | 业务能复用就复用, 别为对称造端点 |
| 7 天签收方案 | @Scheduled, 不 MQ | 长延迟 + 不精准 |
| 状态机 | 4 步前置校验, 不 Spring StateMachine | 状态规模小不需要 |
| 单机调度 | 暂用单机, 注释 TODO | 当前 1 实例没必要分布式 |
| BusinessException 解包 | 兜底 handler 递归 cause | 通用方案, 自动惠及所有 @GlobalTransactional |

## 72.8  G6 没做但已铺路的 (后续阶段补)

| 待办 | 啥时候做 | 怎么做 |
|------|---------|--------|
| admin 网关 + RBAC | G10 后台 | shipOrder 端点应该挂 admin 后面 + 角色鉴权 |
| 分布式锁定时任务 | order 多实例时 | LogisticsScheduledTask 第一步 SETNX Redis 锁 |
| 物流轨迹查询 | 真接物流公司 | 接顺丰/中通 API 拉轨迹, 现在物流公司只是字符串 |
| 用户端倒计时 UI | 前端 | shipTime 给前端, 计算 "还剩 X 天自动签收" |

## 72.9  累计 G6 关键文件

```
DB:
  orders.logistics_no  VARCHAR(64) NULL
  orders.logistics_company VARCHAR(32) NULL

新建文件:
  mini-mall-order/src/main/java/com/minimall/order/dto/ShipOrderDTO.java
  mini-mall-order/src/main/java/com/minimall/order/task/LogisticsScheduledTask.java

修改文件:
  mini-mall-order/.../entity/Orders.java                  (+2 字段)
  mini-mall-order/.../vo/OrderDetailVO.java               (+2 字段)
  mini-mall-order/.../service/IOrdersService.java         (+2 方法)
  mini-mall-order/.../service/impl/OrdersServiceImpl.java (+2 方法实现)
  mini-mall-order/.../controller/OrdersController.java    (+2 端点)
  mini-mall-order/.../MiniMallOrderApplication.java       (+@EnableScheduling)
  mini-mall-common-core/.../exception/GlobalExceptionHandler.java (cause 链解包)
```

---

**G6 完毕**. 微服务从 "下单/付款/取消" 走到 "**完整下单全链路 + 状态机 + 定时任务**". 下一步可选: G7 评价, G8 优惠券, G9 ES 商品搜索, G10 后台管理.

---

# 73. G7 商品评价 (mini-mall-review, 9004)

> 单体 mini-mall 没做评价, 微服务版【独有】. 这是第一次"无单体参考"完全从零写的服务.

## 73.1  G7 目标 & 设计决策

**业务目标**: 用户对【已完成订单】里买过的商品打分 + 写评论, 商品页显示平均分.

**架构决策**:

| 决策点 | 选择 | 原因 |
|---|---|---|
| 单独建 review 服务 | ✅ 是 | 评价业务边界清晰, 数据/规则都独立 |
| 端口 9004 | 9004 | 紧接 9003 order, 顺序好记 |
| `@GlobalTransactional` Seata | ✗ **不用** | 评价不涉及钱, 最终一致即可, 别上重锤 |
| RabbitMQ 异步 | ✗ **不用** | 评价是同步操作, 无削峰需求 |
| Redis | ✗ **不用** (review 侧) | review 主要写, 缓存命中率为 0; product 侧已有 |
| 评分回写方式 | ✅ Feign 调 product.refreshRating | 简单清晰, product 算 AVG 写 product.avg_rating |
| 评分聚合存哪 | ✅ product 表加 2 列 (avg_rating/review_count) | 空间换时间, 避免详情页每次实时 AVG/COUNT |
| product 怎么算 AVG | ✅ product 服务直读 reviews 表 | 共库阶段可行; 长远走事件驱动 |

**端口分配**:
```
9000 = (空)              9001 = user          9002 = product
9003 = order             9004 = review        9080 = gateway
8848 = Nacos             8091 = Seata
Sentinel 客户端口:
  8719=user 8720=product 8721=gateway 8722=order 8723=review
```

## 73.2  G7.1 SQL DDL (踩坑: 存储过程报错)

新建 `sql/g7-reviews.sql`:

```sql
-- product 加 2 列 (评分聚合)
ALTER TABLE `product`
    ADD COLUMN `avg_rating`   DECIMAL(2,1) NOT NULL DEFAULT 0.0,
    ADD COLUMN `review_count` INT          NOT NULL DEFAULT 0;

-- reviews 表
CREATE TABLE IF NOT EXISTS `reviews` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `order_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `rating` TINYINT NOT NULL,
  `content` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_product` (`order_id`, `product_id`),  -- ★ 兜底防重复评价
  KEY `idx_product_id` (`product_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价表';
```

### 设计要点

- **DECIMAL(2,1)** 不用 FLOAT: 钱/分数类要精确, FLOAT 有舍入误差
- **UNIQUE KEY (order_id, product_id)**: 应用层 + 数据库双保险防重复评价
- **TINYINT rating**: 1-5 星, byte 足够

### 第一版存储过程踩坑

最早写了 `add_column_if_not_exists` 存储过程支持幂等执行, 用双引号包含单引号:
```sql
CALL add_column_if_not_exists('product', 'avg_rating', "DECIMAL(2,1) ... '...'");
```
报错: `ERROR 1064 (42000) at line 39: You have an error in your SQL syntax`
- 根因: 双引号 + 内嵌单引号在 MySQL 解析器里语义不明确
- 修复: 直接 ALTER TABLE, 放弃幂等性, 一次性脚本

## 73.3  G7.2~G7.6 模块骨架 + 业务实现

### 模块结构

```
mini-mall-review/
├── pom.xml                    最简依赖, 砍掉 MQ/Seata/Redis
├── src/main/java/com/minimall/review/
│   ├── MiniMallReviewApplication.java   @ComponentScan("com.minimall") 才能扫到 common 包
│   ├── client/
│   │   ├── OrdersFeignClient.java       调 order 校验订单
│   │   ├── ProductFeignClient.java      调 product 回写评分
│   │   └── fallback/                    2 个降级实现
│   ├── controller/ReviewController.java  3 个端点
│   ├── dto/CreateReviewDTO.java          @NotNull/@Min/@Max/@Size 校验
│   ├── entity/Reviews.java               @TableLogic 软删
│   ├── mapper/ReviewsMapper.java         extends BaseMapper
│   ├── service/IReviewsService.java
│   ├── service/impl/ReviewsServiceImpl.java   ★ 核心: 4 步校验 + 落库 + Feign 回写
│   └── vo/ReviewVO.java                  @JsonFormat 格式化 createTime
└── src/main/resources/application.yml    9004 端口
```

### 3 个端点

```
POST /review                     创建评价 (需登录, @Valid 拦参数)
GET  /review/product/{productId} 商品评价列表 (公开, 网关白名单)
GET  /review/user                我的评价 (需登录)
```

## 73.4  ⭐ 业务规则编排 - 4 步校验链

`ReviewsServiceImpl.createReview` 是 G7 教学核心:

```
Step 1: Feign 调 order.getOrderDetail → 校验 code != 200 抛业务异常
Step 2: 校验 order.status == 3 (COMPLETED), 否则"只有已完成订单可评价"
Step 3: 校验 dto.productId 在 order.items 里, 否则"该商品不在订单里"
Step 4: 校验 SELECT COUNT(*) FROM reviews 没记录, 否则"已评价过"
Step 5: INSERT reviews
Step 6: registerSynchronization afterCommit → Feign 调 product.refreshRating
```

### 教学要点 - "前置 vs 后置" Feign Fallback 判断

| 场景 | Fallback 返什么 | 原因 |
|---|---|---|
| Step 1 前置校验 - order 挂了 | `Result.error(503)` | 校验过不去, 不能让评价继续 |
| Step 6 后置补偿 - product 挂了 | `Result.success(null)` | 主流程已成, 不能因副作用回滚 |

口诀: **前置校验/关键扣减 → error 拦; 后置补偿/非关键回写 → success log 走**.

## 73.5  ⭐⭐ G7.E2E 踩的最关键的坑 - 跨服务事务可见性

### 症状

第一次端到端测试:
- 评价 INSERT 成功 (`reviews` 表有 1 条 rating=5)
- 商品详情查出 `avgRating=0.0, reviewCount=0` ← **没回写**
- product 日志: `[refreshRating] productId=1 avg=0 count=0 缓存已删` ← **product 查 reviews 时数据为空**

### 根因 (时序图)

```
[原版有 bug 的代码]                       [问题时序]
                                          
@Transactional                            T0  review 事务开始
public Long createReview(...) {           T1  INSERT reviews (未提交, 在事务里)
    ...                                   T2  Feign 调 product.refreshRating
    reviewsMapper.insert(r);              T3  product 新开事务查 reviews
    productFeignClient                    T4  product 看不到 review 未提交数据
        .refreshRating(productId);  ←——        AVG(rating)=null, COUNT=0
    return r.getId();                     T5  product UPDATE avg_rating=0 (错!)
}                                         T6  review 事务 commit
                                          T7  reviews 表里【现在】有 1 条
                                              但 product.avg_rating【已经】被刷成 0 了
```

### 修复 - TransactionSynchronizationManager.afterCommit

```java
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Step 6 改成:
Long pid = dto.getProductId();
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        productFeignClient.refreshRating(pid);
    }
});
```

修复后时序:
```
T0  review 事务开始
T1  INSERT reviews
T2  注册 afterCommit 回调 (但不执行)
T3  review 事务 commit ✓     ← reviews 表里有了
T4  afterCommit 触发, 调 product.refreshRating
T5  product 查 reviews 看到新数据
T6  AVG=5.0 COUNT=1 → UPDATE product.avg_rating=5.0 ✓
```

### 教训 (面试金句)

**"事务里发外部消息/Feign/邮件" → 永远要 afterCommit**

不只本案. 凡是:
- 事务里发 MQ → 消费方拿到 ID 去查 DB, 可能查空
- 事务里发邮件 → 邮件内容引用了未提交的 ID
- 事务里写 Redis → 业务回滚但 Redis 没回滚

都得 afterCommit. Spring 提供 3 种姿势:
1. `TransactionSynchronizationManager.registerSynchronization(...)` (本案用法)
2. `@TransactionalEventListener(phase = AFTER_COMMIT)`
3. 用 `TransactionTemplate.execute()` 把事务边界缩到 INSERT 那一行

## 73.6  Cache Aside 写策略 (顺手实现)

G7.7 写 `ProductServiceImpl.refreshRating` 时, 顺手实现了 Cache Aside 写:

```java
public void refreshRating(Long productId) {
    // 1. 算 AVG + COUNT
    Map<String, Object> stats = reviewsMapper.selectStats(productId);
    BigDecimal avg = ...;
    Integer count = ...;
    
    // 2. UPDATE product
    Product p = new Product();
    p.setId(productId);
    p.setAvgRating(avg);
    p.setReviewCount(count);
    productMapper.updateById(p);
    
    // 3. DEL Redis 缓存 (Cache Aside 写策略)
    //    为啥是 DEL 不是 SET?
    //    SET 引入双写一致性: A 写 5 星 B 写 3 星, 缓存可能停留在错的值
    //    DEL 让下次读重查 DB → 总是最新, 也最简单不容易错
    redisTemplate.delete("product:detail:" + productId);
}
```

下次 GET /product/1 → 缓存未命中 → 查 MySQL (新 avg=5.0) → 回填 Redis. 端到端 5 端口验证全过.

## 73.7  G7 完整端到端验证记录

| 测试 | 期望 | 实际 |
|---|---|---|
| 创建评价 (合规) | 成功 + 评分回写 | data=2, product.avg=5.0 ✓ |
| 查商品评价列表 (公开) | 返列表 | 1 条 5 星 ✓ |
| 查我的评价 | 返列表 | 1 条 ✓ |
| 重复评价 | 被拒 | "该商品已评价过, 请勿重复" ✓ |
| 用 PAID(1) 订单评价 | 被拒 | "只有已完成订单可评价" ✓ |
| rating=10 超范围 | 400 | "rating 评分最多 5 星" ✓ |

## 73.8  常见踩坑速查

| 坑 | 表现 | 原因 | 修 |
|---|---|---|---|
| **跨事务可见性** | refreshRating 把 avg 刷成 0 | T2 Feign 早于 T6 commit | afterCommit 回调 |
| Java 8 PATH 干扰 | java -jar 启不动 SB3 | which java → Java 8 | 用 D:\jdk-21.0.11\bin\java |
| Git Bash 中文 JSON | 500 Invalid UTF-8 0xba | GBK→UTF-8 转换错 | 测试用英文 content |
| 网关白名单 startsWith 匹配 | POST /review 也被白名单放 | "/review/product" 前缀也匹配不到 POST /review | 路径分隔够清晰即可 |
| product entity 缺字段 | UPDATE 不动 avg_rating | 加了 SQL 列没同步 Java | Product.java 同步加字段 |

## 73.9  N=3 决策回顾 - 为啥还没抽 common-redis

按 `feedback_concrete_first` 严格 N=3 阈值:

| 服务 | 用 Redis? |
|---|---|
| order | ✅ (G1.3 RedisConfig) |
| product | ✅ (G3.5.3 RedisConfig) |
| review | ❌ 暂无缓存需求 |

**N=2, 暂不抽**. 等 review 真出现"热门商品评价列表"等缓存场景时 (那时 N=3), 再抽 common-redis 才自然.

## 73.10  G7 累计文件

```
SQL:
  sql/g7-reviews.sql              新建表 + product 加 2 列
  sql/schema.sql                  全量 schema 同步加 G6 G7 字段

新增模块:
  mini-mall-review/               整个新服务 (9004)

修改:
  父 pom + module 列表加 mini-mall-review
  mini-mall-gateway/.../application.yml          + review-route
  mini-mall-gateway/.../AuthGlobalFilter.java    + "/review/product" 白名单
  mini-mall-product/.../entity/Product.java      + avgRating + reviewCount
  mini-mall-product/.../service/IProductService.java + impl + refreshRating
  mini-mall-product/.../mapper/ReviewsMapper.java    新建 (只为 SELECT stats)
  mini-mall-product/.../controller/ProductController.java + internal/refresh-rating
```

## 73.11  G7 教学速查 - 4 个零散知识点补全

> 这一节把 G7 涉及但前面章节没专题讲的 4 个 API 一次性钉死, 复习只看这里.

### ① Spring `@Transactional` 边界

```java
@Transactional(rollbackFor = Exception.class)
public Long createReview(...) {
    // ① 方法进入 → Spring AOP 开启事务 (TransactionInterceptor.invoke)
    
    reviewsMapper.insert(r);     // ← 此时是【未提交】, 同库其他事务看不到
    
    return r.getId();            // ② 方法返回 → commit 触发
                                  //    抛异常返回 → rollback 触发
}
```

**边界 = 方法的进入 + 返回点**. 这就是为啥 G7.5 第一版会出 bug:
- INSERT 在事务内 = 未提交
- 同方法里 Feign 调下游 = 仍在事务内
- 下游查 reviews 表 = 在新事务里查 = 看不到未提交数据

**两类"事务边界"陷阱**:
1. **自调用失效**: `this.methodA()` 调用同类的 `@Transactional methodB()` → 不走代理 → 事务注解失效
2. **方法返回前发外部消息**: 就是 G7.5 的坑, 用 afterCommit 修

### ② Bean Validation 全链路 (JSR-380)

```
[前端 POST /review]
    {"orderId":11,"productId":1,"rating":10}
            ↓
[Spring MVC 反序列化]
    JSON → CreateReviewDTO 对象
            ↓
[@Valid 触发校验] ← Controller 方法签名上写了 @Valid 才会跑
    @NotNull / @Min(1) / @Max(5) / @Size(max=500)
            ↓ 失败
    抛 MethodArgumentNotValidException
            ↓
[GlobalExceptionHandler 兜底]
    @ExceptionHandler(MethodArgumentNotValidException.class)
    解析所有 fieldErrors → 拼 "rating 评分最多 5 星"
    返 Result.error(400, ...)
            ↓
[前端拿到]
    {"code":400, "message":"rating 评分最多 5 星"}
```

**3 步开关**:
- Step 1: DTO 字段上加注解 (`@NotNull` 等)
- Step 2: Controller 方法签名加 `@Valid` (没这个注解再多 `@NotNull` 也不触发)
- Step 3: 全局有 `GlobalExceptionHandler` 拦 `MethodArgumentNotValidException`

**踩坑**: 漏第 2 步是新手最常见的"DTO 校验不生效", 自己以为加了 `@NotNull` 就够.

### ③ `LambdaQueryWrapper` 方法引用

```java
// 老写法 - 字符串列名, 改 entity 字段名编译器抓不到错
new QueryWrapper<Reviews>().eq("order_id", 123L)
                            ↑ 写错 "oder_id" 运行时才炸

// 新写法 - lambda 方法引用, 改字段名编译报红
new LambdaQueryWrapper<Reviews>().eq(Reviews::getOrderId, 123L)
                                  ↑ Reviews 类没这 getter 直接编译报错
```

**MP 怎么从 getter 反推列名?**
- `Reviews::getOrderId` → 反射拿方法名 `getOrderId` → 去掉 `get` → `orderId` 
- 配 yml 的 `map-underscore-to-camel-case: true` → 转 `order_id`

**常用方法对照 SQL**:

| Wrapper 方法 | 对应 SQL |
|---|---|
| `.eq(field, val)` | `field = ?` |
| `.ne(field, val)` | `field != ?` |
| `.gt/.lt/.ge/.le(field, val)` | `> < >= <=` |
| `.like(field, val)` | `LIKE '%?%'` |
| `.in(field, list)` | `IN (?, ?)` |
| `.between(field, a, b)` | `BETWEEN ? AND ?` |
| `.isNull(field)` | `IS NULL` |
| `.orderByDesc(field)` | `ORDER BY field DESC` |
| `.groupBy(field)` | `GROUP BY field` |
| `.last("LIMIT 10")` | 拼裸 SQL 后缀 (慎用) |

链式调用自动 AND 连接, `.or()` 改 OR.

### ④ `BeanUtils.copyProperties` 同名字段拷贝

```java
private ReviewVO toVO(Reviews r) {
    ReviewVO vo = new ReviewVO();
    BeanUtils.copyProperties(r, vo);   // src → target
    return vo;
}
```

**规则**:
- **同名同类型**字段自动拷过去 (id/userId/orderId/productId/rating/content/createTime)
- 源没有的字段 (VO 的 `username`) → 目标保持 null
- 类型不兼容 (`Long` → `String`) → 不拷贝, 不报错 (悄无声息踩坑点!)

**为啥要拷不直接返 Entity?**
- Entity 是数据库的"投影", 跟前端无关
- 返 Entity → `isDeleted` 字段会暴露给前端
- 返 Entity → 数据库改字段 = 前端契约破坏

**坑预警**: 别拷贝**深结构**.
```java
class A { List<B> bs; }
BeanUtils.copyProperties(srcA, tgtA);
// tgtA.bs 跟 srcA.bs 指向【同一个 List】, 改一个动俩
```
真要深拷贝用 `MapStruct` 或手动 `new ArrayList<>(src.getBs())`.

---

**G7 完毕**. 微服务从 "完整下单" 走到 "**带评价闭环 + Cache Aside 写策略 + 跨服务事务可见性踩坑**". 下一步可选: G8 优惠券, G9 ES 商品搜索, G10 后台管理, 或回头补 C1 README + C2 GitHub 改名.

---

# 74. G8 优惠券 (跨服务用券抵扣)

## 74.1  G8 目标 & 设计决策

**业务目标**: 用户领满减券 → 下单选券抵扣 → 取消订单券能退回.

**核心架构决策**:

| 决策点 | 选择 | 原因 |
|---|---|---|
| 放哪个服务 | ✅ user 服务 | "我的优惠券"在用户中心自然; N=1 不抽独立 coupon 服务 |
| 券类型 | ✅ 只满减 (G8) | 折扣留扩展, 教学聚焦 |
| 用 Seata? | order 已经在用 (G5) | 涉及钱必须强一致 |
| user 也接 Seata? | ❌ 教学省略 | **代价: useCoupon 不能自动回滚, 留下脏数据可能** |
| 取消订单退券 | ✅ 退 | 用户体验; 用 afterCommit 套路 (G7 学过) |
| 数据模型 | coupon(模板) + user_coupon(具体) | UNIQUE KEY(user_id, coupon_id) 防重复领 |

**关键 SQL 设计**:
- `orders.user_coupon_id` (不是 coupon_id): 退回时直接定位用户那张券
- `orders.discount_amount`: 抵扣金额【快照】, 防 coupon 模板被改影响历史订单
- `coupon.remain_stock`: 原子 CAS 扣减防超发

## 74.2  G8 核心业务流

### 领券 (4 步)

```
Step 1: 校验券模板 (存在 + 上架 + 在有效期)
Step 2: 校验该用户没领过 (应用层第一道)
Step 3: ⭐ CAS 扣库存: UPDATE coupon SET remain=remain-1 WHERE remain>0
Step 4: INSERT user_coupon (DB UNIQUE KEY 兜底)
```

### 下单用券 (5.5 步插在 createOrder 里)

```
... [前面 1-5 步算 originalAmount] ...

save(order)  ← 拿到 orderId

if dto.userCouponId != null:
    Feign userFeignClient.useCoupon(userId, ucId, originalAmount, orderId)
    ↓ user 服务那边:
       1. 校验 user_coupon 存在 + status=0
       2. 越权: user_id 匹配
       3. 校验在有效期
       4. orderAmount >= threshold
       5. ⭐ CAS UPDATE user_coupon SET status=1 WHERE status=0  (乐观锁)
       返 discountAmount
    
    order.setTotalAmount(originalAmount - discount)
    order.setUserCouponId(ucId)
    order.setDiscountAmount(discount)
    updateById(order)

... [后面 6-8 步扣库存/MQ] ...
```

### 取消订单退券 (用 G7 学的 afterCommit)

```
@TransactionalExecute / @Transactional 包住:
    UPDATE orders SET status=4
    回库存 (Feign 调 product)
    
    if order.userCouponId != null:
        registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                userFeignClient.refundCoupon(ucId);
            }
        });
```

**afterCommit 第二次复用** — 跟 G7 review.refreshRating 同套路, 学一次记一辈子.

## 74.3  ⭐ 教学要点 - 3 处 CAS 思想

G8 写了 3 处 "UPDATE ... WHERE 条件" 实现乐观锁:

| 位置 | SQL | 防什么 |
|---|---|---|
| `coupon.deductRemainStock` | `UPDATE coupon SET remain=remain-1 WHERE remain>0` | 超发 (两人同抢最后 1 张) |
| `user_coupon.useCoupon` | `UPDATE SET status=1 WHERE status=0` | 同张券被两笔订单都用 |
| (G3.10) `product.deductStock` | `UPDATE SET stock=stock-? WHERE stock>=?` | 库存超卖 |

口诀: **"高并发安全 = WHERE 加状态条件 + 看 rows==1"**.

## 74.4  ⭐⭐ 教学限制 - user 没接 Seata 的脏数据风险

### 风险场景

```
T0  order: save 订单 (Seata XID 分支事务)
T1  order: Feign useCoupon → user 服务 UPDATE user_coupon (本地事务, commit ✓)
T2  order: 第 7 步扣库存失败 (product 没货)
T3  @GlobalTransactional 回滚:
      - order 库的 INSERT 通过 undo_log 撤回 ✓
      - product 库已扣的 stock 通过 undo_log 撤回 ✓
      - ❌ user 库的 user_coupon UPDATE 【不在 Seata 管辖】, 不回滚
T4  最终: 订单不存在了, 但用户的券【已被错扣】
```

### 解法

1. **生产解法**: user 服务也接 Seata Server, 上 undo_log 表 + Seata 依赖. 让 useCoupon 自动加入分支事务.
2. **教学接受**: 当前 user 没接, 留个 "已知脏数据风险" 监控告警. 真出现时用后台 job 比对 user_coupon vs orders 关联做修复.

### 关键认知

跨服务事务【一致性】不是 "用了 Feign 就有", 是 "**所有参与方都接同一套 TC**" 才有.
G5 时 order + product 接了, G8 没把 user 拉进来 = 这条链路不强一致, 是个 trade-off, 不是 bug.

## 74.5  G8 端到端验证记录 (7 项全过)

| 测试 | 期望 | 实际 |
|---|---|---|
| 列可领券 (公开) | 2 张 | ✅ |
| 领券 → DB | user_coupon 新行 + remain 100→99 | ✅ |
| 我的券 + expired 标志 | 显示模板信息 + 是否过期 | ✅ |
| ⭐ 下单抵扣 | 5999 → 5989 (减 10), status=1 | ✅ |
| ⭐ 取消订单退券 | status 1→0, order_id NULL | ✅ |
| 回库存 | product.stock 还原 | ✅ |
| 重复领券拦截 | "您已领取过该券" | ✅ |

## 74.6  G8 踩的小坑速查

| 坑 | 表现 | 原因 | 修 |
|---|---|---|---|
| Windows mysql cli 中文 INSERT | `ERROR 1366 Incorrect string value '\xA1100'` | mysql cli 默认 GBK | 加 `--default-character-set=utf8mb4` + `SET NAMES utf8mb4` |
| 网关 404 找不到 /coupon/** | 加了路由还是 404 | gateway 没重启 jar | 改 yml = 必须 jar 重打+重启 (yml 打进 jar) |
| Git Bash 中文 JSON body | `Invalid UTF-8 0xba` | curl 命令行 GBK→UTF-8 转换错 | 测试用英文 content |
| 列名编译期对不上 | 编译过运行 Where 不命中 | 用 QueryWrapper 字符串 | 改 LambdaQueryWrapper + 方法引用 |

## 74.7  G8 教学速查 - 新出现的 4 个 API

### ① `@Update` + `#{}` 原子 SQL

```java
@Update("UPDATE coupon SET remain_stock=remain_stock-1 WHERE id=#{id} AND remain_stock>0")
int deductRemainStock(@Param("id") Long id);
```
返 `int` = 受影响行数, 0 表示失败.

**口诀**: 写 SQL 用 `#{}` 不用 `${}` ($是字符串拼接, SQL 注入风险).

### ② `LambdaUpdateWrapper` CAS 写法

```java
mapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
    .eq(UserCoupon::getId, id)
    .eq(UserCoupon::getStatus, 0)        // ⭐ WHERE 加状态条件
    .set(UserCoupon::getStatus, 1));
```
对应 SQL `UPDATE ... SET status=1 WHERE id=? AND status=0`. 并发安全.

### ③ N+1 查询 vs `selectBatchIds`

```java
// ❌ 错: 用户有 N 张券就 N 次 SELECT
ucs.forEach(uc -> couponMapper.selectById(uc.getCouponId()));

// ✅ 对: 1 次 IN 查询
List<Long> ids = ucs.stream().map(UserCoupon::getCouponId).distinct().toList();
Map<Long, Coupon> map = couponMapper.selectBatchIds(ids).stream()
    .collect(Collectors.toMap(Coupon::getId, c -> c));
```

### ④ afterCommit 套路 - 第 2 次复用

跟 G7 review.refreshRating 完全一样的写法:
```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override public void afterCommit() {
        userFeignClient.refundCoupon(ucId);
    }
});
```

**用 G7 的笔记 73.5 完整理论**, G8 只是套用.

## 74.8  G8 累计文件

```
SQL:
  sql/g8-coupons.sql          (新建: orders 加 2 列 + coupon/user_coupon 新表 + 2 张测试券)
  sql/schema.sql              (同步全量)

user 服务新增:
  entity/Coupon.java
  entity/UserCoupon.java
  mapper/CouponMapper.java         (+2 原子 SQL deduct/restoreRemainStock)
  mapper/UserCouponMapper.java
  service/ICouponService.java
  service/impl/CouponServiceImpl.java   (~250 行核心)
  controller/CouponController.java      (6 端点)
  dto/UseCouponDTO.java
  vo/UserCouponVO.java

order 服务改:
  entity/Orders.java          (+userCouponId +discountAmount)
  dto/CreateOrderDTO.java     (+userCouponId)
  dto/UseCouponDTO.java       (新建, 跟 user 那边字段对齐)
  client/UserFeignClient.java (+useCoupon/refundCoupon)
  client/fallback/UserFeignClientFallback.java  (+2 fallback)
  service/impl/OrdersServiceImpl.java
    createOrder       (插入 5.5 步用券逻辑)
    cancelOrder       (afterCommit refundCoupon)
    closeOrderByMQ    (afterCommit refundCoupon)

网关:
  application.yml          (+coupon-route)
  AuthGlobalFilter.java    (+"/coupon/available" 白名单)
```

---

**G8 完毕**. 微服务从 "评价闭环" 走到 "**完整电商核心: 用户/商品/订单/库存/物流/评价/优惠券 + 跨服务事务 + CAS 乐观锁**". 后续可选: G9 ES 商品搜索, G10 后台管理, 或 C1 README + C2 GitHub 改名收尾.


# 75. G9 - ES 商品搜索服务 (mini-mall-search)

> 端口 9005, 服务名 `mini-mall-search`. 从 0 建一个新微服务模块, 把 product 数据灌进 ES 给前端搜.

## 75.0 总览 - G9 干了啥

```
┌────────────────────────────────────────────────────────────┐
│                       前端浏览器                            │
│           GET /search/product?keyword=华为                  │
└────────────────────┬───────────────────────────────────────┘
                     ▼
            ┌────────────────┐
            │  网关 :9000    │  lb:// → Nacos 负载均衡
            │  Path=/search/**│
            └───────┬────────┘
                    ▼
      ┌────────────────────────────────┐
      │  mini-mall-search :9005        │ ← G9 主角
      │  Controller → Service → 2 出口 │
      └─┬──────────────────────────┬───┘
        │                          │
   Feign 调 product           Spring Data ES
        ▼                          ▼
  product :9002               ES :9200
  /internal/all               索引 mini_mall_product
```

**数据流向 (全量同步)**:
```
MySQL → product JSON → 网络 → Jackson 反序列化 ProductSource
  → .map(ProductDocument::from) → repository.saveAll() → ES
```

**最大收获**:
1. **微服务铁律** — 服务间**不共享 jar**, 各自写 DTO 副本 (ProductSource), Jackson 按字段名匹配反序列化
2. **静态工厂方法 from()** — 在目标类里写转换, `stream().map(类::from)` 优雅链式转换
3. **Spring Data ES = 'ES 版的 MyBatis-Plus'** — 1 行接口自动获得 20+ CRUD 方法
4. **BoolQuery 灵魂** — `must` 打分(关键词搜) vs `filter` 不打分有缓存(精确过滤)
5. **改代码必须重新打包** — IDE 改的是源码, fat jar 不会自动同步 (G9 真实排坑)

## 75.1 mini-mall-search 模块脚手架

### 75.1.1 pom.xml (4 个依赖 + maven plugin)

```xml
<parent>
    <groupId>com.minimall</groupId>
    <artifactId>mini-mall-cloud</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</parent>
<artifactId>mini-mall-search</artifactId>

<dependencies>
    <!-- Result/GlobalExceptionHandler -->
    <dependency>
        <groupId>com.minimall</groupId>
        <artifactId>mini-mall-common-core</artifactId>
    </dependency>
    <!-- 对外 HTTP -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <!-- ⭐ ES 8.x 客户端 + Spring Data Repository -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
    </dependency>
    <!-- 跨服务 Feign -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-openfeign</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

**知识点**:
- ⭐ `spring-boot-starter-data-elasticsearch` 内部用 **ES 8.x 新版 Java API Client** (`co.elastic.clients`), 不再用老的 RestHighLevelClient
- `<optional>true</optional>` 让 lombok 只编译期可见, 不传递

### 75.1.2 父 pom 解开模块注释

```xml
<modules>
    ...
    <module>mini-mall-search</module>   <!-- 解开这一行 -->
</modules>
```

**坑**: 不加这行 `mvn install` 不会编译这个子模块, IDE 能识别但命令行编译跳过.

### 75.1.3 application.yml

```yaml
server:
  port: 9005

spring:
  application:
    name: mini-mall-search
  elasticsearch:
    uris: http://127.0.0.1:9200
    connection-timeout: 1s
    socket-timeout: 30s
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
    sentinel:
      transport:
        dashboard: 127.0.0.1:8858
        port: 8724      # ⭐ 跟 product/order/user 错开
```

### 75.1.4 启动类

```java
@SpringBootApplication
@EnableFeignClients
@ComponentScan("com.minimall")    // ⭐ 扩大扫描范围, 扫到 common-core
public class MiniMallSearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(MiniMallSearchApplication.class, args);
        System.out.println("=========== mini-mall-search 已启动 :9005 ===========");
    }
}
```

**坑**: IDE 自动 import 可能给你 `import javax.swing.*`, 看到 `javax.swing` 立马删. ⚠

## 75.2 ProductDocument - ES 索引映射类

### 75.2.1 ES 核心概念速查

| 概念 | 含义 | 类比 MySQL |
|---|---|---|
| Document | 一条 JSON 数据 | 一行 row |
| Index | 同类文档集合 | 一张 table |
| 倒排索引 | "词 → 文档列表" 反向映射 | (相当于全文索引但更强) |
| 分词器 Analyzer | 把 Text 字段拆成词 token | (MySQL 没有) |

### 75.2.2 ⭐ Field Type 选择 (G9 最重要知识点)

| FieldType | 用途 | 是否分词 | 用例 |
|---|---|---|---|
| **Text** | 全文搜索 | ✅ 分词 | name/description/detail |
| **Keyword** | 精确匹配 | ❌ 不分词 | tag/code/URL/枚举字符串 |
| **Long** | 整数主键/ID | — | id/categoryId |
| **Integer** | 小整数 | — | stock/sales/reviewCount |
| **Double** | 小数 (BigDecimal 也用它) | — | price/avgRating |
| **Date** | 时间 | — | createTime/updateTime |

**口诀**: '要搜索的文本用 Text, 要精确匹配的字符串用 Keyword, 数值就用对应数值类型.'

### 75.2.3 ProductDocument.java (含静态工厂方法)

```java
@Data
@Document(indexName = "mini_mall_product")
public class ProductDocument {

    @Id                                  // ES 的 _id 主键
    private Long id;

    @Field(type = FieldType.Text)        // 要搜, 分词
    private String name;
    @Field(type = FieldType.Long)
    private Long categoryId;
    @Field(type = FieldType.Text)
    private String description;
    @Field(type = FieldType.Text)
    private String detail;
    @Field(type = FieldType.Double)      // BigDecimal 用 Double 映射
    private BigDecimal price;
    @Field(type = FieldType.Integer)
    private Integer stock;
    @Field(type = FieldType.Integer)
    private Integer sales;
    @Field(type = FieldType.Double)
    private BigDecimal avgRating;
    @Field(type = FieldType.Integer)
    private Integer reviewCount;
    @Field(type = FieldType.Keyword)     // URL 不分词
    private String coverImage;
    @Field(type = FieldType.Integer)
    private Byte status;
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createTime;
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime updateTime;

    // ⭐ 静态工厂方法: ProductSource → ProductDocument
    public static ProductDocument from(ProductSource src) {
        ProductDocument doc = new ProductDocument();
        doc.setId(src.getId());
        doc.setName(src.getName());
        doc.setCategoryId(src.getCategoryId());
        doc.setDescription(src.getDescription());
        doc.setDetail(src.getDetail());
        doc.setPrice(src.getPrice());
        doc.setStock(src.getStock());
        doc.setSales(src.getSales());
        doc.setAvgRating(src.getAvgRating());
        doc.setReviewCount(src.getReviewCount());
        doc.setCoverImage(src.getCoverImage());
        doc.setStatus(src.getStatus());
        doc.setCreateTime(src.getCreateTime());
        doc.setUpdateTime(src.getUpdateTime());
        return doc;
    }
}
```

**关键注解**:
- `@Document(indexName="...")` 类级别, 声明映射到 ES 哪个索引. 索引名规范: 全小写 + 下划线
- `@Id` 主键 → ES 的 `_id`. save 时 ES 用它判断新增 or 覆盖
- `@Field(type=...)` 显式声明类型. 不写 ES 自动推断成 Text+Keyword 双字段, 占空间不可控
- `DateFormat.date_hour_minute_second` 对应 `yyyy-MM-dd'T'HH:mm:ss`, 跟 LocalDateTime 序列化匹配

## 75.3 DTO / VO / 数据载体三剑客

### 75.3.1 ProductSearchRequest (前端搜索条件)

```java
@Data
public class ProductSearchRequest {
    private String keyword;            // 搜 name/description/detail
    private Long categoryId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer page;
    private Integer size;
    private String sort;               // price_asc/price_desc/sales_desc/rating_desc/newest
}
```

**知识点**: Spring MVC 自动把 HTTP query 参数按字段名映射到 DTO, **无需 @RequestParam**. 字段全用包装类, 前端不传时能为 null.

### 75.3.2 ProductSearchVO (返给前端的卡片)

```java
@Data
public class ProductSearchVO {
    private Long id;              // 跳详情
    private String name;          // 卡片标题
    private BigDecimal price;     // ¥X,XXX
    private String coverImage;    // 缩略图
    private Long categoryId;      // 分类标签
    private Integer sales;        // 已售
    private BigDecimal avgRating; // ⭐ 4.8 分
    private Integer reviewCount;  // (1234 评论)

    // ⭐ 第二次用 from() 套路: ProductDocument → VO
    public static ProductSearchVO from(ProductDocument doc) {
        ProductSearchVO vo = new ProductSearchVO();
        vo.setId(doc.getId());
        vo.setName(doc.getName());
        vo.setPrice(doc.getPrice());
        vo.setCoverImage(doc.getCoverImage());
        vo.setCategoryId(doc.getCategoryId());
        vo.setSales(doc.getSales());
        vo.setAvgRating(doc.getAvgRating());
        vo.setReviewCount(doc.getReviewCount());
        return vo;
    }
}
```

**思想**: ProductDocument 14 字段, VO 只取 8 个卡片字段, 不返 description/detail/stock/status/time (详情页才用). **省带宽, 前端代码更清爽**.

### 75.3.3 PageResultVO\<T\> (通用分页结果, 泛型类)

```java
@Data
@AllArgsConstructor
public class PageResultVO<T> {
    private Long total;     // ES totalHits
    private Long pages;     // 总页数 = 向上取整(total/size)
    private Integer page;
    private Integer size;
    private List<T> records;
}
```

**泛型**: 用 `PageResultVO<ProductSearchVO>` 装商品, `PageResultVO<OrderVO>` 装订单. 一个类多种用途.

## 75.4 ⭐ 微服务铁律 - ProductSource + Feign

### 75.4.1 product 服务暴露 internal 端点 (G9.3.1)

```java
// Controller 已有 @RequestMapping("/product") 类前缀
@GetMapping("/internal/all")
public Result<List<Product>> listAllForSync() {
    return Result.success(productService.listAllForSync());
}

// ServiceImpl
@Override
public List<Product> listAllForSync() {
    // ⭐ 只灌"已上架"商品, MP 自动加 is_deleted=0
    return lambdaQuery().eq(Product::getStatus, (byte) 1).list();
}
```

**约定**:
- `/internal/*` 路径 = 服务间内部接口, 网关可不路由
- 最终路径 = `/product/internal/all` (类前缀 + 方法路径自动拼接)

### 75.4.2 ⭐ 微服务核心铁律 - 不共享 jar

**search 服务不能 import product 服务的 Product entity**.

为什么:
- product 和 search 是两个独立的 jar, 不能在 pom 里 `<dependency>` 对方
- 否则 search 跟 product 强耦合, 改 product 要重发 search → 回到单体痛苦
- 跨服务调用**只走 HTTP/Feign**, 不走 jar 依赖

**怎么传数据?** Jackson 反序列化**只看字段名, 不看类名**:
- JSON 里 `"name":"华为"` → 你的 Java 类里有字段 `private String name;` → 自动赋值
- ProductSource 哪怕改名叫 Foo, 字段名跟 JSON 对得上, 反序列化都成功

### 75.4.3 ProductSource (search 自己写一份)

```java
// search 服务 entity/ProductSource.java
@Data
public class ProductSource {                    // ⭐ 不带任何 ES/MP 注解, 纯 POJO
    private Long id;
    private Long categoryId;
    private String name;
    private String description;
    private String detail;
    private BigDecimal price;
    private Integer stock;
    private Integer sales;
    private BigDecimal avgRating;
    private Integer reviewCount;
    private String coverImage;
    private Byte status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    // ⭐ 14 字段跟 product.Product 完全镜像, 但是独立的类
}
```

**早就在 G7 用过同样套路** — review 服务 ReviewStatsVO + product 服务 ReviewStatsVO 各一份. 这是微服务的"DTO 副本模式".

### 75.4.4 ProductFeignClient + Fallback

```java
// client/ProductFeignClient.java
@FeignClient(
    name = "mini-mall-product",
    fallback = ProductFeignClientFallback.class
)
public interface ProductFeignClient {
    @GetMapping("/product/internal/all")        // ⭐ 完整路径
    Result<List<ProductSource>> listAllForSync();
}

// client/ProductFeignClientFallback.java
@Component
@Slf4j
public class ProductFeignClientFallback implements ProductFeignClient {
    @Override
    public Result<List<ProductSource>> listAllForSync() {
        log.warn("[product-feign] listAllForSync 降级, product 服务不通");
        return Result.error(503, "product 服务降级, 拉商品失败");
    }
}
```

**知识点**:
- `@FeignClient(name=...)` 走 Nacos 负载均衡, 不写 url
- Fallback 必须 `@Component` + `implements` 同一接口
- ⭐ 错误码用 **503 (Service Unavailable)** 比 500 贴切, 降级是"服务不可用"
- `log.warn` 不用 `log.error`: 降级是预期内失败, 不算 bug, 不必触发告警

## 75.5 Spring Data ES Repository - ES 版的 MyBatis-Plus

### 75.5.1 ProductDocumentRepository

```java
public interface ProductDocumentRepository
        extends ElasticsearchRepository<ProductDocument, Long> {
    // 不需要任何方法! Spring Data 用 JDK 动态代理自动生成实现
    //
    // 进阶: 加方法签名 → Spring Data 按命名规则自动生成查询 DSL (派生查询)
    // List<ProductDocument> findByCategoryId(Long categoryId);
}
```

**对照 MyBatis-Plus**:

| 概念 | MyBatis-Plus (MySQL) | Spring Data ES |
|---|---|---|
| 数据载体 | `@TableName` Entity | `@Document` Document |
| DAO 接口 | `extends BaseMapper<T>` | `extends ElasticsearchRepository<T, ID>` |
| 自动方法 | selectById/insert/updateById/... | findById/save/findAll/deleteById/... |
| 底层 | 接口→动态代理→SQL→MySQL | 接口→动态代理→HTTP→ES |
| 主键注解 | `@TableId` | `@Id` |

### 75.5.2 ⭐ ES 的 save 是 upsert, 不是 insert!

**关键认知**:
- MP 的 `save` = INSERT, id 已存在会主键冲突
- ES 的 `save` = **upsert** (有则覆盖, 无则插入)

为什么? 因为 ES 的设计哲学是 **"索引就是一份可重建的副本"**, 同步时不需要区分新增 vs 更新, 直接覆盖最方便.

所以 `syncAll` 里 `repository.saveAll(documents)` 重复跑也不会冲突.

## 75.6 IProductSearchService 接口 + 实现分离

### 75.6.1 为什么要接口 + 实现分离 (4 个理由)

1. **Controller 只依赖接口** → 换实现不动调用代码
2. **Spring AOP / @Transactional 底层用 JDK 动态代理, 必须有接口**
3. **看接口能秒懂这个 service 对外能干啥**, 不必钻细节
4. **接口可以共享** (本服务 implements, 别的服务也可以 implements 当 Feign 接口)

### 75.6.2 IProductSearchService.java (4 方法)

```java
public interface IProductSearchService {

    /** 全量同步: product 所有商品 → ES. 首次部署/索引重建/数据修复 */
    int syncAll();

    /** 单条同步: 商品上架/编辑/价格变动时调 (MQ 触发) */
    void syncById(Long productId);

    /** 单条删除: 商品下架/删除时调 */
    void deleteById(Long productId);

    /** 搜索: keyword/categoryId/price 区间 + 分页 + 排序 */
    PageResultVO<ProductSearchVO> search(ProductSearchRequest request);
}
```

**4 个方法定位**: 前 3 个**写 ES** (sync 系列), 第 4 个**读 ES** (search). 读写分离清晰.

## 75.7 ProductSearchServiceImpl - sync 系列 (写 ES)

### 75.7.1 同步 4 步法

```java
@Service
@Slf4j
public class ProductSearchServiceImpl implements IProductSearchService {

    @Resource
    private ProductDocumentRepository repository;
    @Resource
    private ProductFeignClient productFeignClient;
    @Resource
    private ElasticsearchOperations elasticsearchOperations;

    @Override
    public int syncAll() {
        log.info("[search-sync] 开始全量同步商品...");
        // 1. Feign 拉全量上架商品
        Result<List<ProductSource>> result = productFeignClient.listAllForSync();
        // 2. 失败兜底 (Sentinel 降级也走这里, result 是 fallback 的 error)
        if (result.getCode() == null || result.getCode() != 200) {
            log.error("[search-sync] 拉商品失败, message={}", result.getMessage());
            return 0;
        }
        // 3. ⭐ ProductSource → ProductDocument (用 from 静态工厂)
        List<ProductSource> sources = result.getData();
        List<ProductDocument> documents = sources.stream()
                .map(ProductDocument::from)            // 方法引用, 等价 (src -> ProductDocument.from(src))
                .toList();
        // 4. 批量灌 ES (saveAll 是 upsert, 内部走 _bulk API 高效写入)
        repository.saveAll(documents);
        log.info("[search-sync] 全量同步完成, 共 {} 条", documents.size());
        return documents.size();
    }

    @Override
    public void syncById(Long productId) {
        // TODO: 后续 MQ 触发时填实现
        log.info("syncById 还没实现, productId={}", productId);
    }

    @Override
    public void deleteById(Long productId) {
        repository.deleteById(productId);
        log.info("[search-sync] 已从 ES 删除商品 productId={}", productId);
    }
}
```

### 75.7.2 ⭐ 包装类 == 比较的 NPE 坑

```java
if (result.getCode() == 200) {                 // ⚠ 有 NPE 风险
```

`result.getCode()` 返 `Integer`, 跟 `int 200` 比会**自动拆箱**, 如果 code 是 null 抛 NPE.

**严谨写法**:
```java
if (result.getCode() == null || result.getCode() != 200) { return 0; }
```

是 Java 八股**包装类 == 比较拆箱 NPE** 经典考点.

### 75.7.3 deleteById 不需要源数据

ES 操作 vs 需要的数据:

| 操作 | 需要数据 | 为什么 |
|---|---|---|
| save (写) | ID + 14 业务字段 | ES 要给每个字段建索引 |
| findById (读) | 只要 ID | ES 用 ID 精确定位文档返完整 JSON |
| deleteById (删) | 只要 ID | ES 用 ID 找文档**直接抹除**, 不关心里面有啥 |

**核心认知**: "写"才需要完整数据, "读/删" 只要 ID. ES 的 deleteById 是**幂等**的 — 删一个不存在的 ID 不报错.

## 75.8 ⭐ search 方法 - G9 最难一步 (读 ES)

### 75.8.1 BoolQuery 灵魂 (面试常考)

ES 复合查询用 `BoolQuery`, 4 个子句:

| 子句 | 是否打分 | 是否缓存 | 用途 |
|---|---|---|---|
| **must** | ✅ 算分→影响相关度排序 | ❌ | 关键词搜内容 |
| **filter** | ❌ 不算分 | ✅ 缓存 | 精确条件过滤 |
| should | 至少满足一个 | — | OR 逻辑 |
| mustNot | 必须不满足 | — | 排除 |

**经验法则**:
- `keyword` 搜 → **must** (要按相关度排序)
- `categoryId` / `price` 区间 → **filter** (更快, 有缓存)

### 75.8.2 search 完整代码

```java
@Override
public PageResultVO<ProductSearchVO> search(ProductSearchRequest request) {
    // ─── 1. 参数默认值 ────────────
    int page = request.getPage() == null || request.getPage() < 1 ? 1 : request.getPage();
    int size = request.getSize() == null || request.getSize() < 1 ? 10 : request.getSize();

    // ─── 2. 构造 BoolQuery ──────
    BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

    // 2a. keyword: multi_match 多字段模糊匹配 (must, 打分)
    if (StringUtils.hasText(request.getKeyword())) {
        Query keywordQuery = MultiMatchQuery.of(m -> m
                .query(request.getKeyword())
                .fields("name", "description", "detail")
        )._toQuery();
        boolBuilder.must(keywordQuery);
    }

    // 2b. categoryId: TermQuery 精确匹配 (filter, 不打分)
    if (request.getCategoryId() != null) {
        Query catQuery = TermQuery.of(t -> t
                .field("categoryId")
                .value(request.getCategoryId())
        )._toQuery();
        boolBuilder.filter(catQuery);
    }

    // 2c. price 区间: RangeQuery gte/lte (filter)
    if (request.getMinPrice() != null || request.getMaxPrice() != null) {
        Query priceQuery = RangeQuery.of(r -> {
            r.field("price");
            if (request.getMinPrice() != null) r.gte(JsonData.of(request.getMinPrice()));
            if (request.getMaxPrice() != null) r.lte(JsonData.of(request.getMaxPrice()));
            return r;
        })._toQuery();
        boolBuilder.filter(priceQuery);
    }

    // ─── 3. 包装成 NativeQuery (含分页 + 排序) ──
    NativeQuery nativeQuery = NativeQuery.builder()
            .withQuery(boolBuilder.build()._toQuery())
            .withPageable(PageRequest.of(page - 1, size, parseSort(request.getSort())))
            //              ⭐ page - 1 因为 PageRequest 从 0 开始 (经典坑)
            .build();

    // ─── 4. 执行查询 ──────────────
    SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);

    // ─── 5. SearchHits → PageResultVO ─
    List<ProductSearchVO> records = hits.getSearchHits().stream()
            .map(SearchHit::getContent)           // SearchHit → ProductDocument
            .map(ProductSearchVO::from)           // ProductDocument → VO
            .toList();
    long total = hits.getTotalHits();
    long pages = (total + size - 1) / size;       // ⭐ 整数向上取整经典写法

    log.info("[search] keyword={}, total={}, page={}/{}",
            request.getKeyword(), total, page, pages);
    return new PageResultVO<>(total, pages, page, size, records);
}

/** 解析 sort 字符串 → Spring Sort 对象 */
private Sort parseSort(String sort) {
    if (!StringUtils.hasText(sort)) return Sort.unsorted();
    return switch (sort) {
        case "price_asc"   -> Sort.by(Sort.Order.asc("price"));
        case "price_desc"  -> Sort.by(Sort.Order.desc("price"));
        case "sales_desc"  -> Sort.by(Sort.Order.desc("sales"));
        case "rating_desc" -> Sort.by(Sort.Order.desc("avgRating"));
        case "newest"      -> Sort.by(Sort.Order.desc("createTime"));
        default            -> Sort.unsorted();
    };
}
```

### 75.8.3 5 个新 API 速查

| API | 作用 |
|---|---|
| `BoolQuery.Builder().must/filter/should/mustNot` | 拼布尔查询 |
| `MultiMatchQuery.of(m -> ...)` | 一个 keyword 搜多个字段 |
| `TermQuery.of(t -> ...)` | 精确等值匹配 |
| `RangeQuery.of(r -> ...).gte().lte()` | 区间过滤 |
| `._toQuery()` | 新版 Java Client quirk: 具体 Query → 通用 Query 接口 |
| `NativeQuery.builder().withQuery().withPageable().build()` | 查询包装器 |
| `ElasticsearchOperations.search(q, T.class)` | 真正发 HTTP 到 ES |
| `SearchHits<T>.getSearchHits().stream().map(SearchHit::getContent)` | 解析结果 |

### 75.8.4 Repository vs ElasticsearchOperations

| 用哪个 | 场景 |
|---|---|
| **Repository** | 简单 CRUD: save/findById/deleteById/saveAll |
| **ElasticsearchOperations** | 复杂搜索: BoolQuery + 分页 + 排序 + 高亮 |

口诀: **简单 CRUD 用 Repo, 复杂 Query 用 Operations**.

## 75.9 ProductSearchController + 网关路由

### 75.9.1 Controller (4 端点)

```java
@RestController
@RequestMapping("/search")          // ⭐ 类前缀
@Slf4j
public class ProductSearchController {

    @Resource
    private IProductSearchService searchService;

    @PostMapping("/sync")           // 全量同步 (运维触发)
    public Result<Integer> syncAll() {
        return Result.success(searchService.syncAll());
    }

    @PostMapping("/sync/{productId}")    // 单条同步 (后续 MQ)
    public Result<Void> syncById(@PathVariable Long productId) {
        searchService.syncById(productId);
        return Result.success();
    }

    @DeleteMapping("/{productId}")       // 单条删除
    public Result<Void> deleteById(@PathVariable Long productId) {
        searchService.deleteById(productId);
        return Result.success();
    }

    @GetMapping("/product")              // ⭐ 搜索 (核心入口)
    public Result<PageResultVO<ProductSearchVO>> search(ProductSearchRequest request) {
        return Result.success(searchService.search(request));
    }
}
```

**REST 方法语义**:
- **GET** 读 (无副作用) — search
- **POST** 写/创建 (有副作用) — sync
- **DELETE** 删 — delete
- ⚠ 不能搞反 (GET 不能改数据)

`Result<Void>` 不返业务数据时用 (Void 不是 void), 配 `Result.success()` 无参版.

### 75.9.2 网关路由 (gateway/application.yml)

```yaml
spring:
  cloud:
    gateway:
      routes:
        # G9: Elasticsearch product search service (9005)
        - id: search-route
          uri: lb://mini-mall-search
          predicates:
            - Path=/search/**
```

**知识点**:
- `lb://mini-mall-search` — LoadBalancer, 网关从 Nacos 拿所有实例 IP, 自动轮询
- `Path=/search/**` 双星号通配, 命中 `/search/sync` / `/search/product` / `/search/sync/123` 全行

## 75.10 端到端验证 (curl 实测)

### 75.10.1 启动顺序

```bash
# Docker 容器
docker start minimall-nacos minimall-sentinel seata-server
# 已有: mini-mall-es, mini-mall-kibana, mini-mall-rabbitmq

# 本机服务: MySQL :3306, Redis :6379 必须在

# 启动 search jar
java -jar mini-mall-search/target/mini-mall-search-0.0.1-SNAPSHOT.jar

# 验证 Nacos 注册
curl "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=mini-mall-search"
# 期望 "hosts":[{"ip":"...","port":9005,"healthy":true}]
```

### 75.10.2 全量同步

```bash
# 触发
curl -X POST http://127.0.0.1:9005/search/sync
# {"code":200,"data":3}

# 看 ES 索引
curl "http://127.0.0.1:9200/mini_mall_product/_count?pretty"
# {"count": 3}

# 看一条
curl "http://127.0.0.1:9200/mini_mall_product/_search?size=1&pretty"
# 能看到 {"_id":"1","_source":{"name":"小米 14 Pro",...}}
```

### 75.10.3 4 种搜索测试 (全过)

| 测试 | 期望 | 实际 |
|---|---|---|
| 搜 "小米" | 只命中小米手机 | total:1 ✅ |
| 不传 keyword | 返全部 | total:3 ✅ |
| 价格 1000-3000 | 区间内 0 条 | total:0 ✅ filter 生效 |
| sort=sales_desc | 销量降序 | 5 → 2 → 0 ✅ |

**BoolQuery + multi_match + range filter + Sort 全部按预期工作** 🎯

## 75.11 G9 踩的小坑速查

| 坑 | 表现 | 原因 | 修 |
|---|---|---|---|
| ⭐ **改代码后 jar 没重打** | product `/internal/all` 返 `NoResourceFoundException` (404) | fat jar 是旧版本, 没有 G9.3.1 加的端点 | kill 进程 → `mvn install` → 重启 |
| ⭐ IDE 自动 import 坑 (3 次) | `org.w3c.dom.Text` / `org.yaml.snakeyaml.Event.ID` / `org.aspectj.bcel.Code` | IDE 弹候选选错 | 永远先看包名 `com.minimall.*` / `java.*` / `org.springframework.*` |
| BoolQuery must/filter 用反 | 精确过滤被打分影响 | 把 categoryId 放 must 了 | 改 filter (不打分, 有缓存) |
| PageRequest 从 0 开始 | 第 1 页拿到第 2 页数据 | 直接传 page 没减 1 | `PageRequest.of(page - 1, size)` |
| 包装类 == 拆箱 NPE | `result.getCode() == 200` 偶发 NPE | code 为 null 时拆箱失败 | 先判 `code == null` |
| Bash 起 java 失败 (exit 127) | `command not found` | Git Bash PATH 里 java 是 Java 8 | 用 PowerShell + `$env:JAVA_HOME\bin\java.exe` 全路径 |
| product 一直连不上 Seata | 日志刷 `Connection refused 8091` | Seata 容器停了 | `docker start seata-server` (虽然只是干扰日志, 不阻塞业务) |

## 75.12 G9 教学速查 - 关键 API & 模式

### ① ⭐ 静态工厂方法 from() (G9 重点)

```java
public static ProductDocument from(ProductSource src) {
    ProductDocument doc = new ProductDocument();
    doc.setId(src.getId());
    // ... 字段一一 set
    return doc;
}
```

用法: `stream().map(ProductDocument::from).toList()`

**3 个 from 用例**:
- ProductSource → ProductDocument (G9.4.3 syncAll)
- ProductDocument → ProductSearchVO (G9.4.4 search)
- (之前 G7) Reviews → ReviewVO

口诀: **set 在外(目标), get 在里(源)**. 数据流向只有一个: 源 → 目标.

### ② BoolQuery 拼装套路

```java
BoolQuery.Builder b = new BoolQuery.Builder();
if (cond1) b.must(query1);           // 关键词 (打分)
if (cond2) b.filter(query2);          // 精确 (不打分)
Query final = b.build()._toQuery();
```

### ③ NativeQuery 三件套

```java
NativeQuery q = NativeQuery.builder()
    .withQuery(boolQuery)
    .withPageable(PageRequest.of(page - 1, size, sort))
    .build();
SearchHits<T> hits = operations.search(q, T.class);
```

### ④ SearchHits 解析模板

```java
List<VO> records = hits.getSearchHits().stream()
    .map(SearchHit::getContent)      // → Document
    .map(VO::from)                    // → VO
    .toList();
long total = hits.getTotalHits();
long pages = (total + size - 1) / size;
```

### ⑤ Repository 派生查询 (进阶, 没用上)

```java
public interface ProductDocumentRepository extends ElasticsearchRepository<ProductDocument, Long> {
    List<ProductDocument> findByCategoryId(Long categoryId);
    List<ProductDocument> findByStatusAndPriceLessThan(Byte status, BigDecimal price);
}
```

Spring Data 看方法名匹配 findBy + 字段名 → 自动生成 ES 查询 DSL.

## 75.13 G9 累计文件

```
父 pom 改:
  pom.xml                          (解开 <module>mini-mall-search</module>)

mini-mall-search/  (从 0 新建整个模块)
├── pom.xml                                       (新建)
├── src/main/resources/
│   ├── application.yml                           (端口 9005 + ES + Nacos)
│   └── application.yml.example                   (脱敏版给 GitHub)
└── src/main/java/com/minimall/search/
    ├── MiniMallSearchApplication.java            (启动类)
    ├── document/ProductDocument.java             (⭐ ES 索引映射 + from)
    ├── dto/ProductSearchRequest.java             (搜索条件)
    ├── vo/ProductSearchVO.java                   (卡片字段 + from)
    ├── vo/PageResultVO.java                      (泛型分页 VO)
    ├── entity/ProductSource.java                 (⭐ Feign 数据载体)
    ├── client/
    │   ├── ProductFeignClient.java               (调 product)
    │   └── ProductFeignClientFallback.java       (降级)
    ├── repository/ProductDocumentRepository.java (⭐ ES 版 BaseMapper)
    ├── service/
    │   ├── IProductSearchService.java            (接口)
    │   └── impl/ProductSearchServiceImpl.java    (⭐ sync + search 核心 ~120 行)
    └── controller/ProductSearchController.java   (4 端点)

product 服务改:
  controller/ProductController.java               (+ /internal/all 端点)
  service/IProductService.java                    (+ listAllForSync)
  service/impl/ProductServiceImpl.java            (+ lambdaQuery status=1)

gateway 改:
  application.yml                                 (+ search-route, Path=/search/**)
```

## 75.14 ⭐ G9 学到的 10 个核心知识点

1. **Elasticsearch 三大概念**: 文档 / 索引 / 倒排索引 + 分词器
2. **Spring Data ES Repository = ES 版 MyBatis-Plus**: 1 行接口自动 CRUD
3. **ES Field 类型选择**: Text 分词搜 / Keyword 精确 / Long/Double / Date
4. **⭐ BoolQuery 灵魂**: must (打分) vs filter (不打分, 有缓存) — 面试常考
5. **ES 复杂查询用 ElasticsearchOperations + NativeQuery**, 简单 CRUD 用 Repository
6. **⭐ 微服务铁律**: 不共享 jar, ProductSource 副本 + Jackson 按字段名匹配反序列化
7. **⭐ 静态工厂方法 from()**: 在目标类里写转换, `stream().map(类::方法)` 链式
8. **接口 + 实现分离**: 4 个理由 (调用方依赖接口 / AOP 动态代理 / 可读性 / 可共享)
9. **Feign + Fallback 降级**: 服务挂了不抛异常, 走兜底 Result.error (用 503 比 500 贴切)
10. **⭐ 改代码后必须重新打包**: IDE 改的是源码, fat jar 不会自动同步 (G9 真实排坑)

---

**G9 完毕**. 微服务从 "完整电商核心" 走到 "**电商核心 + 搜索引擎**". 至此 mini-mall-cloud 业务能力齐全 (用户/商品/订单/库存/物流/评价/优惠券/搜索), 后续可选: C1 README + C2 GitHub 改名收尾, 或继续 G10 后台管理.



# 76. common-swagger - Knife4j 公共配置

> 抽到公共库, 5 个业务服务 (user/product/order/review/search) 一行依赖搞定 API 文档.

## 76.0 总览 - 做了啥

```
mini-mall-common-swagger (新建子模块)
├── pom.xml                              # 只引一个: knife4j-openapi3-jakarta-spring-boot-starter
└── SwaggerConfig.java
    └── @Bean OpenAPI miniMallOpenAPI()  # Info + JWT Bearer 鉴权方案

5 个业务服务 pom 各加 1 段:
    <dependency>
        <groupId>com.minimall</groupId>
        <artifactId>mini-mall-common-swagger</artifactId>
    </dependency>

启动后访问: http://<host>:<port>/doc.html
```

**核心收获**:
1. **OpenAPI / Swagger / Knife4j / springdoc 4 个概念关系**
2. **JWT Bearer 鉴权方案** (面试常考: HTTP type + bearer scheme + JWT format)
3. **Servlet 3.0 静态资源规范** (META-INF/resources/ 自动映射到 web 根)
4. **零配置设计**: 引依赖 → @ComponentScan 扫到 → Spring 自动注入 OpenAPI bean
5. **改 pom 后必须重启 jar** (老 jar 没新依赖)

## 76.1 4 个相关概念关系 (必须区分)

```
OpenAPI    ────  API 描述规范 (JSON/YAML 标准, Linux 基金会维护)
   ↓
Swagger    ────  最早实现 OpenAPI 的工具集 + 默认 UI
   ↓
springdoc  ────  Spring Boot 3 的 OpenAPI 自动扫描库 (扫 @RestController 生成文档)
   ↓
Knife4j    ────  在 springdoc 基础上, 替换默认 UI 为更漂亮的 Vue UI
                 (中国人写的 Swagger 增强, 国内项目主流)
```

**我们用的栈** = `springdoc` (扫代码生成 OpenAPI JSON) + `knife4j` (好看的前端 UI).

引一个 `knife4j-openapi3-jakarta-spring-boot-starter` 自动把这俩都拉进来.

## 76.2 mini-mall-common-swagger 子模块

### 76.2.1 父 pom 解开模块注释 (注意只解 swagger, security 留着!)

```xml
<modules>
    <module>mini-mall-common-core</module>
    <module>mini-mall-common-redis</module>
    <module>mini-mall-common-swagger</module>
    <!-- 后续做完再解开
    <module>mini-mall-common-security</module>
    -->
</modules>
```

⚠ **坑**: 一次解多了 — 把 common-security 也解开但目录不存在, `mvn install` 必爆 "Could not find module". 哪个建好了才解哪个.

### 76.2.2 mini-mall-common-swagger/pom.xml (最小依赖)

```xml
<parent>
    <groupId>com.minimall</groupId>
    <artifactId>mini-mall-common</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</parent>

<artifactId>mini-mall-common-swagger</artifactId>

<dependencies>
    <!-- ① Knife4j (含 springdoc-openapi 自动扫描 + Knife4j UI 增强) -->
    <!-- 版本父 pom 已锁 4.5.0, 这里不写 version -->
    <dependency>
        <groupId>com.github.xiaoymin</groupId>
        <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
    </dependency>

    <!-- ② Lombok (SwaggerConfig 可能用 @Slf4j) -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

⚠ **3 个常犯的依赖错**:
| 错 | 为啥错 |
|---|---|
| `knife4j-spring-boot-starter` (没 -openapi3-jakarta-) | 那是 Swagger 2 + Spring Boot 2 老版, Spring Boot 3 用不了 |
| 显式引 `spring-boot-starter-web` | knife4j-starter 内部已经传递了 spring-web, 重复引污染依赖 |
| 引 `mini-mall-common-base` | 项目里没这个 artifact (应该是 -core, 但 swagger 不依赖业务的 Result, 干脆不引) |

## 76.3 SwaggerConfig.java (核心)

### 76.3.1 完整代码

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

@Configuration
public class SwaggerConfig {

    /** 取当前服务的 spring.application.name 作为文档标题 */
    @Value("${spring.application.name:mini-mall-cloud}")
    private String appName;

    @Bean
    public OpenAPI miniMallOpenAPI() {
        // ① 文档头部信息
        Info info = new Info()
                .title(appName + " API 文档")
                .description("mini-mall-cloud 微服务电商 API (基于 Knife4j + OpenAPI 3)")
                .version("0.0.1-SNAPSHOT")
                .contact(new Contact()
                        .name("CXY-STU-rug")
                        .url("https://github.com/CXY-STU-rug/mini-mall-cloud"))
                .license(new License().name("MIT"));

        // ② JWT Bearer 鉴权方案 (面试常考)
        SecurityScheme jwtScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT token (登录后从 /user/login 拿)");

        // ③ 组装: info + components(挂鉴权方案) + 全局应用鉴权
        return new OpenAPI()
                .info(info)
                .components(new Components()
                        .addSecuritySchemes("Bearer", jwtScheme))    // 注册名 "Bearer"
                .addSecurityItem(new SecurityRequirement().addList("Bearer"));
                //                                            ^^^^ 引用上面名字, 大小写敏感
    }
}
```

### 76.3.2 ⭐ JWT Bearer 鉴权方案 3 件套 (面试常考)

```java
new SecurityScheme()
    .type(SecurityScheme.Type.HTTP)    // ← 走 HTTP Header 鉴权
    .scheme("bearer")                  // ← 用 Bearer Token 模式
    .bearerFormat("JWT")               // ← 提示这是 JWT 格式
```

**效果**: Knife4j UI 顶部出现 **Authorize** 按钮, 点开输入 JWT → 之后所有请求自动带:
```
Authorization: Bearer <token>
```

### 76.3.3 ⭐ Bean 注入而不是 @EnableXxx 注解

**没有什么 `@EnableSwagger` / `@EnableKnife4j` 注解**! 全是 Spring Bean 扫描机制:

1. SwaggerConfig 在 `com.minimall.common.swagger.config` 包
2. 业务服务启动类用 `@ComponentScan("com.minimall")` 扫描范围扩到顶层包
3. Spring 启动时自动扫到 SwaggerConfig + @Configuration + @Bean
4. 生成 OpenAPI bean, springdoc 检测到这个 bean 就用它当全局配置

跟 common-redis 的 RedisConfig 走的是**完全一样的注入套路**.

## 76.4 5 个业务服务 pom 加依赖

每个 pom 在 common-core 后面加一段:

```xml
<dependency>
    <groupId>com.minimall</groupId>
    <artifactId>mini-mall-common-core</artifactId>
</dependency>

<!-- ⭐ 公共 Swagger / Knife4j -->
<dependency>
    <groupId>com.minimall</groupId>
    <artifactId>mini-mall-common-swagger</artifactId>
</dependency>
```

5 个加: **user / product / order / review / search**. 网关 gateway 跳过 (WebFlux 不是 WebMVC, 网关聚合文档要另搞).

## 76.5 ⭐ /doc.html 为啥不用配置就能访问 (Servlet 3.0 规范)

```
knife4j-openapi3-ui-4.5.0.jar 里有:
  META-INF/resources/doc.html         (1903 字节, Vue SPA 入口)
  META-INF/resources/webjars/css/...
  META-INF/resources/webjars/js/...
```

**Spring Boot 静态资源约定** (Servlet 3.0 引入):

| jar 里的路径 | 自动映射到 |
|---|---|
| `static/foo.html` | `http://host/foo.html` |
| `public/foo.html` | `http://host/foo.html` |
| **`META-INF/resources/foo.html`** | **`http://host/foo.html`** |
| `webjars/bootstrap/css/x.css` | `http://host/webjars/bootstrap/css/x.css` |

**核心**: Spring Boot 启动时扫所有 classpath jar 的 `META-INF/resources/`, 把内容映射到 web 根目录.

**所以**:
1. Knife4j 把 `doc.html` 放 `META-INF/resources/`
2. 你 pom 引依赖 → 这个 jar 进 classpath
3. Spring Boot 自动暴露 `http://host/doc.html`
4. **不用任何 @Controller, 不用任何配置**

这是**零配置设计哲学**.

### Knife4j vs 原生 Swagger 入口对比

| 工具 | 默认入口 |
|---|---|
| 原生 Swagger UI | `/swagger-ui/index.html` |
| **Knife4j** | **`/doc.html`** ← 有自己一套 Vue UI |
| springdoc-openapi | `/swagger-ui/index.html` (用原生 UI) |

引了 Knife4j 后**两个 UI 都能开**, 但 Knife4j 更好看, 国内主流.

## 76.6 端到端验证

```bash
# 1. mvn install (11 模块全 SUCCESS)
mvn clean install -DskipTests

# 2. 启动 user-service
java -jar mini-mall-user/target/mini-mall-user-0.0.1-SNAPSHOT.jar

# 3. 验证 OpenAPI JSON
curl http://127.0.0.1:9001/v3/api-docs
# {"openapi":"3.0.1","info":{"title":"mini-mall-user API 文档",...
#  "security":[{"Bearer":[]}],   ← JWT 鉴权方案生效 ✅
#  "paths":{"/user/address/{id}":{...}, ...}}

# 4. 验证 UI
curl -I http://127.0.0.1:9001/doc.html
# HTTP/1.1 200    ← 浏览器能打开 ✅
```

**预期效果** (浏览器看 doc.html):
- 顶部标题: **mini-mall-user API 文档** (appName 自动替换)
- 左侧端点列表: register / login / me / address / coupon / ...
- 每个端点点开看参数 / 返回值 / 调试按钮
- 右上角 **Authorize** 按钮 (点开就是输入 JWT 的弹窗)

## 76.7 踩的小坑速查

| 坑 | 表现 | 修 |
|---|---|---|
| **改完 pom 后没重启 jar** | `/doc.html` 404 / 老内容 | kill 进程 + mvn install + 重启 |
| **mvn clean 报错占用** | `Failed to delete xxx.jar` | 先 kill 所有 java 进程 (`Stop-Process java`) |
| 父 pom 解多了 module 注释 | `Could not find module 'mini-mall-common-security'` | 没建的不要解开 |
| 包目录手敲错字 | `con**fog**` 而不是 `con**fig**` | IDEA 右键 New > Package 自动建嵌套, 别手动 mkdir |
| import 选错版本 | `io.swagger.annotations.*` (Swagger 2 注解) | 必须 `io.swagger.v3.oas.models.*` (OpenAPI 3) |
| @EnableSwagger 找不到 | 找了一个不存在的注解 | 没有这个东西! 全是 Spring Bean 扫描机制 |

## 76.8 教学速查 - 5 个新概念

### ① OpenAPI builder 链式调用

```java
OpenAPI api = new OpenAPI()
    .info(...)
    .components(...)
    .addSecurityItem(...);
```

每个方法返回 `OpenAPI` 自己, 可链式. 跟 Spring Data 的 PageRequest / NativeQueryBuilder 套路一样.

### ② Components 是公共定义池

```java
Components components = new Components()
    .addSecuritySchemes("Bearer", jwtScheme)    // 鉴权方案
    .addSchemas(...)                             // 通用 schema
    .addResponses(...);                          // 通用响应
```

注册的东西**不会自动生效**, 必须用 `addSecurityItem` / `$ref` 引用才生效.

### ③ SecurityRequirement 全局应用 vs 单方法应用

全局 (我们用的):
```java
.addSecurityItem(new SecurityRequirement().addList("Bearer"));
```
单方法 (springdoc):
```java
@SecurityRequirement(name = "Bearer")  // 加在 @GetMapping 方法上
public ... someMethod() { ... }
```

我们项目: 全局应用一次, 不需要鉴权的端点 (如 /user/login) 会被 Spring Security 在过滤器层放行, Swagger 文档显示会带锁图标但不强制.

### ④ Servlet 3.0 META-INF/resources

Jar 文件里 `META-INF/resources/` 目录的内容自动映射到 web 根目录. 这是 Servlet 3.0 引入的 "jar 自带静态资源" 规范.

WebJars (Bootstrap/jQuery 等) 也是利用这个机制, 把 css/js 打进 jar 里发布.

### ⑤ @Value 配置注入默认值语法

```java
@Value("${spring.application.name:mini-mall-cloud}")
                                  ↑
                       默认值 (key 不存在或为空时用)
```

冒号后面是默认值, 没冒号且 key 不存在 → 启动报错 IllegalArgumentException.

## 76.9 累计文件

```
父 pom 改:
  mini-mall-common/pom.xml         (解开 <module>mini-mall-common-swagger</module>)

mini-mall-common-swagger/  (从 0 新建)
├── pom.xml                                  (只引 knife4j-starter + lombok)
└── src/main/java/com/minimall/common/swagger/config/
    └── SwaggerConfig.java                   (~50 行, OpenAPI bean)

5 个业务服务 pom 各加 1 段 dependency:
  mini-mall-user/pom.xml
  mini-mall-product/pom.xml
  mini-mall-order/pom.xml
  mini-mall-review/pom.xml
  mini-mall-search/pom.xml
```

## 76.10 ⭐ G9 + common-swagger 学到的核心知识 (合集)

延续 G9 的 10 大知识, common-swagger 再补 3 条:

11. **Spring Bean 扫描 + @ComponentScan("com.minimall")** — common 库的配置类被业务自动扫到, 不需要任何 @EnableXxx 注解
12. **Servlet 3.0 META-INF/resources/** — jar 里的静态资源自动映射到 web 根, Knife4j / WebJars 都用这个机制
13. **OpenAPI 3 + Knife4j** — Spring Boot 3 标准: knife4j-openapi3-jakarta-spring-boot-starter + io.swagger.v3.oas.models.* 注解

---

**common-swagger 完毕**. mini-mall-cloud 至此基础库 3/4: **core / redis / swagger**, 只剩 common-security 抽鉴权. 业务 100% + 文档统一, 项目"能展示" + "能讲清楚架构".
