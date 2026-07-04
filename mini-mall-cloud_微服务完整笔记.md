V# mini-mall-cloud 微服务完整学习笔记（合并版）

> 本文件由两份笔记合并而成：
> - **第 1～31 章 + 附录 A1/A2/A3**：来自《微服务基础知识点_v8.docx》——打地基 + A/B/C/D 阶段 + 底层原理补习。
> - **第 32～77 章**：来自《微服务E阶段_Nacos注册中心_笔记.md》——E/F/G/H/SEC 阶段的注册中心、配置中心、限流熔断、业务搬迁等。
>
> 合并日期：2026-06-24。原始两份文件保留未动。

---

## 📑 目录


**🔹 微服务基础知识点**

- [一、版本矩阵（最重要！版本错就全错）](#sec002)
- [二、Maven 多模块 4 层继承](#sec003)
  - [2.1 packaging 类型](#sec004)
  - [2.2 项目里 3 个真实 pom 文件解析](#sec005)
  - [2.3 一句话总结 3 层 pom 的差别](#sec006)
- [三、dependencyManagement vs dependencies（关键区分）](#sec007)
  - [3.1 BOM（Bill Of Materials）写法](#sec008)
  - [3.2 项目里 mini-mall-cloud/pom.xml 的 dependencyManagement 全貌](#sec009)
  - [3.3 项目里 mini-mall-common-core/pom.xml 怎么用上面锁的版本](#sec010)
- [四、依赖 scope 与 optional](#sec011)
  - [4.1 provided 的意义](#sec012)
  - [4.2 optional=true 的意义](#sec013)
  - [4.3 项目里 common-core 全部依赖一览（含 scope）](#sec014)
- [五、Spring Boot 3：jakarta 替代 javax](#sec015)
- [六、Spring Cloud 启动相关机制](#sec016)
  - [6.1 spring-cloud-starter-bootstrap（Boot 2.4+ 必加）](#sec017)
  - [6.2 AutoConfiguration.imports（Boot 3+ 替代 spring.factories）](#sec018)
- [七、统一响应封装 Result<T>](#sec019)
  - [7.1 静态工厂方法模式](#sec020)
  - [7.2 为什么 implements Serializable](#sec021)
- [八、全局异常处理](#sec022)
  - [8.1 BusinessException 业务异常](#sec023)
  - [8.2 GlobalExceptionHandler 全局兜底](#sec024)
  - [8.3 MethodArgumentNotValidException vs BindException 区别](#sec025)
- [九、用户上下文 SecurityContextHolder](#sec026)
  - [9.1 单体 vs 微服务实现对比](#sec027)
  - [9.2 TransmittableThreadLocal（TTL）](#sec028)
  - [9.3 用 Map<String,Object> 不用单字段](#sec029)
  - [9.4 必须 remove() 防泄漏](#sec030)
  - [9.5 项目里 SecurityContextHolder 完整代码](#sec031)
  - [9.6 对照单体 mini-mall 的 UserContext 看差距](#sec032)
- [十、Filter vs Interceptor（容易混）](#sec033)
  - [10.1 单体里 Interceptor 怎么注册（WebMvcConfig）](#sec034)
- [十一、JwtInterceptor 与 Gateway AuthGlobalFilter](#sec035)
  - [11.1 微服务里的【职责拆分】](#sec036)
  - [11.2 单体 JwtInterceptor 代码（mini-mall 项目实际代码）](#sec037)
  - [11.3 微服务 Gateway AuthGlobalFilter 代码（典型写法）](#sec038)
  - [11.4 业务服务 HeaderInterceptor 代码（典型写法）](#sec039)
- [十二、微服务用户透传 3 件套](#sec040)
- [十三、关键命令速查](#sec041)
- [十四、教学顺序：先具体后抽象](#sec042)
- [十五、项目当前状态与代码地图](#sec043)
  - [15.1 当前目录树](#sec044)
  - [15.2 已完成的检查点](#sec045)
  - [15.3 待办（下一步要做的）](#sec046)
- [十六、Java 异常体系（写 BusinessException 必懂）](#sec047)
  - [16.1 受检异常 vs 非受检异常](#sec048)
  - [16.2 为什么 BusinessException 继承 RuntimeException](#sec049)
  - [16.3 throw 之后不能写 return（Unreachable statement）](#sec050)
  - [16.4 @ExceptionHandler 匹配优先级](#sec051)
  - [16.5 参数校验异常：MethodArgumentNotValid vs Bind](#sec052)
- [十七、第一个真业务服务 mini-mall-user](#sec053)
  - [17.1 目录结构](#sec054)
  - [17.2 子模块 pom 关键点](#sec055)
  - [17.3 启动类关键注解](#sec056)
  - [17.4 application.yml 配置](#sec057)
  - [17.5 HelloController：验证 Result + 异常处理](#sec058)
- [十八、MyBatis-Plus 接 DB 详解](#sec059)
  - [18.1 User entity 关键注解](#sec060)
  - [18.2 三个核心注解作用](#sec061)
  - [18.3 UserMapper：为什么没有实现类却能用](#sec062)
  - [18.4 selectById 内部流程](#sec063)
  - [18.5 逻辑删除：MP 自动重写 SQL](#sec064)
  - [18.6 UserController 简单查询接口](#sec065)
  - [18.7 Mapper 依赖注入易错点速查](#sec066)
  - [18.8 MyBatis 分页三大方案对比](#sec066-8)
  - [18.9 本项目方案：MyBatis-Plus 分页实战](#sec066-9)
    - [18.9.6 完整端到端 demo（ADMIN.3 用户管理实例）](#sec066-9-6)
  - [18.10 原生方案 vs PageHelper 对照 demo](#sec066-10)
  - [18.11 ADMIN.5 真踩坑：order 服务漏配 PaginationInnerInterceptor](#sec066-11)
  - [18.12 深分页性能 + 优化方案](#sec066-12)
  - [18.13 #{} vs ${} 与 @Param 速查（分页高频考点）](#sec066-13)
  - [18.14 第十八章分页知识地图（一页脑图）](#sec066-14)
- [十九、登录注册与 JWT 鉴权](#sec067)
  - [19.0 B 阶段文件清单（新建 / 修改）](#sec068)
  - [19.1 这一节做了哪 8 件事](#sec069)
  - [19.2 新增依赖详解](#sec070)
  - [19.2.1 为什么 jjwt 拆 3 个包？](#sec071)
  - [19.3 BCrypt 密码加密原理](#sec072)
  - [19.4 JwtUtil 完整代码](#sec073)
  - [19.5 JWT 长啥样（base64 解码后）](#sec074)
  - [19.6 DTO 加参数校验](#sec075)
  - [19.6.1 UserLoginDTO 完整代码](#sec076)
  - [19.6.2 IUserService 完整代码](#sec077)
  - [19.7 UserServiceImpl 完整代码](#sec078)
  - [19.7.1 三大白嫖父类回顾](#sec079)
  - [19.7.2 UserController 完整代码（B 阶段后）](#sec080)
  - [19.8 application.yml JWT 配置](#sec081)
  - [19.9 实测：6 个场景全部跑通](#sec082)
  - [19.10 整体链路图：登录流程](#sec083)
  - [19.11 一句话总结](#sec084)
- [二十、跨服务调用与 OpenFeign](#sec085)
  - [20.0 C 阶段文件清单（整个新模块 + user 改动）](#sec086)
  - [19.1 问题起源：单体 vs 微服务的根本差别](#sec087)
  - [19.2 三种跨服务调用方案对比](#sec088)
  - [19.2.1 手写 RestTemplate 版本（反面教材）](#sec089)
  - [19.2.2 OpenFeign 版本（专业方案）](#sec090)
  - [19.3 Feign 的核心：动态代理](#sec091)
  - [19.4 @EnableFeignClients 启动类开关](#sec092)
  - [19.5 @FeignClient 注解参数详解](#sec093)
  - [19.5.1 name 参数（现阶段 vs 接 Nacos 后）](#sec094)
  - [19.5.2 接口方法的注解必须跟对方 Controller【镜像】](#sec095)
  - [19.6 数据契约：为啥用 Map 不用 Product](#sec096)
  - [19.7 完整调用链路图](#sec097)
  - [20.6.1 mini-mall-product/pom.xml 完整代码](#sec098)
  - [20.6.2 MiniMallProductApplication 启动类](#sec099)
  - [20.6.3 product/application.yml](#sec100)
  - [20.6.4 Product 实体类](#sec101)
  - [20.6.5 ProductMapper（含防超卖注解 SQL）](#sec102)
  - [20.6.6 ProductService 接口 + 实现](#sec103)
  - [20.6.7 ProductController（被 Feign 调的对象）](#sec104)
  - [20.6.8 ProductFeignClient（user 端的接口）](#sec105)
  - [20.6.9 UserController 加 Feign 演示接口](#sec106)
  - [20.6.10 MiniMallUserApplication 加 @EnableFeignClients](#sec107)
  - [20.6.11 实测响应](#sec108)
  - [20.8 旧版代码索引（简略对照）](#sec109)
  - [19.9 常见错误速查](#sec110)
  - [19.10 一句话总结](#sec111)

**🔹 mini-mall-cloud D 阶段：网关**

- [21.0 D 阶段全景](#sec113)
  - [D 阶段目标一句话](#sec114)
  - [加网关前 vs 后（架构图）](#sec115)
  - [4 小步路线图](#sec116)
- [21.1 基础：为什么需要网关](#sec117)
- [21.2 单体 JwtInterceptor 怎么变成微服务 Gateway 的](#sec118)
  - [21.2.1 单体里 JwtInterceptor 的完整代码](#sec119)
  - [21.2.2 userContext —— ThreadLocal 的封装](#sec120)
  - [21.2.3 ⭐ ThreadLocal 到底是什么？](#sec121)
  - [21.2.4 真实场景类比](#sec122)
  - [21.2.5 单体里为什么能用 ThreadLocal？](#sec123)
  - [21.2.6 微服务为什么不能用 ThreadLocal？](#sec124)
  - [21.2.7 微服务的解法：HTTP header 透传](#sec125)
  - [21.2.8 Gateway 里写 Filter 的代码（D3 预告）](#sec126)
  - [21.2.9 下游 Controller 怎么取 X-User-Id](#sec127)
  - [21.2.10 单体 vs 微服务 完整对比](#sec128)
- [21.3 基础：Spring MVC 是什么](#sec129)
  - [21.3.1 名字来历](#sec130)
  - [21.3.2 三个角色干什么](#sec131)
  - [21.3.3 用你的 login 接口走一遍](#sec132)
  - [21.3.4 为什么要这么分层？](#sec133)
  - [21.3.5 Spring MVC ≠ MVC（最容易混的点）](#sec134)
  - [21.3.6 你写的注解全是 Spring MVC](#sec135)
  - [21.3.7 MVC 跑在什么之上](#sec136)
- [21.4 基础：Spring WebFlux + Reactor](#sec137)
  - [21.4.1 一句话定位](#sec138)
  - [21.4.2 MVC（同步阻塞）vs WebFlux（异步非阻塞）](#sec139)
  - [21.4.3 真实场景类比](#sec140)
  - [21.4.4 Reactor 是什么](#sec141)
  - [21.4.5 Mono 和 Flux 是什么](#sec142)
  - [21.4.6 Mono 常见用法](#sec143)
  - [21.4.7 Gateway 里 Filter 长什么样](#sec144)
  - [21.4.8 Netty vs Tomcat](#sec145)
  - [21.4.9 MVC vs WebFlux 维度对比汇总](#sec146)
  - [21.4.10 spring-cloud-starter-gateway 拉了什么](#sec147)
- [21.5 基础：Spring Cloud Gateway 配置三件套](#sec148)
- [21.6 D 阶段完成后的项目结构](#sec149)
- [21.7 D1：建网关骨架（完整代码 + 行级讲解）](#sec150)
  - [21.7.1 父 pom 修改：加 modules](#sec151)
  - [21.7.2 mini-mall-gateway/pom.xml 完整代码](#sec152)
  - [21.7.2 pom 关键讲解](#sec153)
  - [21.7.3 MiniMallGatewayApplication.java 启动类完整代码](#sec154)
  - [21.7.4 application.yml D1 骨架版](#sec155)
- [21.8 D2：配静态路由（含 YAML 缩进死规则）](#sec156)
  - [21.8.1 D2 完整 yml](#sec157)
  - [21.8.2 字段含义详解](#sec158)
  - [21.8.3 一次完整请求的旅程](#sec159)
  - [21.8.4 YAML 缩进 4 大死规则（用户连踩 4 坑实录）](#sec160)
- [21.9 D 阶段三大踩坑实录](#sec161)
  - [坑 ①：Docker Desktop 占了 9000 / 8080 端口](#sec162)
  - [坑 ②：common-core 偷偷绑定了 Servlet（最大坑）](#sec163)
  - [坑 ③：fat jar 缓存了旧 common-core](#sec164)
- [21.10 D2 验证：三 jar 联调](#sec165)
  - [21.10.1 启动命令](#sec166)
  - [21.10.2 curl 测试](#sec167)
  - [21.10.3 实测结果（2026-06-19 00:53）](#sec168)
  - [21.10.4 数据链路证明图](#sec169)
- [21.11 接下来：D3 JWT 鉴权预告](#sec170)
- [附录：D 阶段常用命令速查](#sec171)
  - [21.12  D3 完整实现: GlobalFilter JWT 鉴权 + X-User-Id 透传](#sec172)
  - [21.13  D4 三 jar 联调实录 + 3 个新坑](#sec173)
- [22  底层补习总览(本章及之后全是新增)](#sec174)
  - [22.1 从开机到 /user/login: 整条链路全景](#sec175)
- [23  Spring IoC: 容器、Bean、@Autowired 真相](#sec176)
  - [23.1 IoC 一句话](#sec177)
  - [23.2 容器到底是个啥](#sec178)
  - [23.3 @Autowired 真相](#sec179)
  - [23.4 Bean 生命周期(简化版)](#sec180)
  - [23.5 项目里能用 IoC 看穿的代码](#sec181)
- [24  AOP + 代理: @ExceptionHandler 怎么'无侵入'生效](#sec182)
  - [24.1 AOP 是什么](#sec183)
  - [24.2 AOP 的原理: 代理对象偷偷换掉你的对象](#sec184)
  - [24.3 代理对象怎么生成(两种)](#sec185)
  - [24.4 @ExceptionHandler 怎么工作(AOP 具体案例)](#sec186)
  - [24.5 项目里哪些是 AOP?](#sec187)
- [25  Spring Boot 自动装配三件套](#sec188)
  - [25.1 为什么不用写一堆 XML 配置就能启动?](#sec189)
  - [25.2 @SpringBootApplication 三合一](#sec190)
  - [25.3 自动装配的物理触发机制](#sec191)
  - [25.4 项目里看得到的自动装配证据](#sec192)
- [26  ClassLoader + fat jar 解剖](#sec193)
  - [26.1 ClassLoader 是什么](#sec194)
  - [26.2 NoClassDefFoundError 是什么](#sec195)
  - [26.3 fat jar 解剖](#sec196)
- [27  HTTP 协议本身 + URI 五段](#sec197)
  - [27.1 HTTP 是个文本协议](#sec198)
  - [27.2 HTTP 响应也是文本](#sec199)
  - [27.3 URI 五段](#sec200)
- [28  Tomcat (BIO/NIO) vs Netty (事件循环)](#sec201)
  - [28.1 一句话定位](#sec202)
  - [28.2 Tomcat 线程模型(BIO 简化版)](#sec203)
  - [28.3 Netty 事件循环模型](#sec204)
  - [28.4 一图对比](#sec205)
- [29  动态代理统一论 (MyBatis Mapper / Feign / @Transactional 同一招)](#sec206)
  - [29.1 共同套路](#sec207)
  - [29.2 MyBatis UserMapper 的 selectById 调用过程](#sec208)
  - [29.3 Feign ProductFeignClient 的 getById 调用过程](#sec209)
  - [29.4 @Transactional 怎么开事务](#sec210)
  - [29.5 三大魔法对照表](#sec211)
  - [29.6 看清这一招后](#sec212)
- [30  ThreadLocal 内存模型 + TTL 跨线程拷贝原理](#sec213)
  - [30.1 ThreadLocal 内存模型(底层)](#sec214)
  - [30.2 ThreadLocalMap 的 key 是【弱引用】(关键!)](#sec215)
  - [30.3 SecurityContextHolder 项目代码回顾](#sec216)
  - [30.4 普通 ThreadLocal 在线程池下的【数据丢失】问题](#sec217)
  - [30.5 TTL (TransmittableThreadLocal) 的解决思路](#sec218)
- [31  JWT 签名验签(HMAC-SHA256) + BCrypt 加盐慢哈希](#sec219)
  - [31.1 JWT 是什么(更深一层)](#sec220)
  - [31.2 JWT 三段式结构](#sec221)
  - [31.3 验签算法(secret 在做什么)](#sec222)
  - [31.4 JJWT 库的 0.12+ 新 API 演变](#sec223)
  - [31.5 项目里 user-service 的 JwtUtil.generateToken 内部细节](#sec224)
  - [31.6 BCrypt 加盐慢哈希(更深一层)](#sec225)
  - [31.7 项目里登录的完整签名/验签流程](#sec226)
- [附录 A1  全阶段命令速查](#sec227)
- [附录 A2  端口规划 & 启动顺序](#sec228)
- [附录 A3  v8 修订说明](#sec229)

**🔹 第二部分 · E 阶段及以后（注册中心 → 配置中心 → 限流熔断 → 业务搬迁 → 分布式事务 → 安全）**

- [mini-mall-cloud E 阶段：Nacos 注册中心](#sec230)
  - [32.0  E 阶段全景](#sec231)
  - [32.1  基础：服务发现要解决什么问题](#sec232)
  - [32.2  Nacos 是什么](#sec233)
  - [32.3  E0：装 + 启 Nacos（Docker 方案）](#sec234)
  - [32.4  E1：3 服务接入 Nacos](#sec235)
  - [32.5  E2：Gateway 路由 lb:// 改造](#sec236)
  - [32.6  E3：Feign 去掉 url 硬编码](#sec237)
  - [32.7  E 阶段三大坑](#sec238)
  - [32.8  E 阶段成果对比](#sec239)
  - [32.9  附录 A：实测命令速查](#sec240)
- [第三十三章：F1 阶段 Nacos 配置中心](#sec241)
  - [33.0  F 阶段全景 + F1 目标](#sec242)
  - [33.1  F1 痛点（E 阶段后留下的）](#sec243)
  - [33.2  Nacos 配置中心是什么](#sec244)
  - [33.3  4 个核心概念（F1 必懂）](#sec245)
  - [33.4  F1 路线图](#sec246)
  - [33.5  F1.1：父 pom 加 nacos-config 依赖](#sec247)
  - [33.6  F1.2：user-service 建 bootstrap.yml](#sec248)
  - [33.7  F1.3：在 Nacos 创建配置项](#sec249)
  - [33.8  F1.4：重启 user-service 验证](#sec250)
  - [33.9  F1.5：默认 @Value 不动态刷新（教学坑）](#sec251)
  - [33.10  F1.6：加 @RefreshScope 实现动态刷新](#sec252)
  - [33.11  F1 底层原理（两条流程图）](#sec253)
  - [33.12  配置优先级规则](#sec254)
  - [33.13  F1 三大坑](#sec255)
  - [33.14  F1 目录结构变化](#sec256)
  - [33.15  F1 命令速查](#sec257)
  - [33.16  接下来：F 阶段后续预告](#sec258)
  - [33.17  章节地图（更新到 F2）](#sec259)
- [第三十四章：F2 阶段 Sentinel 限流熔断](#sec260)
  - [34.0  F2 阶段全景](#sec261)
  - [34.1  F2 痛点](#sec262)
  - [34.2  Sentinel 是什么 + 4 核心概念](#sec263)
  - [34.3  F2 路线图（4 阶段 10 步）](#sec264)
  - [34.4  F2.0：装 Sentinel Dashboard（Docker）](#sec265)
  - [34.5  F2.1：父 pom 加 sentinel-starter](#sec266)
  - [34.6  F2.2：3 服务 yml 配置](#sec267)
  - [34.7  F2.3：重打 + 启动 + Dashboard 验证](#sec268)
  - [34.8  F2.4：@SentinelResource + BlockHandler + Fallback](#sec269)
  - [34.9  F2.5：配限流规则 + curl 压测](#sec270)
  - [34.10  Sentinel 工作流程（源码级深入）](#sec271)
  - [34.11  F2 三大坑（真实踩过）](#sec272)
  - [34.12  F2 阶段成果对比](#sec273)
  - [34.13  F2 目录结构变化](#sec274)
  - [34.14  命令速查](#sec275)
  - [34.15  F2.6 实战：异常数熔断完整闭环](#sec276)
  - [34.16  F2.7 实战：网关全局限流](#sec277)
  - [34.17  深入：Sentinel 规则到底是什么](#sec278)
  - [34.18  F2.8 实战：系统保护（整机自动限流）](#sec279)
  - [34.19  F2.9 实战：规则持久化到 Nacos](#sec280)
  - [34.20  F 阶段总结 + 后续方向](#sec281)
  - [34.21  v8 → v9 完整章节地图](#sec282)
- [第 35 章  G 阶段开篇：从"治理就绪"到"业务搬迁"](#sec283)
  - [35.0  我们处在哪里？](#sec284)
  - [35.1  G 阶段路线图](#sec285)
  - [35.2  DDD 拆分提案 (我们目前在用的边界)](#sec286)
- [第 36 章  G3.0  建 mini-mall-order 空骨架](#sec287)
  - [36.1  目标](#sec288)
  - [36.2  文件结构图](#sec289)
  - [36.3  Step 1 — 父 pom 解开 module 注释](#sec290)
  - [36.4  Step 2 — `mini-mall-order/pom.xml`](#sec291)
  - [36.5  Step 3 — `application.yml`](#sec292)
  - [36.6  Step 4 — 启动类 `MiniMallOrderApplication.java`](#sec293)
  - [36.7  Step 5 — `HelloController.java`](#sec294)
  - [36.8  Step 6 — 编译 + 启动 + 验证](#sec295)
  - [36.9  Step 7 — 启动日志关键 3 行](#sec296)
- [第 37 章  G3.1  Category 搬到 product 服务](#sec297)
  - [37.1  搬迁清单](#sec298)
  - [37.2  Entity 代码 + 解析](#sec299)
  - [37.3  Mapper 代码 + 解析](#sec300)
  - [37.4  Service 接口 + 实现 + 解析](#sec301)
  - [37.5  Controller 代码 + 解析](#sec302)
- [第 38 章  G3.2  Address 搬到 user 服务](#sec303)
  - [38.1  为什么这章是【整轮搬迁的重头戏】？](#sec304)
  - [38.2  ⭐ 核心对比：单体 vs 微服务的上下文传递](#sec305)
  - [38.3  Entity / Mapper / Service / 实现 (4 个文件) — 与 Category 一样的套路](#sec306)
  - [38.4  Controller 完整代码 + 逐段解析](#sec307)
- [第 39 章  G3.3  联调验证 12 步](#sec308)
  - [39.1  网关路由配置补充](#sec309)
  - [39.2  12 步 curl 验证清单](#sec310)
  - [39.3  关键命令解析](#sec311)
  - [39.4  跑通后的验证结果](#sec312)
- [第 40 章  MyBatis-Plus 在微服务里的角色解析](#sec313)
  - [40.1  常见误解 vs 实际事实](#sec314)
  - [40.2  MP 在项目里的"分工图"](#sec315)
  - [40.3  Category/Address 5+5 接口 ↔ MP 方法 全映射](#sec316)
  - [40.4  关于"分页插件"——什么时候才需要补？](#sec317)
  - [40.5  关于"自动填充 createTime/updateTime"](#sec318)
- [第 41 章  本轮章节地图 + 待办清单](#sec319)
  - [41.1  v9 → v10 (本轮新增) 章节地图](#sec320)
  - [41.2  待办清单 (按优先级)](#sec321)
  - [41.3  能力清单 (到本轮结束)](#sec322)
- [第 42 章  G1  Redis 接入 (order 服务先吃螃蟹)](#sec323)
  - [42.1  目标 + 设计原则](#sec324)
  - [42.2  Redis 已在本机跑](#sec325)
  - [42.3  Step 1 — order/pom.xml 加 Redis starter](#sec326)
  - [42.4  Step 2 — order/application.yml 加 redis 配置](#sec327)
  - [42.5  Step 3 — 写 RedisConfig (核心)](#sec328)
  - [42.6  Step 4 — 写 RedisTestController](#sec329)
  - [42.7  Step 5 — 联调验证](#sec330)
- [第 43 章  G3.5  Favorite 搬到 product 服务 (Redis Set 实战)](#sec331)
  - [43.1  搬迁清单 + 关键差异](#sec332)
  - [43.2  Redis 数据结构选 Set 的理由](#sec333)
  - [43.3  product 接 Redis (第二份, feedback_concrete_first 实战)](#sec334)
  - [43.4  IFavoriteService 接口](#sec335)
  - [43.5  FavoriteServiceImpl 核心代码](#sec336)
  - [43.6  FavoriteController](#sec337)
  - [43.7  网关路由 + 验证](#sec338)
- [第 44 章  G3.4  CartItem 搬到 order (⭐ Feign 跨服务调用首次实战)](#sec339)
  - [44.1  这一章是【整轮搬迁的难度顶点】](#sec340)
  - [44.2  改 7 个文件 + 改启动类](#sec341)
  - [44.3  Step 1 — order/pom.xml 加 MP + MySQL](#sec342)
  - [44.4  Step 2 — order/application.yml 加 datasource](#sec343)
  - [44.5  Step 3 — 启动类加 @MapperScan + @EnableFeignClients](#sec344)
  - [44.6  Step 4 — 写 ProductFeignClient (核心)](#sec345)
  - [44.7  Step 5 — CartItemServiceImpl 核心改动](#sec346)
  - [44.8  Step 6 — CartItemController](#sec347)
  - [44.9  Step 7 — 网关路由 + 验证](#sec348)
- [第 45 章  踩坑实录: 单体留下的 cart 唯一索引 bug](#sec349)
  - [45.1  现象](#sec350)
  - [45.2  排查日志](#sec351)
  - [45.3  根因](#sec352)
  - [45.4  3 个修法](#sec353)
  - [45.5  本次绕过方法 (教学用)](#sec354)
- [第 46 章  本轮章节地图 + 累计能力 + 待办](#sec355)
  - [46.1  v10 → v11 (本轮新增) 章节地图](#sec356)
  - [46.2  累计能力清单](#sec357)
  - [46.3  待办清单 (按优先级)](#sec358)
  - [46.4  搬迁实战经验沉淀](#sec359)
- [Chapter 47 · G2 RabbitMQ 5 大核心概念](#sec360)
  - [47.1  四个角色: Producer / Consumer / Broker / Channel](#sec361)
  - [47.2  Queue (队列): 排队的地方](#sec362)
  - [47.3  Exchange (交换机): 路由器](#sec363)
  - [47.4  RoutingKey (路由键): 消息的"地址标签"](#sec364)
  - [47.5  Binding (绑定): "RoutingKey → Queue" 的路由规则](#sec365)
  - [47.6  四种 Exchange 类型 (路由"算法")](#sec366)
- [Chapter 48 · TTL + DLX 延迟队列 (单体最骚的设计)](#sec367)
  - [48.1  为啥要延迟队列? (业务背景)](#sec368)
  - [48.2  原理 (TTL + DLX 二合一)](#sec369)
  - [48.3  代码 (args Map 配置)](#sec370)
  - [48.4  实测验证 (G2.6 联调数据)](#sec371)
- [Chapter 49 · G2 代码全集 + 详细解释](#sec372)
  - [49.1  pom.xml — 一行 starter 拉一切](#sec373)
  - [49.2  application.yml — 4 类配置](#sec374)
  - [49.3  RabbitMQConfig.java — 3 组队列基建](#sec375)
  - [49.4  MqTestController.java — Producer 三连](#sec376)
  - [49.5  MqTestListener.java — Consumer 全套](#sec377)
- [Chapter 50 · G2 踩坑实录](#sec378)
  - [50.1  坑 ① Windows PATH 里 JDK 8 干扰](#sec379)
  - [50.2  坑 ② Docker 容器 Paused 状态](#sec380)
  - [50.3  坑 ③ guest/guest 不是万能默认](#sec381)
  - [50.4  3 坑共通: 排查"启动失败"的标准动作](#sec382)
- [Chapter 51 · G2 累计能力清单 + 待办](#sec383)
  - [51.1  G2 完成后能干啥](#sec384)
  - [51.2  G2 没做但已铺路的 (后续阶段补)](#sec385)
  - [51.3  G2 决策沉淀](#sec386)
  - [51.4  G2 搬迁经验沉淀](#sec387)
- [Chapter 52 · G3.7 Orders 搬迁 - 总述 + 架构图](#sec388)
  - [52.1  G3.7 是啥, 凭啥这么重](#sec389)
  - [52.2  端到端全景图 (服务交互)](#sec390)
  - [52.3  对比一下: Category (G3.1) vs Orders (G3.7) 的复杂度](#sec391)
- [Chapter 53 · createOrder 9 步深度解析](#sec392)
  - [53.1  全貌 (代码骨架)](#sec393)
  - [53.2  为啥锁要包在事务外? (经典面试题)](#sec394)
  - [53.3  为啥 MQ 要在事务提交后发?](#sec395)
  - [53.4  7 步业务流程逐一解析](#sec396)
- [Chapter 54 · 真业务 MQ 通路 (G2 延伸到这里才闭环)](#sec397)
  - [54.1  从 demo 走到真业务](#sec398)
  - [54.2  closeOrderByMQ 的幂等 (核心代码 + 解析)](#sec399)
  - [54.3  Listener 完整代码 + 解析](#sec400)
- [Chapter 55 · G3.7 代码全集](#sec401)
  - [55.1  17 个新文件 (按职责分类)](#sec402)
  - [55.2  跨服务调用矩阵](#sec403)
  - [55.3  状态机迁移 (5 态 + 3 路径)](#sec404)
- [Chapter 56 · G3.7 踩坑实录 + 累计能力 + 待办](#sec405)
  - [56.1  踩坑 ① PowerShell + curl + 中文 → UTF-8 解析炸](#sec406)
  - [56.2  踩坑 ② yml 全局 logic-delete-field 反扑 cart bug](#sec407)
  - [56.3  G3.7 累计能力清单](#sec408)
  - [56.4  G3.7 没做但已铺路的 (后续)](#sec409)
  - [56.5  G3.7 决策沉淀](#sec410)
  - [56.6  G3.7 搬迁经验沉淀](#sec411)
- [Chapter 57 · G3.8 Seckill 搬迁 - 总述 + 架构图](#sec412)
  - [57.1  G3.8 是啥, 它解决的问题](#sec413)
  - [57.2  端到端架构图](#sec414)
  - [57.3  vs G3.7 Orders 复杂度对比](#sec415)
- [Chapter 58 · Lua 脚本深度解析 (G3.8 最炫的 5 行)](#sec416)
  - [58.1  为啥要 Lua? 朴素 Java 做不到啥?](#sec417)
  - [58.2  5 行 Lua 逐行讲](#sec418)
  - [58.3  KEYS 和 ARGV 的设计为啥分开?](#sec419)
  - [58.4  返回值约定 (面试高频)](#sec420)
  - [58.5  Spring 怎么注册 Lua 脚本 (EVALSHA 优化)](#sec421)
- [Chapter 59 · MQ 异步下单 + 轮询 3 态查询](#sec422)
  - [59.1  为啥 Lua 抢到了不直接写 DB?](#sec423)
  - [59.2  消息体设计 "activityId:userId"](#sec424)
  - [59.3  幂等设计: SeckillOrderListener 的核心](#sec425)
  - [59.4  3 态轮询查询 (前端用户体验关键)](#sec426)
- [Chapter 60 · G3.8 代码全集](#sec427)
  - [60.1  11 个新文件 + 1 Lua 资源 (按职责分类)](#sec428)
  - [60.2  Lua + Java 调用 (核心 5 行代码)](#sec429)
  - [60.3  跨服务调用矩阵 (vs G3.7 比)](#sec430)
  - [60.4  端到端实测时间线 (G3.8.9 真实数据)](#sec431)
- [Chapter 61 · G3.8 决策 + 累计能力 + 待办](#sec432)
  - [61.1  G3.8 关键决策](#sec433)
  - [61.2  G3.8 累计能力](#sec434)
  - [61.3  G3.8 没做但已铺路的 (后续)](#sec447)
  - [61.4  G3.8 搬迁经验沉淀](#sec448)
- [62. G3.9 — 补 Product 模块缺失的 5 个方法](#sec449)
  - [62.1  背景: 全面 diff 发现的洞](#sec450)
  - [62.2  搬的 5 个方法逐个解释](#sec451)
  - [62.3  MybatisPlusConfig (分页插件)](#sec452)
  - [62.4  G3.9 大坑: MP 3.5.9 把分页插件拆包了](#sec453)
  - [62.5  G3.9 第二个大坑: Jackson 默认不会序列化 LocalDateTime](#sec454)
  - [62.6  G3.9 累计能力](#sec455)
- [63. G3.10 — 补扣库存断点 (业务真闭环)](#sec456)
  - [63.1  背景: G3.7 留的债](#sec457)
  - [63.2  方案: order 通过 Feign 调 product 扣库存](#sec458)
  - [63.3  product 服务暴露 2 个内部端点](#sec459)
  - [63.4  ProductFeignClient 加 2 方法](#sec460)
  - [63.5  createOrder 加扣库存循环](#sec461)
  - [63.6  cancelOrder + closeOrderByMQ 加回库存](#sec462)
  - [63.7  原子扣库存 SQL 的一致性](#sec463)
  - [63.8  G3.10 累计能力](#sec464)
- [64. H1 — Feign Fallback 服务降级](#sec465)
  - [64.1  问题: 下游挂了, 上游裸抛 5xx](#sec466)
  - [64.2  Feign Fallback 是什么](#sec467)
  - [64.3  3 个 Fallback 类全部代码](#sec468)
  - [64.4  关键开关: feign.sentinel.enabled](#sec469)
  - [64.5  验证: 杀 product, 看 fallback 日志](#sec470)
  - [64.6  H1 累计能力](#sec471)
- [65. H2 — 过时注释纠错 (小但重要)](#sec472)
  - [65.1  背景](#sec473)
  - [65.2  改成准确版](#sec474)
  - [65.3  H2 经验](#sec475)
- [66. G5 — Seata AT 分布式事务](#sec476)
  - [66.1  问题再现: 单体的招在微服务为啥失灵](#sec477)
  - [66.2  Seata AT 模式核心原理](#sec478)
  - [66.3  Seata Server 部署 (Docker file 模式)](#sec479)
  - [66.4  客户端依赖 (BOM 锁版本)](#sec480)
  - [66.5  客户端 yml 配置](#sec481)
  - [66.6  @GlobalTransactional 注解](#sec482)
  - [66.7  SeataTestController (端到端验证神器)](#sec483)
  - [66.8  端到端验证 5 步](#sec484)
  - [66.9  XID 怎么从 order 透传到 product](#sec485)
  - [66.10  G5 大坑回顾](#sec486)
  - [66.11  AT vs TCC vs SAGA 简表 (面试问到)](#sec487)
  - [66.12  G5 累计能力](#sec488)
  - [66.13  G5 经验沉淀](#sec489)
- [67. G6 — 物流 / 签收 (开篇 + 状态机图)](#sec490)
  - [67.1  G6 是啥, 凭啥做](#sec491)
  - [67.2  状态机文字版图](#sec492)
  - [67.3  G6 整体决策 (3 个关键)](#sec493)
- [68. 有限状态机 FSM 工程实现](#sec494)
  - [68.1  什么是有限状态机 (FSM)](#sec495)
  - [68.2  FSM 在工程里就是 4 步前置校验](#sec496)
  - [68.3  为啥要 `.equals` 不用 `==`](#sec497)
  - [68.4  G6 用 FSM 的 2 个真实例子](#sec498)
  - [68.5  ship vs sign 差异对照表 (理解为啥这么设计)](#sec499)
  - [68.6  状态机要不要上 Spring StateMachine?](#sec500)
- [69. @Scheduled 定时任务深度解析](#sec501)
  - [69.1  Spring 定时调度 3 件套](#sec502)
  - [69.2  cron 表达式 6 字段速记](#sec503)
  - [69.3  @Scheduled 4 种触发方式](#sec504)
  - [69.4  单机调度的致命局限 ⚠️](#sec505)
  - [69.5  分布式调度 3 大方案 (面试常问)](#sec506)
- [70. 单体 MQ 延迟队列 vs 微服务 @Scheduled (选型对比) ⭐⭐⭐](#sec507)
  - [70.1  两个场景, 选型截然不同](#sec508)
  - [70.2  选型决策表](#sec509)
  - [70.3  为啥 30 分钟关单选 MQ?](#sec510)
  - [70.4  为啥 7 天签收选 @Scheduled?](#sec511)
  - [70.5  错配的代价](#sec512)
  - [70.6  万能选型法则 (记住这 3 句话)](#sec513)
  - [70.7  单体里这件事的真相](#sec514)
- [71. G6 代码全集 + 解释](#sec515)
  - [71.1  改了哪些文件 (一图速览)](#sec516)
  - [71.2  SQL DDL](#sec517)
  - [71.3  Orders entity 加字段](#sec518)
  - [71.4  ShipOrderDTO (NEW)](#sec519)
  - [71.5  OrderDetailVO 加 2 字段](#sec520)
  - [71.6  IOrdersService 接口](#sec521)
  - [71.7  OrdersServiceImpl 实现 (已在 68.4 节展示, 此处略)](#sec522)
  - [71.8  OrdersController 加端点](#sec523)
  - [71.9  启动类加 @EnableScheduling](#sec524)
  - [71.10  LogisticsScheduledTask (最炫的一段)](#sec525)
- [72. G6 踩坑实录 + 累计能力 + 待办](#sec526)
  - [72.1  坑 ① Seata AOP 把 BusinessException 包成了 RuntimeException ⭐⭐⭐](#sec527)
  - [72.2  坑 ② Controller 路径前缀重复](#sec528)
  - [72.3  坑 ③ @PathVariable / @RequestHeader 用错](#sec529)
  - [72.4  坑 ④ Java 8 path 干扰 (重复出现)](#sec530)
  - [72.5  G6 端到端验证完整记录](#sec531)
  - [72.6  G6 完成后能干啥](#sec532)
  - [72.7  G6 决策沉淀](#sec533)
  - [72.8  G6 没做但已铺路的 (后续阶段补)](#sec534)
  - [72.9  累计 G6 关键文件](#sec535)
- [73. G7 商品评价 (mini-mall-review, 9004)](#sec536)
  - [73.1  G7 目标 & 设计决策](#sec537)
  - [73.2  G7.1 SQL DDL (踩坑: 存储过程报错)](#sec538)
  - [73.3  G7.2~G7.6 模块骨架 + 业务实现](#sec539)
  - [73.4  ⭐ 业务规则编排 - 4 步校验链](#sec540)
  - [73.5  ⭐⭐ G7.E2E 踩的最关键的坑 - 跨服务事务可见性](#sec541)
  - [73.6  Cache Aside 写策略 (顺手实现)](#sec542)
  - [73.7  G7 完整端到端验证记录](#sec543)
  - [73.8  常见踩坑速查](#sec544)
  - [73.9  N=3 决策回顾 - 为啥还没抽 common-redis](#sec545)
  - [73.10  G7 累计文件](#sec546)
  - [73.11  G7 教学速查 - 4 个零散知识点补全](#sec547)
- [74. G8 优惠券 (跨服务用券抵扣)](#sec548)
  - [74.1  G8 目标 & 设计决策](#sec549)
  - [74.2  G8 核心业务流](#sec550)
  - [74.3  ⭐ 教学要点 - 3 处 CAS 思想](#sec551)
  - [74.4  ⭐⭐ 教学限制 - user 没接 Seata 的脏数据风险](#sec552)
  - [74.5  G8 端到端验证记录 (7 项全过)](#sec553)
  - [74.6  G8 踩的小坑速查](#sec554)
  - [74.7  G8 教学速查 - 新出现的 4 个 API](#sec555)
  - [74.8  G8 累计文件](#sec556)
- [75. G9 - ES 商品搜索服务 (mini-mall-search)](#sec557)
  - [75.0 总览 - G9 干了啥](#sec558)
  - [75.1 mini-mall-search 模块脚手架](#sec559)
  - [75.2 ProductDocument - ES 索引映射类](#sec560)
  - [75.3 DTO / VO / 数据载体三剑客](#sec561)
  - [75.4 ⭐ 微服务铁律 - ProductSource + Feign](#sec562)
  - [75.5 Spring Data ES Repository - ES 版的 MyBatis-Plus](#sec563)
  - [75.6 IProductSearchService 接口 + 实现分离](#sec564)
  - [75.7 ProductSearchServiceImpl - sync 系列 (写 ES)](#sec565)
  - [75.8 ⭐ search 方法 - G9 最难一步 (读 ES)](#sec566)
  - [75.9 ProductSearchController + 网关路由](#sec567)
  - [75.10 端到端验证 (curl 实测)](#sec568)
  - [75.11 G9 踩的小坑速查](#sec569)
  - [75.12 G9 教学速查 - 关键 API & 模式](#sec570)
  - [75.13 G9 累计文件](#sec571)
  - [75.14 ⭐ G9 学到的 10 个核心知识点](#sec572)
- [76. common-swagger - Knife4j 公共配置](#sec573)
  - [76.0 总览 - 做了啥](#sec574)
  - [76.1 4 个相关概念关系 (必须区分)](#sec575)
  - [76.2 mini-mall-common-swagger 子模块](#sec576)
  - [76.3 SwaggerConfig.java (核心)](#sec577)
  - [76.4 5 个业务服务 pom 加依赖](#sec578)
  - [76.5 ⭐ /doc.html 为啥不用配置就能访问 (Servlet 3.0 规范)](#sec579)
  - [76.6 端到端验证](#sec580)
  - [76.7 踩的小坑速查](#sec581)
  - [76.8 教学速查 - 5 个新概念](#sec582)
  - [76.9 累计文件](#sec583)
  - [76.10 ⭐ G9 + common-swagger 学到的核心知识 (合集)](#sec584)
- [Chapter 77 · SEC 阶段 · common-security 抽取](#sec435)
  - [77.1  这次做了什么](#sec436)
  - [77.2  一次完整跨服务调用的链路](#sec437)
  - [77.3  common-security 4 大组件](#sec438)
  - [77.4  HeaderInterceptor 源码 (进站)](#sec439)
  - [77.5  FeignAuthInterceptor 源码 (出站)](#sec440)
  - [77.6  ⭐ 最深的坑 — @ConditionalOnClass + @Import 不能混用](#sec441)
  - [77.7  Controller 改造范围 (SEC.10)](#sec442)
  - [77.8  Feign Client 改造范围 (SEC.11)](#sec443)
  - [77.9  端到端验证 (SEC.12)](#sec444)
  - [77.10  踩坑实录](#sec445)
  - [77.11  本轮累计能力](#sec446)
- [Chapter 78 · OAUTH 阶段 · GitHub 第三方登录 + Java 通用知识深挖](#sec700)
  - [78.1  这次做了什么](#sec701)
  - [78.2  Authorization Code Flow 4 步链路](#sec702)
  - [78.3  关键安全设计 (为什么是 code 而不是 access_token)](#sec703)
  - [78.4  踩坑实录 (8 大坑)](#sec704)
  - [78.5  📄 Java 通用知识深挖 (独立 docx)](#sec705)
  - [78.6  本轮累计能力](#sec706)
- [Chapter 79 · AUTH 阶段 · 抽认证服务 mini-mall-auth](#sec800)
  - [79.1  这次做了什么 (1 句话)](#sec801)
  - [79.2  抽完后的服务架构图](#sec802)
  - [79.3  internal 接口模式 (auth 不直连 DB, Feign 调 user)](#sec803)
  - [79.4  踩坑实录 (5 大坑)](#sec804)
  - [79.5  端到端验证 (6/6 通过)](#sec805)
  - [79.6  本轮累计能力 + 设计反思](#sec806)
- [第 80 章 · C 端商城 + 越权安全专题](#sec810)
  - [80.1  RBAC 角色权限体系 (ADMIN 阶段地基)](#sec811)
  - [80.2  对象存储 MinIO (FILE 服务)](#sec812)
  - [80.3  DTO / VO 分层 + 参数校验 (/user/me)](#sec813)
  - [80.4  MyBatis-Plus:一个字段存多张图 (商品缩略图)](#sec814)
  - [80.5  地址默认互斥 (MP null 不更新 + 事务)](#sec815)
  - [80.6  越权安全专题 ⭐ (含网关白名单 / WebFlux / needAdmin)](#sec816)
  - [80.7  数据规范:分类图标该存什么](#sec817)
  - [80.8  上线准备:Docker 化 (刚起步)](#sec818)
  - [80.9  环境 / 打包 踩坑速查](#sec819)
  - [80.10  这一章的主线 (一条链)](#sec820)


# 微服务基础知识点 <a id="sec001"></a>

从 mini-mall 单体到 mini-mall-cloud 微服务，搭建过程涉及的全部基础概念

项目：mini-mall-cloud (Spring Boot 3 + Spring Cloud 2023 + Spring Cloud Alibaba 2023)

整理日期：2026-06-18

## 一、版本矩阵（最重要！版本错就全错） <a id="sec002"></a>

Spring Boot / Spring Cloud / Spring Cloud Alibaba 三方版本必须严格对齐，不能乱配。

项目选用版本：

| 组件 | 版本 | 说明 |
|---|---|---|
| Spring Boot | 3.3.5 | JDK 17 + 最新稳定 LTS |
| Spring Cloud | 2023.0.3 | 对应 Boot 3.3.x |
| Spring Cloud Alibaba | 2023.0.1.2 | 对应 SC 2023.0.x |
| MyBatis-Plus | 3.5.9 | Boot 3 兼容版 |
| JJWT | 0.12.6 | 新 API（旧 0.9 用法已废弃） |
| Knife4j | 4.5.0 | OpenAPI 3 + Swagger UI |
| MinIO | 8.5.11 | 对象存储客户端 |
| Hutool | 5.8.27 | 全家桶工具类 |
| Fastjson2 | 2.0.53 | 阿里 JSON 库 |
| TransmittableThreadLocal | 2.14.5 | 线程池上下文透传 |

查版本对应表的官方地址：

https://github.com/alibaba/spring-cloud-alibaba/wiki/版本说明

## 二、Maven 多模块 4 层继承 <a id="sec003"></a>

微服务项目的 pom 是金字塔结构，越往上越通用，越往下越具体。

mini-mall-cloud 的继承链：

```
spring-boot-starter-parent  ← Spring 官方根 pom（定义所有 Spring 系版本）
        ↑
mini-mall-cloud (根 pom)     ← 我们的项目根（定义 SC / SCA / 业务依赖版本）
        ↑
mini-mall-common             ← 公共模块聚合器（packaging=pom，不生成 jar）
        ↑
mini-mall-common-core        ← 真正的代码模块（packaging=jar）
```

### 2.1 packaging 类型 <a id="sec004"></a>

| packaging | 含义 | 什么时候用 |
|---|---|---|
| jar（默认） | 编译成 jar 包 | 实际的代码模块 |
| pom | 只起聚合/继承作用，不生成产物 | 父模块、聚合模块 |
| war | 编译成 war（部署到 Tomcat） | 传统 Servlet 项目 |

### 2.2 项目里 3 个真实 pom 文件解析 <a id="sec005"></a>

下面把项目里 3 个 pom 文件依次贴出来，看每一层在做什么。

📁 第 1 层 mini-mall-cloud/pom.xml（根 pom）

```
<!-- ① parent 指向 Spring 官方根 pom，自动锁所有 Boot 依赖版本 -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
    <relativePath/>
</parent>

<!-- ② 自己的坐标 -->
<groupId>com.minimall</groupId>
<artifactId>mini-mall-cloud</artifactId>
<version>0.0.1-SNAPSHOT</version>
<packaging>pom</packaging>          ← 关键：自己也是 pom，不打 jar

<!-- ③ 列出直接子模块 -->
<modules>
    <module>mini-mall-common</module>
    <!-- 后续解开
    <module>mini-mall-api</module>
    <module>mini-mall-auth</module>
    <module>mini-mall-gateway</module>
    <module>mini-mall-modules</module>
    -->
</modules>
```
作用：定义微服务全局版本（SC / SCA / MP / JWT 等），声明子模块，加阿里云镜像。

📁 第 2 层 mini-mall-common/pom.xml（公共聚合器）

```
<!-- ① parent 指向根 pom mini-mall-cloud
     这样能拿到根 pom 里 dependencyManagement 锁好的所有版本 -->
<parent>
    <groupId>com.minimall</groupId>
    <artifactId>mini-mall-cloud</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</parent>

<!-- ② 自己也是聚合层，packaging=pom -->
<artifactId>mini-mall-common</artifactId>
<packaging>pom</packaging>          ← 关键：聚合层不出 jar

<!-- ③ 子模块顺序：core 必须最前面（其他都要依赖它） -->
<modules>
    <module>mini-mall-common-core</module>
    <!-- 后续解开
    <module>mini-mall-common-security</module>
    <module>mini-mall-common-redis</module>
    <module>mini-mall-common-swagger</module>
    -->
</modules>
```
作用：纯粹的「目录组织」。把所有 common-xxx 子模块分组放一起，自己不写一行代码。

📁 第 3 层 mini-mall-common/mini-mall-common-core/pom.xml（真正的代码）

```
<!-- ① parent 指向 mini-mall-common（不是根 pom）
     Maven 4 层继承：core ─父─> common ─父─> mini-mall-cloud ─父─> spring-boot-starter-parent -->
<parent>
    <groupId>com.minimall</groupId>
    <artifactId>mini-mall-common</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</parent>

<!-- ② 自己的坐标，不写 packaging 默认 jar -->
<artifactId>mini-mall-common-core</artifactId>
<name>mini-mall-common-core</name>

<!-- ③ 真正的代码模块才写 dependencies -->
<dependencies>
    <!-- Feign / LoadBalancer / Web / Servlet / Lombok / TTL / Validation 等 -->
    ...
</dependencies>
```
作用：写 Result.java / BusinessException.java 等真实 Java 代码。

打成 jar 后被其他模块引用。

### 2.3 一句话总结 3 层 pom 的差别 <a id="sec006"></a>

| 层级 | packaging | 写代码吗 | modules 标签 | parent 指向 |
|---|---|---|---|---|
| mini-mall-cloud（根） | pom | ✗ | 有（列子模块） | Spring 官方 |
| mini-mall-common（聚合） | pom | ✗ | 有（列子模块） | mini-mall-cloud |
| mini-mall-common-core（代码） | jar（默认） | ✓ | 无 | mini-mall-common |

记忆点：

• 有 <modules> 标签 = 聚合层 = packaging=pom = 不写代码

• 没 <modules> 标签 = 代码层 = packaging=jar（默认）= 写真实 Java 类

## 三、dependencyManagement vs dependencies（关键区分） <a id="sec007"></a>

这是父 pom 设计最容易搞混的点。

|  | dependencyManagement | dependencies |
|---|---|---|
| 位置 | 父 pom（推荐） | 父 pom 或子 pom |
| 作用 | 只锁版本，不引入 | 引入并传递 |
| 子模块要不要写 | 要再写 <dependency> 才生效 | 自动继承下来 |
| 写 version | 必须写 | 可以省略（用 management 里的） |
| 典型用法 | BOM / 业务依赖统一管理 | 全模块都用的核心库 |

### 3.1 BOM（Bill Of Materials）写法 <a id="sec008"></a>

```
<dependencyManagement>
    <dependencies>
        <!-- Spring Cloud BOM -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>          ← 关键：导入整个 pom 里的版本定义
            <scope>import</scope>      ← 关键：仅导入版本管理信息
        </dependency>
    </dependencies>
</dependencyManagement>
```
type=pom + scope=import 是导入 BOM 的固定写法。

### 3.2 项目里 mini-mall-cloud/pom.xml 的 dependencyManagement 全貌 <a id="sec009"></a>

📁 文件：mini-mall-cloud/pom.xml 第 68~170 行

```
<dependencyManagement>
    <dependencies>

        <!-- ① Spring Cloud BOM —— 锁所有 Spring Cloud 子项目版本 -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <!-- ② Spring Cloud Alibaba BOM —— Nacos/Sentinel/Seata 等 -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>${spring-cloud-alibaba.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <!-- ③ 业务工具（单个依赖锁版本，不是 BOM） -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- ④ JWT 三件套 / Knife4j / MinIO / Hutool / Fastjson2 / TTL ... -->
        <!-- 省略类似写法 -->

        <!-- ⑤ ⭐ 内部 4 个公共模块的版本管理（让子模块引时不写 version） -->
        <dependency>
            <groupId>com.minimall</groupId>
            <artifactId>mini-mall-common-core</artifactId>
            <version>${minimall.version}</version>
        </dependency>
        <dependency>
            <groupId>com.minimall</groupId>
            <artifactId>mini-mall-common-security</artifactId>
            <version>${minimall.version}</version>
        </dependency>
        <!-- 其余 redis/swagger 略 -->

    </dependencies>
</dependencyManagement>
```
⭐ 关键观察：

• 这里【只锁版本不引入】——子模块写 <dependency> 才生效

• 子模块写 <dependency> 时【不用写 version】，自动用这里的

• 加自己内部的 mini-mall-common-* 也写在这（这样 mini-mall-user 引 core 时不用写版本）

### 3.3 项目里 mini-mall-common-core/pom.xml 怎么用上面锁的版本 <a id="sec010"></a>

📁 文件：mini-mall-common-core/pom.xml

```
<dependencies>
    <!-- 注意：所有依赖【都没写 version】，全靠根 pom 的 dependencyManagement 锁版本 -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-openfeign</artifactId>
        <!-- 没写 version → 自动用 SC BOM 里的版本 -->
    </dependency>
    <dependency>
        <groupId>com.alibaba</groupId>
        <artifactId>transmittable-thread-local</artifactId>
        <!-- 没写 version → 自动用根 pom dependencyManagement 里锁的 2.14.5 -->
    </dependency>
</dependencies>
```
这就是 dependencyManagement 的核心好处：版本统一管理，子模块写起来干净。

## 四、依赖 scope 与 optional <a id="sec011"></a>

| scope | 编译时 | 运行时 | 传递给下游 | 典型例子 |
|---|---|---|---|---|
| compile（默认） | ✓ | ✓ | ✓ | spring-web |
| provided | ✓ | ✗（由容器提供） | ✗ | jakarta.servlet-api |
| runtime | ✗ | ✓ | ✓ | mysql-connector |
| test | 测试时 | 测试时 | ✗ | junit |

### 4.1 provided 的意义 <a id="sec012"></a>

打包时不打进 jar/war，因为容器（Tomcat）会提供。

如果打进去，会跟 Tomcat 自带的冲突报错。

📁 文件：mini-mall-common-core/pom.xml 第 51~56 行

```
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <scope>provided</scope>
    <!-- ⭐ provided：编译时需要（GlobalExceptionHandler 要 HttpServletRequest），
                     运行时由 Tomcat 提供，不打进 jar -->
</dependency>

```

### 4.2 optional=true 的意义 <a id="sec013"></a>

```
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>      ← 不传递给依赖本模块的下游
</dependency>
```
Lombok 是【编译期】注解处理器，下游模块自己用自己的，不需要从 core 透传。

📁 文件：mini-mall-common-core/pom.xml 第 59~64 行

### 4.3 项目里 common-core 全部依赖一览（含 scope） <a id="sec014"></a>

把所有依赖列出来，看每个 scope 都是什么：

| 依赖 | scope | 为什么这样设？ |
|---|---|---|
| spring-cloud-starter-openfeign | compile（默认） | 运行时也要用，全模块共享 |
| spring-cloud-starter-loadbalancer | compile | Feign 调远程要 LB 选实例 |
| spring-web | compile | Result 要返 HTTP 响应 |
| jakarta.servlet-api | provided | Tomcat 会提供，不打 jar |
| lombok | compile + optional=true | 编译期注解处理器，不传递下游 |
| transmittable-thread-local | compile | SecurityContextHolder 必须用它 |
| spring-boot-starter-validation | compile | GlobalExceptionHandler 要 @Valid 异常类 |
| commons-lang3 | compile | StringUtils 等工具类 |

## 五、Spring Boot 3：jakarta 替代 javax <a id="sec015"></a>

Boot 2.x 用 javax.*，Boot 3.x 全部改成 jakarta.*。

Servlet 5（Jakarta EE 9）开始包名从 javax → jakarta，这是 Oracle 把 Java EE 捐给 Eclipse 基金会后必须改的。

| Boot 2.x（旧） | Boot 3.x（新） |
|---|---|
| javax.servlet.http.HttpServletRequest | jakarta.servlet.http.HttpServletRequest |
| javax.servlet.Filter | jakarta.servlet.Filter |
| javax.validation.Valid | jakarta.validation.Valid |
| javax.annotation.PostConstruct | jakarta.annotation.PostConstruct |

⚠️ 把单体（Boot 3）的代码迁过来时如果误用 javax 包，会一片飘红。

📁 项目里 GlobalExceptionHandler 的 import 验证

文件：mini-mall-common-core/src/main/java/com/minimall/common/core/exception/GlobalExceptionHandler.java

```
package com.minimall.common.core.exception;

import com.minimall.common.core.domain.Result;
import jakarta.servlet.http.HttpServletRequest;          ← ⭐ 不是 javax.servlet
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
```
Boot 3 项目所有 servlet 相关都得 jakarta 包，IDE 飘红时先看是不是写成 javax 了。

## 六、Spring Cloud 启动相关机制 <a id="sec016"></a>

### 6.1 spring-cloud-starter-bootstrap（Boot 2.4+ 必加） <a id="sec017"></a>

Spring Cloud 默认从 bootstrap.yml 读 Nacos 地址，但 Boot 2.4 起，bootstrap.yml 不再默认加载。

必须显式引入这个 starter，否则 application.yml 里的 nacos 配置不会生效。

```
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bootstrap</artifactId>
</dependency>
```
📁 项目里在 mini-mall-cloud/pom.xml 第 177~182 行

```
<!-- ⭐ 父级 <dependencies>：强制所有子模块都有
     spring-cloud-starter-bootstrap 让所有子模块能读 bootstrap.yml
     （不加这个，Boot 2.4+ 不会加载 bootstrap.yml，连 Nacos 都连不上） -->
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-bootstrap</artifactId>
    </dependency>
</dependencies>
```
⭐ 注意：这是放在【根 pom 的 <dependencies>】（不是 dependencyManagement），

意思是所有子模块自动继承，不用每个 pom 都写一遍。

### 6.2 AutoConfiguration.imports（Boot 3+ 替代 spring.factories） <a id="sec018"></a>

旧机制：META-INF/spring.factories

新机制：META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

```
# 写法：每行一个全限定类名
com.minimall.common.security.config.HeaderInterceptorConfig
com.minimall.common.security.config.FeignAutoConfig
```
Spring Boot 启动时会自动扫描这个文件，注册里面列出的 @Configuration 类。

作用：让 common-* 模块「开箱即用」——业务模块只要引入依赖，配置就生效。

## 七、统一响应封装 Result<T> <a id="sec019"></a>

所有 Controller 返回值统一格式：{ code, message, data }

📁 文件：mini-mall-common-core/src/main/java/com/minimall/common/core/domain/Result.java

```
package com.minimall.common.core.domain;

@Data @AllArgsConstructor @NoArgsConstructor
public class Result<T> implements Serializable {

    private Integer code;       // 状态码（200=成功 / 其他=失败）
    private String message;     // 响应消息
    private T data;             // 业务数据

    // ─── 静态工厂方法（业务代码用这些） ───

    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
```

### 7.1 静态工厂方法模式 <a id="sec020"></a>

不用 new Result(200, ...)，而是 Result.success(data)，可读性更高。

好处：可以加日志、加默认值、加业务规则，调用方不感知。

### 7.2 为什么 implements Serializable <a id="sec021"></a>

Spring Cloud 中 Feign 调用、Redis 缓存、消息队列序列化都需要类实现 Serializable。

## 八、全局异常处理 <a id="sec022"></a>

### 8.1 BusinessException 业务异常 <a id="sec023"></a>

📁 文件：mini-mall-common-core/src/main/java/com/minimall/common/core/exception/BusinessException.java

```
package com.minimall.common.core.exception;

@Getter
public class BusinessException extends RuntimeException {

    /** 业务状态码（前端用它区分错误类型，比如 401 跳登录页） */
    private final Integer code;

    /** 构造 1：只传 message，code 默认 500 */
    public BusinessException(String message) {
        super(message);            // 调父类 RuntimeException(message)
        this.code = 500;
    }

    /** 构造 2：自定义 code 和 message */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
```
为什么继承 RuntimeException 而不是 Exception？

RuntimeException = 非受检异常，方法签名不用写 throws，业务代码干净。

Exception = 受检异常，每层方法都得 throws，污染调用链。

### 8.2 GlobalExceptionHandler 全局兜底 <a id="sec024"></a>

@RestControllerAdvice 是 @ControllerAdvice + @ResponseBody 的组合。

所有 Controller 抛的异常都会被它的 @ExceptionHandler 捕获。

匹配规则：异常类越具体优先级越高（子类优先于父类）。

| 异常类型 | 触发场景 | 返回 code | 日志级别 |
|---|---|---|---|
| BusinessException | 业务规则不满足（最常见） | 自定义 | warn |
| MethodArgumentNotValidException | @Valid @RequestBody DTO 校验失败 | 400 | warn |
| BindException | @Valid 表单/query 参数校验失败 | 400 | warn |
| Exception（兜底） | NPE / DB 超时 / Redis 挂等 | 500 | error |

📁 文件：mini-mall-common-core/src/main/java/com/minimall/common/core/exception/GlobalExceptionHandler.java

```
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** ① 业务异常（我们自己 throw 的，最常见） */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("[业务异常] 请求路径={}, 错误码={}, 消息={}",
                request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /** ② @Valid 参数校验失败（@RequestBody DTO 类） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null
                ? fieldError.getField() + " " + fieldError.getDefaultMessage()
                : "参数校验失败";
        log.warn("[参数校验失败] {}", message);
        return Result.error(400, message);
    }

    /** ③ @Valid 表单/query 参数校验失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null
                ? fieldError.getField() + " " + fieldError.getDefaultMessage()
                : "参数绑定失败";
        log.warn("[参数绑定失败] {}", message);
        return Result.error(400, message);
    }

    /** ④ 兜底：所有未捕获的异常（NPE/DB 超时等） */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("[系统异常] 请求路径={}", request.getRequestURI(), e);
        return Result.error(500, "系统繁忙，请稍后再试");
    }
}
```

### 8.3 MethodArgumentNotValidException vs BindException 区别 <a id="sec025"></a>

```
// 触发 MethodArgumentNotValidException
@PostMapping("/login")
public Result login(@Valid @RequestBody UserLoginDTO dto) { ... }

// 触发 BindException（没有 @RequestBody）
@GetMapping("/list")
public Result list(@Valid PageDTO dto) { ... }
```
Spring 设计的坑：JSON 入参和表单入参校验失败抛不同异常，两个都得 handle。

## 九、用户上下文 SecurityContextHolder <a id="sec026"></a>

作用：在【当前线程】里随时拿到当前登录用户信息。

### 9.1 单体 vs 微服务实现对比 <a id="sec027"></a>

| 维度 | 单体 UserContext | 微服务 SecurityContextHolder |
|---|---|---|
| 容器 | ThreadLocal<Long> | TransmittableThreadLocal<Map<String,Object>> |
| 装的字段 | 只装 userId | userId / userName / from-source / tenantId 等 |
| 来源 | 拦截器自己解 JWT | 拦截器从 HTTP Header 读（网关已解过） |
| 线程池透传 | ✗（单体没用线程池所以没事） | ✓ TTL 自动透传 |
| 清理时机 | afterCompletion 调 clear() | afterCompletion 调 remove() |

### 9.2 TransmittableThreadLocal（TTL） <a id="sec028"></a>

阿里开源（com.alibaba:transmittable-thread-local），专治线程池透传问题。

普通 ThreadLocal 痛点：

```
主线程 setUserId(42)
    ↓ 提交任务到 ForkJoinPool / @Async
子线程 getUserId() → null ❌  ← 线程切换就丢
```
TTL 工作原理：

```
主线程 setUserId(42)
    ↓ TtlExecutors.getTtlExecutorService 包装的线程池
子线程 getUserId() → 42 ✓  ← TTL 自动把父线程的值拷贝过来
```
微服务里【一定会】用到线程池的场景：

• Feign 远程调用（默认走线程池）

• MQ 消费线程

• @Async 任务

• Sentinel/Hystrix 熔断隔离池

### 9.3 用 Map<String,Object> 不用单字段 <a id="sec029"></a>

```
// 方案 A：每字段一个 ThreadLocal —— 乱
ThreadLocal<Long>   USER_ID
ThreadLocal<String> USER_NAME
ThreadLocal<Long>   TENANT_ID
// 加字段要改 N 处

// 方案 B：一个 Map —— 统一（RuoYi 选）
TransmittableThreadLocal<Map<String,Object>> CONTEXT
// 加字段只加 key

```

### 9.4 必须 remove() 防泄漏 <a id="sec030"></a>

Tomcat 是线程池模型，线程会复用。如果不 remove()，下次同线程拿到上一个用户的信息（数据串号 + 内存泄漏）。

拦截器的 afterCompletion 钩子里必须调 SecurityContextHolder.remove()。

### 9.5 项目里 SecurityContextHolder 完整代码 <a id="sec031"></a>

📁 文件：mini-mall-common-core/src/main/java/com/minimall/common/core/context/SecurityContextHolder.java

```
package com.minimall.common.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import java.util.HashMap;
import java.util.Map;

public class SecurityContextHolder {

    // ─── TTL 容器：每个线程一份独立的 Map ─────────
    private static final TransmittableThreadLocal<Map<String, Object>> CONTEXT
            = new TransmittableThreadLocal<>() {
        @Override
        protected Map<String, Object> initialValue() {
            return new HashMap<>();           // 首次 get() 自动给空 Map
        }
    };

    // ─── Key 常量 ───
    public static final String USER_ID_KEY = "user_id";
    public static final String USER_NAME_KEY = "user_name";

    // ─── 通用 set/get（操作 Map） ───
    public static void set(String key, Object value) {
        CONTEXT.get().put(key, value);
    }

    public static String get(String key) {
        Object value = CONTEXT.get().get(key);
        return value == null ? "" : value.toString();   // null 返回 "" 防 NPE
    }

    // ─── 业务便捷方法 ───
    public static Long getUserId() {
        String userIdStr = get(USER_ID_KEY);
        return userIdStr.isEmpty() ? 0L : Long.parseLong(userIdStr);
    }

    public static void setUserId(String userId) {
        set(USER_ID_KEY, userId);
    }

    public static String getUserName() {
        return get(USER_NAME_KEY);
    }

    public static void setUserName(String userName) {
        set(USER_NAME_KEY, userName);
    }

    // ─── ⭐⭐⭐ 防内存泄漏，afterCompletion 必调 ───
    public static void remove() {
        CONTEXT.remove();
    }
}

```

### 9.6 对照单体 mini-mall 的 UserContext 看差距 <a id="sec032"></a>

📁 单体文件：mini-mall/src/main/java/com/minimall/minimall/common/util/UserContext.java

```
package com.minimall.minimall.common.util;

public class UserContext {
    // 只存 Long userId，一个普通 ThreadLocal 搞定
    public static ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static Long getUserId() {
        return CURRENT_USER_ID.get();
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}
```
⭐ 单体 UserContext = 4 行核心；微服务 SecurityContextHolder = 50 行。

多出来的部分都是为了解决：①线程池透传 ②多字段 ③字符串值兼容。

## 十、Filter vs Interceptor（容易混） <a id="sec033"></a>

```
HTTP 请求
   ↓
[Filter]            ← Servlet 规范，最早。Spring Security 在这层
   ↓
[DispatcherServlet]
   ↓
[Interceptor]       ← Spring MVC 自己的，晚一点
   ↓
[Controller]
```

| 维度 | Filter | Interceptor |
|---|---|---|
| 规范 | Servlet 规范 | Spring MVC 私有 |
| 位置 | DispatcherServlet 之前 | DispatcherServlet 之后 |
| 实现接口 | jakarta.servlet.Filter | HandlerInterceptor |
| 拿到的对象 | ServletRequest | HttpServletRequest + Handler |
| 典型用途 | CORS / 编码 / Spring Security | 鉴权 / 上下文塞值 / 日志 |

Gateway 用的是 Filter（WebFlux 的 GlobalFilter），不是 Interceptor

因为 Gateway 基于 WebFlux（响应式），不是 Spring MVC，没有 Interceptor 这套。

### 10.1 单体里 Interceptor 怎么注册（WebMvcConfig） <a id="sec034"></a>

Interceptor 不会自动生效，必须在 WebMvcConfigurer 里调 addInterceptor 注册。

📁 单体文件：mini-mall/src/main/java/com/minimall/minimall/config/WebMvcConfig.java

```
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")                       // 拦截所有请求
                .excludePathPatterns(                          // 但这些不拦截（白名单）
                        "/api/users/register",                 // 注册
                        "/api/users/login",                    // 登录
                        "/doc.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/hello/**"
                );
    }
}
```
⭐ 微服务的 HeaderInterceptor 注册方式一样，只是白名单换成由网关统一管。

（业务服务自己的 excludePathPatterns 一般只排除健康检查、Knife4j 接口文档。）

## 十一、JwtInterceptor 与 Gateway AuthGlobalFilter <a id="sec035"></a>

职责相同（入口鉴权），但技术栈不同。

| 维度 | 单体 JwtInterceptor | 微服务 Gateway AuthGlobalFilter |
|---|---|---|
| 依赖框架 | Spring MVC | Spring WebFlux |
| 实现接口 | HandlerInterceptor | GlobalFilter |
| 拿请求 | HttpServletRequest（同步） | ServerWebExchange（响应式） |
| 返回值 | boolean | Mono<Void> |
| 运行容器 | Tomcat | Netty |
| IO 模型 | BIO / NIO 阻塞 | 异步非阻塞 |

### 11.1 微服务里的【职责拆分】 <a id="sec036"></a>

```
单体：
   请求 → JwtInterceptor 解 Token → 塞 ThreadLocal → Controller

微服务：
   请求 → Gateway AuthGlobalFilter 解 Token → 写 user_id 到 Header
                                              ↓
              业务服务 HeaderInterceptor 从 Header 读 → 塞 SecurityContextHolder
                                              ↓
                                         Controller
```
为什么拆？

• Token 解密+验签是 CPU 开销，让网关统一做一次，业务服务直接读 Header 即可。

• 业务服务「信任内部网络」——只要请求来自网关，Header 里的 user_id 就可信。

### 11.2 单体 JwtInterceptor 代码（mini-mall 项目实际代码） <a id="sec037"></a>

继承 HandlerInterceptor，重写 preHandle 验证 Token，afterCompletion 清理 ThreadLocal。

```
@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    // Controller 执行前调用 —— 验证 token，返回 true 放行
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        // ① 从请求头取 token
        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            response.setStatus(401);
            return false;
        }
        // ② 去掉 Bearer 前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        // ③ 解析 token 拿 userId（CPU 开销在这一步！）
        try {
            Long userId = jwtUtil.getUserIdFromToken(token);
            UserContext.setUserId(userId);   // ④ 塞 ThreadLocal
            return true;
        } catch (Exception e) {
            response.setStatus(401);
            return false;
        }
    }

    // Controller 执行完调用 —— 清理 ThreadLocal 防内存泄漏
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
```
注册位置：WebMvcConfig.addInterceptors() 配置拦截路径和白名单。

### 11.3 微服务 Gateway AuthGlobalFilter 代码（典型写法） <a id="sec038"></a>

基于 WebFlux，实现 GlobalFilter 接口。响应式风格，返回 Mono<Void>。

```
@Component
@Slf4j
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtUtil jwtUtil;

    // 白名单（登录/注册等不验 Token 的路径）
    private static final List<String> WHITE_LIST = List.of(
            "/api/user/login",
            "/api/user/register"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // ① 白名单直接放行
        if (WHITE_LIST.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // ② 拿 token
        String token = request.getHeaders().getFirst("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        token = token.substring(7);

        // ③ 解 token（整个微服务集群唯一一次解 Token 的地方）
        Long userId;
        try {
            userId = jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            return unauthorized(exchange);
        }

        // ④ 把 userId 写进 Header，往下游服务转发
        ServerHttpRequest mutated = request.mutate()
                .header("user_id", String.valueOf(userId))
                .header("from-source", "outer")   // 标记外部来源
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100;   // 越小越先执行，鉴权要最早
    }
}
```
⭐ 关键差异：

• HttpServletRequest → ServerHttpRequest（WebFlux 版本）

• boolean preHandle() → Mono<Void> filter()（响应式，异步非阻塞）

• request.mutate() 不可变模式：原对象不变，返回新对象

### 11.4 业务服务 HeaderInterceptor 代码（典型写法） <a id="sec039"></a>

Spring MVC 拦截器，从 Header 读 user_id 塞进 SecurityContextHolder。

```
@Component
@Slf4j
public class HeaderInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        // ① 直接读 Header（不解 Token，Gateway 已经解过了）
        String userId = request.getHeader("user_id");
        String userName = request.getHeader("user_name");

        // ② 塞进 SecurityContextHolder
        if (userId != null && !userId.isEmpty()) {
            SecurityContextHolder.setUserId(userId);
        }
        if (userName != null && !userName.isEmpty()) {
            SecurityContextHolder.setUserName(userName);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        // ③ 清理上下文（防线程池复用串号 + 内存泄漏）
        SecurityContextHolder.remove();
    }
}
```
⭐ 对比单体 JwtInterceptor 少了什么？

• 没有 jwtUtil.getUserIdFromToken() —— 不解 Token

• 没有 Bearer 前缀处理 —— Header 里直接是 user_id

• 没有 401 返回 —— 鉴权失败 Gateway 已经拦了，到这层默认可信

把单体的 JwtInterceptor 拆成网关 AuthGlobalFilter + 业务服务 HeaderInterceptor 后，

业务服务的拦截器变得超简单 —— 这就是网关的价值。

## 十二、微服务用户透传 3 件套 <a id="sec040"></a>

📁 当前项目进度：3 件套都还没建，目前只有第①件套要用到的 SecurityContextHolder。

微服务里用户身份从外部一路传到内部，需要 3 个组件配合：

| 组件 | 位置 | 职责 | 对应单体的什么 |
|---|---|---|---|
| AuthGlobalFilter | Gateway | 解 Token → 写 Header | JwtInterceptor 的「鉴权」部分 |
| HeaderInterceptor | 业务服务（每个） | 读 Header → 塞 ThreadLocal | JwtInterceptor 的「塞 ThreadLocal」部分 |
| FeignRequestInterceptor | 业务服务（每个） | Feign 出站时把 ThreadLocal 写回 Header | 单体没有（单体不调 Feign） |

完整链路图：

```
前端
  ↓ Authorization: Bearer xxx
[Gateway]
  ↓ AuthGlobalFilter: 解 JWT → 写 Header user_id=42
  ↓ 转发请求
[order-service]
  ↓ HeaderInterceptor: 读 Header user_id → SecurityContextHolder.set(USER_ID, "42")
  ↓ OrderController.create()
  ↓ orderService.create()
  ↓ productFeignClient.checkStock(productId)
       ↓ FeignRequestInterceptor: 读 SecurityContextHolder → 写 Header user_id=42
  ↓ HTTP 调用
[product-service]
  ↓ HeaderInterceptor: 读 Header → SecurityContextHolder.set(USER_ID, "42")
  ↓ ProductController.checkStock()
  ↓ Long uid = SecurityContextHolder.getUserId()  ← 跨服务也能拿到

```

## 十三、关键命令速查 <a id="sec041"></a>

```
# 编译整个项目（不跑测试）
mvn clean install -DskipTests

# 只编译当前模块及其依赖（增量）
mvn install -pl mini-mall-common/mini-mall-common-core -am

# 只看依赖树（排查版本冲突）
mvn dependency:tree

# 跑指定服务（dev 环境）
mvn spring-boot:run -pl mini-mall-user -Dspring-boot.run.profiles=dev
```
命令拆解：

| 命令片段 | 含义 |
|---|---|
| clean | 删除 target/ 目录，保证全新构建 |
| install | 编译 + 打包 + 装进本地仓库 ~/.m2/ |
| -DskipTests | 跳过单元测试（节省时间） |
| -pl | project list，只构建指定模块 |
| -am | also make，连同依赖的模块一起构建 |

## 十四、教学顺序：先具体后抽象 <a id="sec042"></a>

⭐ 个人学习准则（feedback-concrete-first）

写代码顺序：能跑的具体类优先（Entity/Service/Controller），常量/工具/AOP/异常基类靠后。

等「同一段字符串散落 4 处」的痛点真出现，再抽象成常量类——这时候你能看懂为什么必须集中。

反例（错误顺序）：

先建 9 个 common 子模块、几十个常量类——RuoYi 的工程组织方式适合参考，但不适合教学。

脱离用例时，常量类就是死字符串，看不出「为什么需要它」。

正例（正确顺序）：

Result → BusinessException → GlobalExceptionHandler → SecurityContextHolder → 真业务服务 →

Feign 出现 → 才抽 ServiceException → MinIO 接入 → 才抽 FileException → 多个 header 字符串重复 → 才抽 SecurityConstants

## 十五、项目当前状态与代码地图 <a id="sec043"></a>

文档对应的代码完成进度，方便随时回头对照实际文件位置。

### 15.1 当前目录树 <a id="sec044"></a>

```
mini-mall-cloud/                              ← 根 pom（已建✓）
├── pom.xml                                   ← packaging=pom 列子模块/锁版本/阿里云镜像
├── 微服务基础知识点.docx                       ← 本文档
└── mini-mall-common/                         ← 聚合层 pom（已建✓）
    ├── pom.xml                               ← packaging=pom 列子模块
    └── mini-mall-common-core/                ← 实际代码模块（已建✓）
        ├── pom.xml                           ← packaging=jar 写依赖
        └── src/main/java/com/minimall/common/core/
            ├── domain/
            │   └── Result.java               ← ✓ 统一响应封装
            ├── exception/
            │   ├── BusinessException.java    ← ✓ 业务异常
            │   └── GlobalExceptionHandler.java ← ✓ 4 个 @ExceptionHandler
            └── context/
                └── SecurityContextHolder.java ← ✓ TTL + Map 用户上下文

```

### 15.2 已完成的检查点 <a id="sec045"></a>

| 编号 | 完成项 | 对应章节 |
|---|---|---|
| ① | 父子 4 层 pom 结构搭好 | 二、三章 |
| ② | Spring Boot 3 + SC 2023 + SCA 2023 版本锁定 | 一、三章 |
| ③ | common-core 4 个核心类 | 七、八、九章 |
| ④ | mvn clean install BUILD SUCCESS | 十三章 |

### 15.3 待办（下一步要做的） <a id="sec046"></a>

| 任务 | 为什么不立刻做 | 什么时候做 |
|---|---|---|
| mini-mall-user 业务服务 | 进行中 | 立刻 |
| JwtUtils 工具类 | user-service login 接口要用时 | user-service 加 login 时 |
| SecurityConstants 常量类 | Header 字符串散落 4 处时 | Gateway 建好时 |
| mini-mall-common-security | HeaderInterceptor 真要用时 | Gateway+第二个服务后 |
| mini-mall-common-redis | Redis 真要用时 | user-service 加 token 缓存时 |
| mini-mall-gateway | 有 2 个以上服务要统一入口时 | user + product 都建好后 |

⭐ 学习原则：先具体后抽象（feedback-concrete-first）。

每个抽象层（common-*、Constants、工具类）都等真痛点出现再加，不预先准备。

## 十六、Java 异常体系（写 BusinessException 必懂） <a id="sec047"></a>

写自定义异常前，先搞清楚 Java 异常的家族关系。

```
Throwable（祖宗）
   ├── Error            ← 严重错误（OOM、StackOverflow），不要 catch
   └── Exception
        ├── 受检异常     ← IOException、SQLException、ClassNotFoundException
        │                  必须 throws 声明或 try-catch，否则编译报错
        └── RuntimeException ← 非受检异常
             ├── NullPointerException
             ├── IllegalArgumentException
             ├── BusinessException ⭐ 我们自定义的
             └── ...
```

### 16.1 受检异常 vs 非受检异常 <a id="sec048"></a>

|  | 受检（Checked） | 非受检（Unchecked / Runtime） |
|---|---|---|
| 父类 | Exception（直接子类） | RuntimeException |
| 必须声明 throws 吗 | 是（编译期强制） | 否 |
| 每层方法都要 throws 吗 | 是 | 否 |
| 典型例子 | IOException / SQLException | NPE / IllegalArgument |
| 业务异常该选哪个 | ❌ | ✅ |

### 16.2 为什么 BusinessException 继承 RuntimeException <a id="sec049"></a>

```
// ❌ 如果继承 Exception（受检）
public Result login() throws BusinessException {  // Service 要声明
    throw new BusinessException("密码错");
}
// Controller 也得 throws，每层都得 throws，污染整个调用链

// ✅ 继承 RuntimeException
public Result login() {                  // 无需声明
    throw new BusinessException("密码错"); // 自由 throw
}
```
业务异常 99% 都该继承 RuntimeException，避免污染方法签名。

📁 项目里 BusinessException 已经这么写了，见第八章。

### 16.3 throw 之后不能写 return（Unreachable statement） <a id="sec050"></a>

```
// ❌ 编译报错
public Result<Void> boom() {
    throw new BusinessException(401, "你没登录别瞎点");
    return Result.success();   // Unreachable statement
}

// ✅ 正确（删掉死代码 return）
public Result<Void> boom() {
    throw new BusinessException(401, "你没登录别瞎点");
}
```
Java 编译器做静态分析：throw 后【一定结束】，后面的代码永远不会执行 → 强制报错。

方法签名仍然写 Result<Void>（表示正常路径返回什么），编译器能识别「方法一定 throw」接受这种情况。

📁 项目里 HelloController.boom() 就是这种写法。

### 16.4 @ExceptionHandler 匹配优先级 <a id="sec051"></a>

规则：异常类越具体，优先级越高（子类优先于父类）。

```
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)         // 父类
    public Result handle1(Exception e) { ... }

    @ExceptionHandler(BusinessException.class) // 子类
    public Result handle2(BusinessException e) { ... }
}
异常匹配链：
  抛 BusinessException → 找最具体的 → BusinessException handler
  抛 NullPointerException → 没有 NPE handler → 找父类 → Exception handler 兜底
  抛 SQLException → 没有 SQL handler → 找父类 → Exception handler 兜底
```
设计原则：

• 给【可预期】的业务异常专门写 handler（BusinessException）

• 给【可预期】的校验异常专门写 handler（MethodArgumentNotValid、Bind）

• 留一个 Exception.class 兜底 handler 抓所有意外

### 16.5 参数校验异常：MethodArgumentNotValid vs Bind <a id="sec052"></a>

```
// ① @Valid + @RequestBody（JSON 入参）→ MethodArgumentNotValidException
@PostMapping("/login")
public Result login(@Valid @RequestBody UserLoginDTO dto) { ... }

// ② @Valid 无 @RequestBody（表单/query 参数）→ BindException
@GetMapping("/list")
public Result list(@Valid PageDTO dto) { ... }
```
Spring 设计的坑：两种入参形式校验失败抛【不同的异常】，但拿错误信息 API 一样。

📁 项目里 GlobalExceptionHandler 第 53~81 行同时 handle 了这两种。

## 十七、第一个真业务服务 mini-mall-user <a id="sec053"></a>

有了 common-core 后，建第一个业务服务来验证它真的可用。

### 17.1 目录结构 <a id="sec054"></a>

```
mini-mall-cloud/mini-mall-user/
├── pom.xml                  ← 继承根 pom + 引 common-core + MP + MySQL
└── src/main/
    ├── java/com/minimall/user/
    │   ├── MiniMallUserApplication.java   ← 启动类
    │   ├── controller/
    │   │   ├── HelloController.java       ← 验证 Result/异常生效
    │   │   └── UserController.java        ← 验证 DB 接通
    │   ├── entity/
    │   │   └── User.java                  ← 拷自单体
    │   └── mapper/
    │       └── UserMapper.java            ← 拷自单体
    └── resources/
        └── application.yml                ← 端口 + DB + MP 配置
```

### 17.2 子模块 pom 关键点 <a id="sec055"></a>

📁 文件：mini-mall-user/pom.xml

```
<!-- ① parent 指向根 pom，自动继承所有版本锁定 + 阿里云镜像 -->
<parent>
    <groupId>com.minimall</groupId>
    <artifactId>mini-mall-cloud</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</parent>

<artifactId>mini-mall-user</artifactId>
<!-- packaging 不写默认 jar -->

<dependencies>
    <!-- ② 引 common-core，一行依赖拿到 Result/异常/SecurityContextHolder -->
    <dependency>
        <groupId>com.minimall</groupId>
        <artifactId>mini-mall-common-core</artifactId>
        <!-- 不写 version，根 pom 锁好了 -->
    </dependency>

    <!-- ③ Spring Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- ④ MyBatis-Plus（接 DB） -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    </dependency>

    <!-- ⑤ MySQL 驱动，runtime 即可 -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>

<!-- ⑥ ⭐ 业务服务【必须】显式声明 spring-boot-maven-plugin 才能打 fat jar -->
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```
⭐ 关键点：父 pom 的 pluginManagement 锁了 boot 插件版本，但子 pom 必须再写一遍才生效。

（不写就跑 java -jar 报「no main manifest attribute」）

### 17.3 启动类关键注解 <a id="sec056"></a>

📁 文件：mini-mall-user/src/main/java/com/minimall/user/MiniMallUserApplication.java

```
@SpringBootApplication
@ComponentScan("com.minimall")                  // ① 扫描扩到上层
@MapperScan("com.minimall.user.mapper")          // ② 扫描 Mapper 接口
public class MiniMallUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniMallUserApplication.class, args);
        System.out.println("=========== mini-mall-user 启动成功 ===========");
    }
}
```
⭐ @ComponentScan(「com.minimall」) 的必要性

默认 @SpringBootApplication 只扫【启动类所在包及子包】，即 com.minimall.user。

但 common-core 的 GlobalExceptionHandler 在 com.minimall.common.core 包，默认扫不到。

扩到 com.minimall 上层，common-core 的类才会被装配。

⭐ @MapperScan 的作用

告诉 MyBatis 去哪扫 Mapper 接口，扫到后给每个接口动态生成代理类放进容器。

（也可以在每个 Mapper 上加 @Mapper，二选一，统一在启动类配置更清爽。）

### 17.4 application.yml 配置 <a id="sec057"></a>

📁 文件：mini-mall-user/src/main/resources/application.yml

```
server:
  port: 9001                  # 端口规划：网关 9000 / user 9001 / product 9002

spring:
  application:
    name: mini-mall-user      # 服务名（Nacos/Feign 用它识别）
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/mini_mall?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: 123456          # ⚠️ 生产环境用环境变量，别明文写

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 控制台打 SQL
    map-underscore-to-camel-case: true                     # 下划线转驼峰
  global-config:
    db-config:
      logic-delete-field: isDeleted     # 逻辑删除字段
      logic-delete-value: 1
      logic-not-delete-value: 0

logging:
  level:
    com.minimall: debug
    root: info

```

### 17.5 HelloController：验证 Result + 异常处理 <a id="sec058"></a>

📁 文件：mini-mall-user/src/main/java/com/minimall/user/controller/HelloController.java

```
@RestController
@RequestMapping("/hello")
public class HelloController {

    /** ① 基础接口：验证 Result 跨模块可用 */
    @GetMapping
    public Result<String> hello() {
        return Result.success("hi mini-mall-user");
    }

    /** ② 业务异常：验证 BusinessException + GlobalExceptionHandler 跨模块生效 */
    @GetMapping("/boom")
    public Result<Void> boom() {
        // throw 抛异常 = 方法立刻结束，后面不能写 return
        throw new BusinessException(401, "你没登录别瞎点");
    }

    /** ③ 系统异常：验证兜底 handler 抓 NPE */
    @GetMapping("/bug")
    public Result<Integer> bug() {
        String s = null;
        return Result.success(s.length());   // NPE，被兜底 handler 接住
    }
}
```
实测响应：

```
GET /hello       → {"code":200,"message":"操作成功","data":"hi mini-mall-user"}
GET /hello/boom  → {"code":401,"message":"你没登录别瞎点","data":null}
GET /hello/bug   → {"code":500,"message":"系统繁忙，请稍后再试","data":null}
```
控制台日志：

```
WARN  c.m.c.c.e.GlobalExceptionHandler : [业务异常] 请求路径=/hello/boom, 错误码=401, 消息=你没登录别瞎点
ERROR c.m.c.c.e.GlobalExceptionHandler : [系统异常] 请求路径=/hello/bug
java.lang.NullPointerException: ... (完整堆栈)
```
⭐ 业务异常用 warn 级别（正常情况），系统异常用 error 级别（要排查）。

## 十八、MyBatis-Plus 接 DB 详解 <a id="sec059"></a>

user-service 接 MySQL 是把单体的 User entity + Mapper 拷过来，几乎不用改。

### 18.1 User entity 关键注解 <a id="sec060"></a>

📁 文件：mini-mall-user/src/main/java/com/minimall/user/entity/User.java

```
@Getter @Setter
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)   // ① 主键自增
    private Long id;

    private String username;

    @JsonIgnore                                   // ② 序列化跳过密码字段
    private String password;

    private String nickname;
    private String phone;
    private String email;
    private String avatar;
    private Byte role;
    private Byte status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic                                   // ③ 逻辑删除标记
    private Byte isDeleted;
}
```

### 18.2 三个核心注解作用 <a id="sec061"></a>

| 注解 | 作用 | 踩坑 |
|---|---|---|
| @TableId(type=IdType.AUTO) | 标记主键 + 数据库自增 | 不写默认雪花算法 Long，会跟自增冲突 |
| @JsonIgnore | Jackson 序列化时跳过这个字段 | 保密字段（password）必加 |
| @TableLogic | 逻辑删除字段标记 | 配合 application.yml 的 logic-delete-* 配置才生效 |

⭐ implements Serializable 必加

微服务里 Feign 远程调用、Redis 缓存、消息队列都要求 POJO 实现 Serializable。

### 18.3 UserMapper：为什么没有实现类却能用 <a id="sec062"></a>

📁 文件：mini-mall-user/src/main/java/com/minimall/user/mapper/UserMapper.java

```
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 空的，啥都不写
}
```
这一个接口就能用 17 个方法（selectById / selectList / insert / updateById...），没写实现类，为什么？

```
启动时 MyBatis 看到 @MapperScan / @Mapper 标记
        ↓
扫描所有 Mapper 接口
        ↓
用 JDK 动态代理给每个接口【生成代理类】（你看不到这个类）
        ↓
代理类的实例放进 Spring 容器（作为 Bean）
        ↓
你 @Autowired UserMapper → 容器把代理对象注进来
```

### 18.4 selectById 内部流程 <a id="sec063"></a>

```
userMapper.selectById(1L)
        ↓
代理对象拦截这次调用
        ↓
代理根据继承关系，去 BaseMapper 找 selectById 方法的元信息
        ↓
拼出 SQL：SELECT * FROM user WHERE id=?
        ↓
（如果配了逻辑删除）自动加 AND is_deleted=0
        ↓
执行 SQL，把结果集映射成 User 对象返回
```
⭐ 重要区分：

• 「代码能力」（selectById 等方法）来自【继承】（BaseMapper）

• 「对象本身」（userMapper 这个变量）来自【依赖注入】（Spring 容器给的代理）

继承解释「长啥样」，依赖注入解释「你存在」。

### 18.5 逻辑删除：MP 自动重写 SQL <a id="sec064"></a>

application.yml 里这段配置一旦生效：

```
mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: isDeleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```
所有查询/删除被 MP 自动改写：

```
-- 你写：userMapper.selectById(1)
-- 实际：SELECT * FROM user WHERE id=1 AND is_deleted=0
                                          ↑ MP 自动加的

-- 你写：userMapper.deleteById(1)
-- 实际：UPDATE user SET is_deleted=1 WHERE id=1
                ↑ DELETE 变 UPDATE
```
⭐ 已经被逻辑删除的数据查不到了——下次想「恢复」只需 UPDATE is_deleted=0。

为什么用逻辑删除？

• 用户/订单数据真删了，审计、客服查、数据分析就完了

• 误删可恢复（is_deleted=0）

• 数据归档前留软删除窗口

### 18.6 UserController 简单查询接口 <a id="sec065"></a>

📁 文件：mini-mall-user/src/main/java/com/minimall/user/controller/UserController.java

```
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserMapper userMapper;

    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable("id") Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return Result.success(user);
    }
}
```
访问 GET /user/1 时实际执行的 SQL（控制台能看到）：

```
==>  Preparing: SELECT id,username,password,nickname,phone,email,avatar,role,status,create_time,update_time,is_deleted FROM user WHERE id=? AND is_deleted=0
==> Parameters: 1(Long)
<==      Total: 1
```
⭐ password 字段虽然 SELECT 出来了，但响应 JSON 里没有——因为 @JsonIgnore。

### 18.7 Mapper 依赖注入易错点速查 <a id="sec066"></a>

| 问题 | 答案 |
|---|---|
| userMapper 这个变量从哪来的 | Spring 容器注入（MyBatis 生成的动态代理实例） |
| selectById 这方法是哪里来的 | 继承自 BaseMapper（17 个方法都是继承的） |
| UserMapper 是接口，能 new 吗 | 不能，Spring/MyBatis 帮你生成代理类的实例 |
| @MapperScan 和 @Mapper 必须二选一吗 | 一个就行，加 @MapperScan 更清爽 |
| 逻辑删除后能用 selectById 查到吗 | 查不到（MP 自动加 AND is_deleted=0 过滤） |
| @JsonIgnore 影响 SELECT 吗 | 不影响（SQL 查出来了，序列化时跳过） |

### 18.8 MyBatis 分页三大方案对比 <a id="sec066-8"></a>

MyBatis 生态里实现分页有三种主流方案，本项目用的是第 ③ 种（MP 内置）。

| 方案 | 谁来加 LIMIT | 谁来 count | 何时用 |
|---|---|---|---|
| ① **原生手写 LIMIT** | 你在 XML/@Select 里手动写 `LIMIT #{offset},#{size}` | 自己写第二条 `SELECT COUNT(*)` | 极简 demo、不想引插件 |
| ② **PageHelper 插件** | 插件拦截器自动拼 | 插件自动跑 count | 用纯 MyBatis 的项目（无 MP） |
| ③ **MyBatis-Plus PaginationInnerInterceptor** | 插件自动拼 | 自动跑 count | **本项目方案**（已经在用 MP） |

**核心区别一句话**：原生方案你写两条 SQL；插件方案（PageHelper / MP）你只写普通查询，插件**运行时拦截**生成的 SQL 加上 LIMIT 并执行 count。

> 同时引入 PageHelper 和 MP 的分页插件会**互相打架**（两套拦截器都改 SQL，结果错乱）。本项目只用 MP，不要再引 PageHelper。

### 18.9 本项目方案：MyBatis-Plus 分页实战 <a id="sec066-9"></a>

#### 18.9.1 三步配齐（user / product / order 都是这套）

**Step 1：pom 加依赖**（MP 3.5.9+ 必须）

```xml
<!-- MP 3.5.9+ 把 PaginationInnerInterceptor 拆到了 jsqlparser 包 -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>
</dependency>
```

> ⚠ **本项目真踩坑**：MP 3.5.5- 时 `PaginationInnerInterceptor` 在 `mybatis-plus-extension` 里；3.5.9+ 拆出来到 `mybatis-plus-jsqlparser`，不加这个依赖就 `ClassNotFoundException`。详见 18.11 ADMIN.5 踩坑实录。

**Step 2：注册插件 Bean**

```java
// mini-mall-user/src/main/java/com/minimall/user/config/MybatisPlusConfig.java
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 关键: PaginationInnerInterceptor 才是分页拦截器, 必须指定数据库类型
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

**没这个 Bean 的后果**（ADMIN.5 实测）：
- 调 `service.page(p, wrapper)` SQL **不会改写**，没有 LIMIT，全表扫返回
- `IPage.getTotal()` 永远是 **0**（因为根本没跑 count SQL）

**Step 3：用 `service.page(Page, Wrapper)` 查**

```java
@GetMapping("/page")
public Result<IPage<Product>> page(AdminProductPageDTO query) {
    // 构造分页对象 (当前页, 每页条数)
    Page<Product> p = new Page<>(query.getPage(), query.getSize());

    // 拼条件 (LambdaQueryWrapper 是 MP 的链式 SQL 构造器)
    LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
    w.like(query.getKeyword() != null, Product::getName, query.getKeyword());
    w.eq(query.getCategoryId() != null, Product::getCategoryId, query.getCategoryId());
    w.orderByDesc(Product::getId);

    // ⭐ service.page 是 IService 提供的, 内部调 BaseMapper.selectPage
    IPage<Product> result = productService.page(p, w);
    return Result.success(result);
}
```

#### 18.9.2 关键对象速查

| 对象 | 来源 | 作用 |
|---|---|---|
| `Page<T>` | MP 提供的 IPage 实现类 | 既是**入参**（带 current/size）也是**返回值**（自动填 records/total/pages） |
| `IPage<T>` | 接口 | `Page<T>` 实现它；controller 返回类型用接口更通用 |
| `LambdaQueryWrapper<T>` | MP 提供 | 用 `User::getName` lambda 替代字符串列名，重构安全 |
| `PaginationInnerInterceptor` | MP 插件 | 真正干活的：拦截 SQL，改写加 LIMIT + 跑 count |

#### 18.9.3 IPage 返回 JSON 长这样

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [ { "id": 14, ... }, { "id": 13, ... } ],
    "total": 14,
    "size": 10,
    "current": 1,
    "pages": 2
  }
}
```

前端 axios 拦截器解 `data` 后直接 `res.records` / `res.total` 就能用（参见前端 `src/api/user.ts` 的 `PageResult<T>` 类型）。

#### 18.9.4 MP 智能 count（自动优化）

MP 的 `PaginationInnerInterceptor` 跑 count SQL 时**自动**做两件事：
- 剔除 `ORDER BY`（count 不需要排序）
- 剔除 `LEFT JOIN`（前提是被 join 的表没在 WHERE 出现，剔了不影响行数）

效果：业务 SQL 写得多复杂，count SQL 都会精简成最小开销，**深分页时显著提速**。

#### 18.9.5 分页 + @TableLogic 配合

实体上加了 `@TableLogic` 的 `is_deleted` 字段，分页 SQL 会自动加 `WHERE is_deleted=0`，count 也会。

```java
// Orders.java
@TableLogic
private Byte isDeleted;
```

实际执行的 SQL（从 ADMIN.5 调试日志抓的）：

```sql
-- 分页 (插件改写后)
SELECT id,order_no,user_id,total_amount,status,... 
FROM orders 
WHERE is_deleted=0 
ORDER BY id DESC 
LIMIT 0, 10

-- count (插件自动跑, 剔了 ORDER BY)
SELECT COUNT(*) FROM orders WHERE is_deleted=0
```

#### 18.9.6 完整端到端 demo（ADMIN.3 用户管理实例） <a id="sec066-9-6"></a>

> 跟着这一节，从 pom 到 curl 把一个 MP 分页接口完整跑起来。**代码全部出自本项目 `mini-mall-user` 实战**，复制即用。

**整体结构：8 层文件**

```
mini-mall-user/
├── pom.xml                       ← ① 依赖
├── src/main/resources/
│   ├── application.yml           ← ② 配置
│   └── (建表 SQL)                ← ③ 表结构
├── src/main/java/com/minimall/user/
│   ├── entity/User.java          ← ④ 实体
│   ├── mapper/UserMapper.java    ← ⑤ Mapper (空, 继承 BaseMapper)
│   ├── config/
│   │   └── MybatisPlusConfig.java← ⑥ 注册分页插件 Bean
│   ├── dto/
│   │   └── AdminUserPageDTO.java ← ⑦ 入参
│   └── controller/
│       └── AdminUserController.java ← ⑧ 分页接口
```

##### ① pom.xml 依赖（关键 4 条）

```xml
<!-- ⭐ 单纯加 starter 不够, 3.5.9+ 还要单独引 jsqlparser -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <!-- 版本由 mini-mall-cloud 根 pom 锁: 3.5.9 -->
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>
</dependency>

<!-- MySQL 驱动: runtime 即可, 编译期不需要 -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Lombok 简化 entity / dto -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

##### ② application.yml 配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/mini_mall?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true                # 下划线转驼峰 (create_time → createTime)
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 打印 SQL, 调试分页必备
  global-config:
    db-config:
      logic-delete-field: isDeleted   # 全局逻辑删除字段 (字段加 @TableLogic 也可)
      logic-delete-value: 1
      logic-not-delete-value: 0
```

##### ③ 数据库表

```sql
CREATE TABLE `user` (
    `id`        BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `username`  VARCHAR(64)  NOT NULL UNIQUE,
    `password`  VARCHAR(128),
    `nickname`  VARCHAR(64),
    `email`     VARCHAR(128),
    `role`      TINYINT      NOT NULL DEFAULT 0    COMMENT '0 普通 / 1 管理员',
    `status`    TINYINT      NOT NULL DEFAULT 1    COMMENT '0 禁用 / 1 正常',
    `oauth_provider` VARCHAR(32) NULL,
    `is_deleted`     TINYINT  NOT NULL DEFAULT 0,
    `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

##### ④ Entity（MP 三大注解都用上）

```java
package com.minimall.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user")   // 显式指定表名 (避免 user 是保留字之类的歧义)
public class User {

    @TableId(value = "id", type = IdType.AUTO)   // 主键自增
    private Long id;

    private String username;

    @JsonIgnore                                  // ⭐ 序列化时不返给前端
    private String password;

    private String nickname;
    private String email;
    private Byte   role;        // 0 普通 / 1 管理员
    private Byte   status;      // 0 禁用 / 1 正常
    private String oauthProvider;

    @TableLogic                                  // ⭐ 自动 WHERE is_deleted=0
    private Byte isDeleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

##### ⑤ Mapper（一行代码搞定 17 个方法）

```java
package com.minimall.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimall.user.entity.User;

// 不写 @Mapper, 因为启动类 @MapperScan("com.minimall.user.mapper") 已扫
// 继承 BaseMapper<User> 自动获得 17 个方法: selectById / selectPage / insert / updateById ...
public interface UserMapper extends BaseMapper<User> {
}
```

##### ⑥ 分页插件 Bean（**最关键，漏配就是 ADMIN.5 那个坑**）

```java
package com.minimall.user.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // ⭐ 没这一行: service.page() SQL 不会被改写, 没 LIMIT, total=0
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

##### ⑦ 查询入参 DTO

```java
package com.minimall.user.dto;

import lombok.Data;

@Data
public class AdminUserPageDTO {
    private Integer page = 1;        // 当前页 (默认 1)
    private Integer size = 20;       // 每页条数 (默认 20)
    private String  keyword;         // 关键词: 模糊匹配 username 或 nickname
    private Byte    status;          // null = 不过滤
    private Byte    role;            // null = 不过滤
}
```

##### ⑧ Controller（核心 13 行）

```java
package com.minimall.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.minimall.common.core.domain.Result;
import com.minimall.user.dto.AdminUserPageDTO;
import com.minimall.user.entity.User;
import com.minimall.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/user")
public class AdminUserController {

    @Autowired
    private UserMapper userMapper;

    @GetMapping("/page")
    public Result<IPage<User>> page(AdminUserPageDTO query) {
        // ① 分页对象
        Page<User> p = Page.of(query.getPage(), query.getSize());

        // ② 动态条件: 字段非空才加 WHERE
        QueryWrapper<User> w = new QueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            w.and(q -> q.like("username", query.getKeyword())
                        .or().like("nickname", query.getKeyword()));
        }
        if (query.getStatus() != null) w.eq("status", query.getStatus());
        if (query.getRole()   != null) w.eq("role",   query.getRole());
        w.orderByDesc("id");                       // 新用户在前

        // ③ 一行触发分页 (插件拦截, 自动 LIMIT + COUNT)
        IPage<User> result = userMapper.selectPage(p, w);

        // ④ 兜底清密码 (因为 entity 没加 @JsonIgnore 就是这种兜底, 加了可以省)
        result.getRecords().forEach(u -> u.setPassword(null));

        return Result.success(result);
    }
}
```

##### 运行 + 测试

```bash
# 1. 启动 user 服务 (端口 9001)
java -jar mini-mall-user-0.0.1-SNAPSHOT.jar

# 2. 拿 admin token (略, 走 /auth/login)

# 3. 分页查询
curl "http://localhost:9001/admin/user/page?page=1&size=3&role=1" \
     -H "Authorization: Bearer $TOKEN"
```

##### 控制台实际看到的 SQL（验证插件生效）

```sql
==>  Preparing: 
  SELECT COUNT(*) FROM user WHERE is_deleted=0 AND role = ?
==>  Parameters: 1(Byte)
<==      Total: 1                              ← ✅ count SQL 跑了, 返 1

==>  Preparing: 
  SELECT id, username, password, nickname, email, role, status, oauth_provider, 
         is_deleted, create_time, update_time 
  FROM user 
  WHERE is_deleted=0 AND role = ? 
  ORDER BY id DESC 
  LIMIT ?
==>  Parameters: 1(Byte), 3(Long)              ← ✅ 自动 LIMIT 3
<==      Total: 1
```

> 重点关注三件事：① count SQL 自动剔了 `ORDER BY id DESC`；② 主查询自动加 `LIMIT ?`；③ `WHERE is_deleted=0` 是 `@TableLogic` 自动加的。

##### 返回的 JSON

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      { "id": 7, "username": "admin", "nickname": "超级管理员", "role": 1, "status": 1, "password": null }
    ],
    "total": 1,
    "size": 3,
    "current": 1,
    "pages": 1
  }
}
```

##### Checklist：跑不通时按顺序排查

| 现象 | 检查 |
|---|---|
| 404 | `@MapperScan` 扫到包路径了？controller 注解都对？ |
| 500 ClassNotFound: PaginationInnerInterceptor | **pom 漏 `mybatis-plus-jsqlparser`** |
| 200 但 total=0 + 全表扫 | 漏建 `MybatisPlusConfig` 注册 Bean（ADMIN.5 经典坑） |
| 200 但 records 字段全是下划线 | 漏配 `map-underscore-to-camel-case: true` |
| 200 但 records 带 password 密文 | 漏 `setPassword(null)` 兜底或漏 `@JsonIgnore` |
| 删了一条还能查到 | 漏 `@TableLogic` 注解 / yml 全局配置 |

### 18.10 原生方案 vs PageHelper 对照 demo <a id="sec066-10"></a>

> 本节只为对照学习，本项目不用。如果你将来在**纯 MyBatis 项目**（没 MP）里要做分页，参考这套。

#### 18.10.1 原生手写 LIMIT（最朴素）

**Mapper 接口（多参数必须 `@Param`，否则 XML 只能用 `#{arg0}` 难读）**

```java
public interface UserMapper {
    // 数据 SQL: 手动 LIMIT
    List<User> selectByLimit(@Param("offset") Integer offset,
                             @Param("pageSize") Integer pageSize);
    // count SQL: 单独一条
    Long selectCount();
}
```

**XML**

```xml
<select id="selectByLimit" resultType="User">
    SELECT id, username, age, email, create_time
    FROM user
    LIMIT #{offset}, #{pageSize}
</select>

<select id="selectCount" resultType="Long">
    SELECT COUNT(*) FROM user
</select>
```

**Service 手动组装（每个分页接口都要写这套）**

```java
public PageResult<User> page(Integer pageNum, Integer pageSize) {
    int offset = (pageNum - 1) * pageSize;            // 偏移量
    List<User> records = userMapper.selectByLimit(offset, pageSize);
    Long total = userMapper.selectCount();
    long pages = (total + pageSize - 1) / pageSize;   // 向上取整 (替代 % 三目)

    return new PageResult<>(records, total, pageNum, pageSize, pages);
}
```

**缺点**：每多一个表的分页接口，重复写一遍这套；多条件查询要在 LIMIT SQL 和 count SQL 里各写一份 WHERE，容易漏改。

#### 18.10.2 PageHelper 插件

**pom**

```xml
<dependency>
    <groupId>com.github.pagehelper</groupId>
    <artifactId>pagehelper-spring-boot-starter</artifactId>
    <version>1.4.7</version>
</dependency>
```

**application.yml**

```yaml
pagehelper:
  helper-dialect: mysql
  reasonable: true            # 页码 <1 自动查第 1 页, 越界自动查最后一页
  support-methods-arguments: true
```

**Mapper 接口（普通查询，无 LIMIT）**

```java
@Select("SELECT id, username, age, email FROM user")
List<User> selectAll();
```

**Service：一行 startPage**

```java
public PageResult<User> page(Integer pageNum, Integer pageSize) {
    // ⭐ 紧跟下一条 SQL 自动加分页, 自动跑 count
    PageHelper.startPage(pageNum, pageSize);
    List<User> list = userMapper.selectAll();          // 返回的其实是 Page<User>

    Page<User> pageInfo = (Page<User>) list;
    return new PageResult<>(
            pageInfo.getResult(),
            pageInfo.getTotal(),
            pageInfo.getPageNum(),
            pageInfo.getPageSize(),
            (long) pageInfo.getPages()
    );
}
```

**避坑（PageHelper 高频面试题）**：

1. `startPage()` 后**必须紧跟一条** select，中间不能夹其他查询。否则分页绑到了别的 SQL，目标 SQL 退化成全表扫。
2. 分页参数存在 **ThreadLocal**，所以分页只对**当前线程的下一条 select** 生效，自动用完即清。
3. 关闭 count：`PageHelper.startPage(pageNum, pageSize, false)` —— 不要总数时省一次 SQL。
4. 不要在循环里调 `startPage()`，会堆积参数。

#### 18.10.3 三方案统一返回类 PageResult<T>

PageResult **不是框架自带**的，是项目自定义的统一前后端约定。三种方案最后都把数据塞进它返前端。

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    private List<T> records;
    private Long    total;
    private Integer pageNum;
    private Integer pageSize;
    private Long    pages;
}
```

> 本项目用的是 MP 自带的 `IPage<T>`（字段是 `records / total / size / current / pages`），不另定义 `PageResult`。前端 `PageResult<T>` 是 **TypeScript 接口**而非 Java 类，只是为了前端类型对齐 MP 的 IPage。

#### 18.10.4 三种方案选型决策表

| 你的项目 | 推荐 |
|---|---|
| **已经用 MyBatis-Plus** | ③ MP `PaginationInnerInterceptor`（**本项目**） |
| 纯 MyBatis、不想引插件 | ① 原生 LIMIT |
| 纯 MyBatis、想偷懒 | ② PageHelper |
| 纯 MyBatis + 多数据库（MySQL/Oracle 切换） | ② PageHelper（`helperDialect` 自动切换） |
| 单条 SQL 千万级深分页 | 任何方案都要换"游标分页"，见 18.12 |

### 18.11 ADMIN.5 真踩坑：order 服务漏配 PaginationInnerInterceptor <a id="sec066-11"></a>

#### 现象

ADMIN.5 后台订单分页接口刚跑通，curl 测：

```bash
curl /admin/order/page?page=1&size=3
```

返回：

```json
{
  "total": 0,        ← ❌ 实际表里有 14 条
  "records": [       ← ✅ 返了 8 条 (不是 3 条!)
    { "id": 14 }, ... { "id": 7 }
  ]
}
```

#### 根因诊断

打开 MyBatis 的 SQL 日志（`log-impl: org.apache.ibatis.logging.stdout.StdOutImpl`）看实际 SQL：

```sql
SELECT id, order_no, ... FROM orders WHERE is_deleted=0 ORDER BY id DESC
```

**没有 LIMIT**！这说明 `PaginationInnerInterceptor` 完全没拦截 SQL —— 插件 Bean 根本没注册。

#### 根因

```bash
$ find mini-mall-cloud/mini-mall-order -name "MybatisPlusConfig.java"
# 空，没有！

$ grep -A1 "mybatis-plus" mini-mall-cloud/mini-mall-order/pom.xml
mybatis-plus-spring-boot3-starter  ← 只有这一个，缺 jsqlparser
```

历史原因：order 服务以前的端点（`/order/my`）直接 `List<>` 返全部用户订单，不需要分页，所以一直没人配过分页插件。今天加了 admin 后台分页才暴露。

#### 修复（两步）

**Step 1：order pom 加 jsqlparser**

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>
</dependency>
```

**Step 2：新建 MybatisPlusConfig 注册 Bean**（跟 user / product 服务那个一模一样）

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

重打包 + 重启 order 服务，SQL 立刻有 LIMIT，total 正常返 14。

#### 教训

每个新建的微服务，**只要将来可能用到分页**，就把这个 Config 类一起复制过去，别留隐患。这是 ADMIN.5 之后产品的踩坑记忆。

### 18.12 深分页性能 + 优化方案 <a id="sec066-12"></a>

#### 问题

`LIMIT 100000, 10` 实际怎么执行？MySQL 会扫描前 **100010 条**记录、丢掉前 100000、返最后 10 条 —— 越往后越慢。

```sql
-- 实测 100w 数据
SELECT * FROM orders LIMIT 0, 10        -- ~1ms
SELECT * FROM orders LIMIT 100000, 10   -- ~500ms
SELECT * FROM orders LIMIT 500000, 10   -- ~3000ms
```

#### 优化方案 1：游标/主键分页（推荐）

不再用 page 跳转，改用"上一页最后一条的 id"作为锚点：

```sql
-- 客户端记住上一页最后 id (比如 100000)
SELECT * FROM orders 
WHERE is_deleted=0 AND id < 100000 
ORDER BY id DESC 
LIMIT 10
```

主键索引直接跳到 100000，零扫描，性能恒定。代价：**不支持跳页**，只能"下一页"。适合无限滚动、消息流类。

#### 优化方案 2：延迟关联（兼容跳页）

只对小 id 列分页，再 join 回主表：

```sql
SELECT o.*
FROM orders o
JOIN (
    SELECT id FROM orders WHERE is_deleted=0 ORDER BY id DESC LIMIT 100000, 10
) tmp ON o.id = tmp.id
```

子查询只走 id 索引（覆盖索引），不回表，比直接 `SELECT * LIMIT 100000,10` 快几个数量级。

#### 本项目层面

mini-mall-cloud 当前数据量（订单 14 条）远没到深分页瓶颈，**不用优化**。但订单表如果未来上千万，admin 后台分页"跳到第 1000 页"就会卡，那时候要么改游标，要么前端禁掉跳页改成"上一页/下一页"。

### 18.13 #{} vs ${} 与 @Param 速查（分页高频考点） <a id="sec066-13"></a>

#### #{} 必用，${} 严禁用于 LIMIT

| 写法 | 行为 | 用法 |
|---|---|---|
| `#{offset}` | **预编译占位符** `?`，参数后传 | 99% 的场景，包括 LIMIT 参数 |
| `${offset}` | **字符串拼接**到 SQL | 仅用于动态表名/列名/排序方向 |

```xml
<!-- ✅ 正确, 预编译, 防注入 -->
LIMIT #{offset}, #{pageSize}

<!-- ❌ 危险, 如果 offset 是用户传的, 可以 SQL 注入 -->
LIMIT ${offset}, ${pageSize}
```

#### @Param 何时必加

| 场景 | 加不加 |
|---|---|
| **方法多个参数**（含分页 offset/size） | **必须加** `@Param("offset")`，否则 XML 只能 `#{arg0}` `#{param1}` 难读 |
| 方法只有 1 个基本类型参数 | 可不加（XML 直接 `#{id}` 也认） |
| 方法传 1 个 POJO/Map | 不用加（XML 直接 `#{username}` 取对象属性） |

```java
// ✅ 多参数, 加 @Param
List<User> selectByLimit(@Param("offset") Integer offset,
                         @Param("pageSize") Integer pageSize);

// ✅ 单 POJO, 不加 @Param
List<User> selectByCondition(User condition);  // XML 里 #{username} 取 condition.username
```

### 18.14 第十八章分页知识地图（一页脑图） <a id="sec066-14"></a>

```
MyBatis 分页
├── 方案选择
│   ├── ① 原生 LIMIT          → XML 手写 limit+count, Service 拼 PageResult
│   ├── ② PageHelper          → startPage(p,s) + 普通 SELECT, 拦截器自动处理
│   └── ③ MP PaginationIner   → service.page(Page, Wrapper)  ★ 本项目
│
├── MP 三步配齐
│   ├── pom: mybatis-plus-jsqlparser (3.5.9+ 必加)
│   ├── @Bean MybatisPlusInterceptor + PaginationInnerInterceptor
│   └── new Page<>(current, size) → service.page(p, wrapper)
│
├── 关键对象
│   ├── Page<T>          入参 + 返回值, 自动填 records/total/pages
│   ├── IPage<T>         接口, controller 返这个更通用
│   └── LambdaQueryWrapper<T>   类型安全的条件构造器
│
├── SQL 底层
│   ├── 分页 SQL:  原 SQL + LIMIT 自动拼上
│   ├── count SQL: MP 自动剔 ORDER BY / LEFT JOIN 提速
│   └── @TableLogic 字段自动 WHERE is_deleted=0
│
├── 踩坑
│   ├── ADMIN.5: 漏配 PaginationInnerInterceptor → total=0
│   ├── PageHelper 中间穿插 SQL → 分页失效
│   └── 同时引 PageHelper + MP 插件 → 互相打架
│
└── 性能
    ├── 小数据: 任意方案
    ├── 大数据 deep page: 游标分页 / 延迟关联
    └── 不需要 total: PageHelper false 参数 / MP 用 selectList 代替
```

---

## 十九、登录注册与 JWT 鉴权 <a id="sec067"></a>

user-service 接通 DB 后，加 login/register 接口。这是【自然引出 JwtUtils】的时刻。

### 19.0 B 阶段文件清单（新建 / 修改） <a id="sec068"></a>

📁 在 mini-mall-user 模块下，共 +6 新文件 / +1 修改 / +1 yml 更新：

```
mini-mall-user/
├── pom.xml                                ← ✏️ 改：加 jjwt 三件套 + bcrypt + validation
├── src/main/
│   ├── java/com/minimall/user/
│   │   ├── MiniMallUserApplication.java
│   │   ├── controller/
│   │   │   └── UserController.java        ← ✏️ 改：加 /register /login 接口
│   │   ├── service/
│   │   │   ├── IUserService.java          ← ➕ 新建（继承 IService）
│   │   │   └── impl/
│   │   │       └── UserServiceImpl.java   ← ➕ 新建（继承 ServiceImpl + BCrypt + JwtUtil）
│   │   ├── mapper/UserMapper.java
│   │   ├── entity/User.java
│   │   ├── dto/                           ← ➕ 整个包新建
│   │   │   ├── UserLoginDTO.java
│   │   │   └── UserRegisterDTO.java
│   │   └── util/                          ← ➕ 整个包新建
│   │       └── JwtUtil.java
│   └── resources/
│       └── application.yml                ← ✏️ 改：加 jwt.secret + jwt.expiration

```

### 19.1 这一节做了哪 8 件事 <a id="sec069"></a>

```
① pom 加 4 个依赖（JWT 三件套 + BCrypt + validation）
② 建 DTO（UserLoginDTO / UserRegisterDTO，带 @NotBlank @Size 校验）
③ 建 JwtUtil（签发/解析 token）
④ 建 IUserService 接口（继承 IService<User>）
⑤ 建 UserServiceImpl（继承 ServiceImpl + 注入 JwtUtil + BCrypt 加密）
⑥ UserController 加 /register /login 接口
⑦ application.yml 加 jwt.secret + jwt.expiration
⑧ curl 测 6 个场景全部跑通

```

### 19.2 新增依赖详解 <a id="sec070"></a>

📁 文件：mini-mall-user/pom.xml 第 51~84 行

```
<!-- ⑥ JWT 三件套：版本根 pom 锁了 0.12.6 -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>           ← 编译要用的接口
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <scope>runtime</scope>                       ← 运行时实现
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <scope>runtime</scope>                       ← JSON 序列化
</dependency>

<!-- ⑦ BCrypt 密码加密
     只引 spring-security-crypto，不引入完整 Spring Security（轻量） -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>

<!-- ⑧ 参数校验：@NotBlank @Size 等 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

### 19.2.1 为什么 jjwt 拆 3 个包？ <a id="sec071"></a>

| 子包 | 作用 | scope |
|---|---|---|
| jjwt-api | Jwts/Claims/JwtBuilder 等接口 | compile（默认） |
| jjwt-impl | 上面接口的实现类 | runtime（编译期不依赖） |
| jjwt-jackson | 用 Jackson 序列化 payload JSON | runtime |

⭐ 这种【接口/实现/序列化】三包拆分是 JJWT 0.12+ 的设计：业务代码只依赖 api 接口，

实现可以随时换（impl 改成别的实现也行，jackson 改成 gson 也行）。

### 19.3 BCrypt 密码加密原理 <a id="sec072"></a>

BCrypt = 加盐慢哈希算法，专门用来存密码。

```
// 加密
String hash1 = ENCODER.encode("123456");  // 输出：$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
String hash2 = ENCODER.encode("123456");  // 输出：$2a$10$abc...（跟 hash1 完全不一样！）

// 验证
ENCODER.matches("123456", hash1);  // true
ENCODER.matches("123456", hash2);  // true
ENCODER.matches("wrong",  hash1);  // false
```
⭐ 同一个明文每次加密结果都不一样，因为 BCrypt 每次生成不同的【随机 salt】并混进哈希。

结构 $2a$10$<22位 salt><31位 hash>：

```
$2a$10$N9qo8uLOickgx2ZMRZoMye IjZAgcfl7p92ldGxad68LJZdL17lhWy
 │   │   └─ 22 位 salt ───┘ └────── 31 位 hash ────────────────┘
 │   └─ cost factor（10 = 2^10 次迭代，越大越慢）
 └─ 版本标识
```
BCrypt 的 matches() 验证：从存储的 hash 里取出 salt，把原文密码用同样的 salt 重新哈希一遍比较。

彩虹表破不了它——每个用户的 salt 不同，得为每个用户独立建表。

### 19.4 JwtUtil 完整代码 <a id="sec073"></a>

📁 文件：mini-mall-user/src/main/java/com/minimall/user/util/JwtUtil.java

```
@Component
public class JwtUtil {

    @Value("${jwt.secret}")    // ← 从 yml 读密钥
    private String secret;

    @Value("${jwt.expiration}") // ← 从 yml 读过期时间毫秒
    private Long expiration;

    /** 生成 token */
    public String generateToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);

        return Jwts.builder()
                .claims(claims)                                                  // payload 业务数据
                .issuedAt(new Date())                                            // 签发时间
                .expiration(new Date(System.currentTimeMillis() + expiration))   // 过期时间
                .signWith(getSigningKey())                                       // 签名
                .compact();                                                       // 输出 xxx.yyy.zzz
    }

    /** 解析 token，验签 + 返回 Claims */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())     // 验签：密钥不对会抛 SignatureException
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从 token 提取 userId */
    public Long getUserIdFromToken(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    /** 生成 SecretKey（HMAC 算法对称密钥），库的固定写法 */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
```
⭐ 注意 javax.crypto.SecretKey 不需要改成 jakarta

这是 JCE（Java Cryptography Extension）标准包，跟 Servlet/EE 改名无关。

Boot 3 只改了 javax.servlet/javax.persistence 这些包，javax.crypto 保留。

### 19.5 JWT 长啥样（base64 解码后） <a id="sec074"></a>

```
eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWxpY2UiLCJpYXQiOjE3ODE3OTYwNjcsImV4cCI6MTc4MjQwMDg2N30.Vuqf0-p4MUx7WGPswqSgVEvOxgw5TZ0IiAEQPh3WQGQ
└─── header ───┘ └────────────────── payload ──────────────────────┘ └─── signature ───┘

[base64 解码 header]                    [base64 解码 payload]
{ "alg": "HS256" }                       { "userId": 1,
                                            "username": "alice",
                                            "iat": 1781796067,    ← 签发时间(epoch)
                                            "exp": 1782400867 }   ← 过期时间(7天后)
```
⚠️ payload 是 base64 编码，任何人都能解！别把密码塞进去。

签名才是防篡改的关键：改了 payload，签名就对不上，verifyWith() 会抛异常。

### 19.6 DTO 加参数校验 <a id="sec075"></a>

📁 文件：mini-mall-user/src/main/java/com/minimall/user/dto/UserRegisterDTO.java

```
@Data
public class UserRegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度 3~20")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度 6~50")
    private String password;
}
```
配合 Controller 里的 @Valid 才生效：

```
@PostMapping("/register")
public Result<User> register(@Valid @RequestBody UserRegisterDTO dto) {
    return Result.success(userService.register(dto));
}
```
失败时抛 MethodArgumentNotValidException → GlobalExceptionHandler 接住 → 返 400 + 字段名+原因。

### 19.6.1 UserLoginDTO 完整代码 <a id="sec076"></a>

📁 文件：mini-mall-user/src/main/java/com/minimall/user/dto/UserLoginDTO.java

```
package com.minimall.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 登录入参 DTO（@NotBlank 空串/null/全空格都会被拒） */
@Data
public class UserLoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}

```

### 19.6.2 IUserService 完整代码 <a id="sec077"></a>

📁 文件：mini-mall-user/src/main/java/com/minimall/user/service/IUserService.java

```
package com.minimall.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.minimall.user.dto.UserLoginDTO;
import com.minimall.user.dto.UserRegisterDTO;
import com.minimall.user.entity.User;

/**
 * User Service 接口
 * 继承 IService<User> 白嫖 save/getById/list/page/updateById/removeById 等 30+ 通用方法
 * 只需声明【业务专有方法】（register/login）
 */
public interface IUserService extends IService<User> {

    /** 注册 → 返回新创建的 User（id 已回填） */
    User register(UserRegisterDTO dto);

    /** 登录 → 返回 JWT token 字符串 */
    String login(UserLoginDTO dto);
}

```

### 19.7 UserServiceImpl 完整代码 <a id="sec078"></a>

📁 文件：mini-mall-user/src/main/java/com/minimall/user/service/impl/UserServiceImpl.java

```
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements IUserService {

    // ⭐ 静态加密器：无状态、线程安全，全类共享一份
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public User register(UserRegisterDTO dto) {
        // ① 用 Wrapper 查用户名是否已存在
        boolean exists = this.baseMapper.exists(
            new QueryWrapper<User>().eq("username", dto.getUsername())
        );
        if (exists) throw new BusinessException("用户名已存在");

        // ② BCrypt 加密
        String encryptedPwd = ENCODER.encode(dto.getPassword());

        // ③ 组装 User
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(encryptedPwd);

        // ④ 入库（save 从 ServiceImpl 白嫖来的）
        this.save(user);
        return user;   // id 已自动回填
    }

    @Override
    public String login(UserLoginDTO dto) {
        // ① 按 username 查
        User user = this.baseMapper.selectOne(
            new QueryWrapper<User>().eq("username", dto.getUsername())
        );

        // ② 安全：不告诉前端是"用户不存在"还是"密码错"，防爆破
        if (user == null) throw new BusinessException("用户名或密码错误");

        // ③ BCrypt 验证（内部解 salt 重哈希比较）
        boolean matches = ENCODER.matches(dto.getPassword(), user.getPassword());
        if (!matches) throw new BusinessException("用户名或密码错误");

        // ④ 签发 JWT 返回
        return jwtUtil.generateToken(user.getId(), user.getUsername());
    }
}
```

### 19.7.1 三大白嫖父类回顾 <a id="sec079"></a>

这段代码完美演示了 MyBatis-Plus 的【三大白嫖父类】：

| 父类 | 用在哪 | 白嫖了啥 |
|---|---|---|
| BaseMapper<User> | UserMapper extends BaseMapper | this.baseMapper.exists/selectOne 等方法 |
| IService<User> | IUserService extends IService | save/getById/list/page 等方法签名 |
| ServiceImpl<M,T> | UserServiceImpl extends ServiceImpl | this.save(user) 等实现 |

### 19.7.2 UserController 完整代码（B 阶段后） <a id="sec080"></a>

📁 文件：mini-mall-user/src/main/java/com/minimall/user/controller/UserController.java

```
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private IUserService userService;

    /** ① 按 id 查（直接走 Mapper） */
    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable("id") Long id) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        return Result.success(user);
    }

    /**
     * ② 注册：POST /user/register
     * Body: { "username": "bob", "password": "123456" }
     */
    @PostMapping("/register")
    public Result<User> register(@Valid @RequestBody UserRegisterDTO dto) {
        return Result.success(userService.register(dto));
    }

    /**
     * ③ 登录返 JWT：POST /user/login
     * Body: { "username": "alice", "password": "123456" }
     * 返回 data 直接是 token 字符串（前端 localStorage 保存）
     */
    @PostMapping("/login")
    public Result<String> login(@Valid @RequestBody UserLoginDTO dto) {
        return Result.success(userService.login(dto));
    }
}

```

### 19.8 application.yml JWT 配置 <a id="sec081"></a>

📁 文件：mini-mall-user/src/main/resources/application.yml

```
jwt:
  secret: my-mini-mall-super-secret-key-1234567890
  expiration: 604800000
```
⭐ 关键约束：

• secret 至少 32 字节（HMAC-SHA256 要求），少了 Keys.hmacShaKeyFor 抛 WeakKeyException

• expiration 单位毫秒：604800000 = 7 天

• ⚠️ 生产环境用环境变量注入，不要明文写在 yml（会被 GitHub 扫到）

### 19.9 实测：6 个场景全部跑通 <a id="sec082"></a>

登录三种情况：

```
# ① 正确登录
POST /user/login {"username":"alice","password":"123456"}
→ {"code":200,"message":"操作成功","data":"eyJhbGciOi..."}

# ② 密码错误
POST /user/login {"username":"alice","password":"wrong"}
→ {"code":500,"message":"用户名或密码错误","data":null}      ← BusinessException

# ③ 字段为空（触发 @NotBlank）
POST /user/login {"username":"","password":""}
→ {"code":400,"message":"username 用户名不能为空","data":null}  ← MethodArgumentNotValidException
```
注册三种情况：

```
# ④ 新用户注册
POST /user/register {"username":"bob","password":"123456"}
→ {"code":200,"message":"操作成功","data":{"id":3,"username":"bob",...}}

# ⑤ 用户名已存在
POST /user/register {"username":"bob","password":"123456"}
→ {"code":500,"message":"用户名已存在","data":null}

# ⑥ 用户名太短（触发 @Size）
POST /user/register {"username":"ab","password":"123456"}
→ {"code":400,"message":"username 用户名长度 3~20","data":null}

```

### 19.10 整体链路图：登录流程 <a id="sec083"></a>

```
curl POST /user/login {"username":"alice","password":"123456"}
   ↓
[Tomcat 接到 HTTP 请求]
   ↓
[DispatcherServlet 路由]
   ↓
UserController.login(dto)
   ↓ @Valid 触发校验
   ├─ 失败 → MethodArgumentNotValidException → GlobalExceptionHandler 接住 → 400
   └─ 通过 → 继续
   ↓
userService.login(dto)
   ↓
this.baseMapper.selectOne(wrapper)
   ↓ MyBatis 代理拦截 → SQL → DB
   └─ user 对象（含 BCrypt 密文 password）
   ↓
ENCODER.matches(明文, 密文)
   ├─ false → throw BusinessException → GlobalExceptionHandler → 500
   └─ true → 继续
   ↓
jwtUtil.generateToken(userId, username)
   ├─ 拼 claims Map
   ├─ Jwts.builder().signWith(...).compact()
   └─ 返回 "eyJhbGc..." 字符串
   ↓
Result.success(token)
   ↓ Jackson 序列化
   ↓
HTTP 200 + JSON 给前端

```

### 19.11 一句话总结 <a id="sec084"></a>

登录 = BCrypt 验密码 + JwtUtil 签发 token

注册 = 查重 + BCrypt 加密 + this.save 入库

@Valid 触发校验，校验失败抛 MethodArgumentNotValidException → 400

业务失败抛 BusinessException → 500（用 BCrypt 时不暴露『用户存在但密码错』）

## 二十、跨服务调用与 OpenFeign <a id="sec085"></a>

上一节 user-service 已经能独立跑了。现在加第二个服务 product-service，重点：user 怎么拿到 product 的数据？

### 20.0 C 阶段文件清单（整个新模块 + user 改动） <a id="sec086"></a>

📁 工程结构变化：

```
mini-mall-cloud/
├── pom.xml                                ← ✏️ 改：modules 加 mini-mall-product
│
├── mini-mall-user/                        ⭐ 已有，只改少量
│   ├── MiniMallUserApplication.java       ← ✏️ 改：加 @EnableFeignClients
│   ├── controller/UserController.java     ← ✏️ 改：加 /with-product/ 演示接口
│   └── client/                            ← ➕ 整个包新建
│       └── ProductFeignClient.java        ← ➕ Feign 客户端接口
│
└── mini-mall-product/                     ⭐⭐⭐ 整个模块新建
    ├── pom.xml                            ← 跟 user pom 几乎一模一样
    └── src/main/
        ├── java/com/minimall/product/
        │   ├── MiniMallProductApplication.java
        │   ├── controller/ProductController.java
        │   ├── service/
        │   │   ├── IProductService.java
        │   │   └── impl/ProductServiceImpl.java
        │   ├── mapper/ProductMapper.java   ← 含 deductStock 注解 SQL
        │   └── entity/Product.java
        └── resources/application.yml       ← 端口 9002

```

### 19.1 问题起源：单体 vs 微服务的根本差别 <a id="sec087"></a>

单体里跨「模块」拿数据：直接 @Autowired 注入对方 Bean 调用方法。

```
// 单体里（一个 jar 里）：user 调 product
@Service
class UserService {
    @Autowired
    private ProductService productService;  // ✅ 直接拿到同一个 Spring 容器里的 Bean

    public Object getDetail() {
        return productService.getById(1L);   // 同进程内方法调用，纳秒级
    }
}
```
微服务里跨「服务」拿数据：两个独立的 JVM 进程，没法 @Autowired 对方的 Bean。

```
// 微服务里（两个独立的 jar）：
[user-service JVM]                    [product-service JVM]
    端口 9001                              端口 9002
    内存里有 UserService                   内存里有 ProductService
    内存里【没有】ProductService            内存里【没有】UserService

    @Autowired ProductService → ❌ Spring 容器里没这个 Bean，启动报错
```
两个进程要通信，只能走【进程间通信】，最常见的就是 HTTP。

### 19.2 三种跨服务调用方案对比 <a id="sec088"></a>

| 方案 | 怎么做 | 缺点 | 什么时候用 |
|---|---|---|---|
| ① 直连对方 DB | user-service 也连 product 数据库 | 服务边界破了，强耦合，DB 改字段所有服务都受影响 | ❌ 反模式 |
| ② RestTemplate / HttpClient | 手写 HTTP 调用，自己解 JSON | 代码重复多、易错、没类型安全 | 学习用 / 简单脚本 |
| ③ OpenFeign | 声明式：接口 + 注解 = 自动远程调用 | 需要双方契约保持一致 | ✅ 微服务标配 |

### 19.2.1 手写 RestTemplate 版本（反面教材） <a id="sec089"></a>

```
@Service
public class UserService {
    @Autowired
    private RestTemplate restTemplate;   // 需要 @Bean 配置注入

    public Result<Product> getProductDetail(Long productId) {
        // ① 自己拼 URL
        String url = "http://localhost:9002/product/" + productId;

        // ② 自己处理类型擦除（List/Generic 这种泛型要写 ParameterizedTypeReference）
        ResponseEntity<Result> resp = restTemplate.getForEntity(url, Result.class);

        // ③ 自己判 HTTP 状态码、读 body
        if (resp.getStatusCode() != HttpStatus.OK) {
            throw new BusinessException("调 product 失败");
        }

        return resp.getBody();
    }
}
```
缺点：路径硬编码、泛型痛苦、错误处理啰嗦、没类型安全。

### 19.2.2 OpenFeign 版本（专业方案） <a id="sec090"></a>

```
// 接口（不写实现，启动时 Spring 用动态代理给生成代理类）
@FeignClient(name = "mini-mall-product", url = "http://localhost:9002")
public interface ProductFeignClient {
    @GetMapping("/product/{id}")
    Result<Map<String, Object>> getById(@PathVariable("id") Long id);
}

// 业务代码用起来跟本地方法没区别
@Service
public class UserService {
    @Autowired
    private ProductFeignClient productFeignClient;

    public Result<?> getProductDetail(Long productId) {
        return productFeignClient.getById(productId);
    }
}
```
Feign 帮你做的事：

• 自动拼 URL（看 @FeignClient 的 url + @GetMapping 的 path）

• 自动序列化/反序列化 JSON（用 Jackson）

• 自动 HTTP 调用（底层用 OkHttp / HttpClient）

• 自动返回类型映射（你说 Result<Map>，它就给你 Result<Map>）

### 19.3 Feign 的核心：动态代理 <a id="sec091"></a>

⭐ 关键认知：Feign 跟 MyBatis Mapper 是【同一种魔法】——动态代理。

对照学习：

|  | MyBatis Mapper | Feign Client |
|---|---|---|
| 接口长啥样 | extends BaseMapper<T> | @FeignClient + 方法注解 |
| 有实现类吗 | ❌ 没有 | ❌ 没有 |
| 谁给生成代理实例 | MyBatis 启动时 | Spring 启动时（@EnableFeignClients） |
| 代理拦截后干啥 | 拼 SQL → 查 DB → 映射对象 | 拼 HTTP → 调远程 → 反序列化 |
| 业务怎么用 | @Autowired UserMapper | @Autowired ProductFeignClient |

学一次理解一类技术 —— Feign / MyBatis / Spring Data JPA 全是这套。

### 19.4 @EnableFeignClients 启动类开关 <a id="sec092"></a>

📁 文件：mini-mall-user/src/main/java/com/minimall/user/MiniMallUserApplication.java

```
@SpringBootApplication
@ComponentScan("com.minimall")
@MapperScan("com.minimall.user.mapper")
@EnableFeignClients(basePackages = "com.minimall.user.client")   // ⭐ 新增这个
public class MiniMallUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(MiniMallUserApplication.class, args);
    }
}
```
作用：启动时扫描 basePackages 下所有 @FeignClient 标记的接口，给每个接口生成代理放进容器。

⚠️ 不写 basePackages 默认扫【启动类同级及子包】，跟 @ComponentScan 类似。

### 19.5 @FeignClient 注解参数详解 <a id="sec093"></a>

```
@FeignClient(
    name = "mini-mall-product",           // ① 客户端标识
    url = "http://localhost:9002"          // ② 远程服务地址
)
public interface ProductFeignClient {
    @GetMapping("/product/{id}")
    Result<Map<String, Object>> getById(@PathVariable("id") Long id);
}
```

### 19.5.1 name 参数（现阶段 vs 接 Nacos 后） <a id="sec094"></a>

| 阶段 | name 的含义 | url 还要不要 |
|---|---|---|
| 现在（没 Nacos） | 只是【客户端标识】，区分多个 Feign 客户端 + 配置隔离 | 要（硬编码服务地址） |
| 接 Nacos 后 | 【服务发现的服务名】，自动找注册中心查实例 IP | 不要了（自动） |

### 19.5.2 接口方法的注解必须跟对方 Controller【镜像】 <a id="sec095"></a>

```
// product-service 的 Controller（被调方）
@RestController
@RequestMapping("/product")
public class ProductController {
    @GetMapping("/{id}")
    public Result<Product> getById(@PathVariable("id") Long id) { ... }
}

// user-service 的 Feign Client（调用方）
@FeignClient(name = "mini-mall-product", url = "http://localhost:9002")
public interface ProductFeignClient {
    @GetMapping("/product/{id}")           // ⭐ 完整路径要跟对方 RequestMapping + GetMapping 拼起来一致
    Result<Map<String, Object>> getById(@PathVariable("id") Long id);
}
```
⚠️ Feign 客户端的注解【完全是【发请求】用的，跟对方 Controller 镜像但不等价】

它【模仿】对方 Controller 长啥样，然后【发出】对应的 HTTP 请求。

### 19.6 数据契约：为啥用 Map 不用 Product <a id="sec096"></a>

我们的 Feign 接口返回类型是 Result<Map<String, Object>>，不是 Result<Product>。原因：

```
[mini-mall-user 模块]            [mini-mall-product 模块]
   user 的 Java 代码                  product 的 Java 代码
   import com.minimall.user.*         import com.minimall.product.*
   ⚠️ 没 import com.minimall.product.entity.Product
       因为 user 没引 product 模块（避免循环依赖）
```
3 种解决方案：

| 方案 | 怎么做 | 优缺点 |
|---|---|---|
| ① 用 Map 接收 | Result<Map<String,Object>> | ✅ 最简单 ❌ 没类型安全，IDE 提示弱 |
| ② 在 user 模块复制一份 Product 类 | user.dto.ProductDTO 跟 product.entity.Product 同字段 | ✅ 类型安全 ❌ 改字段要同步两边 |
| ③ 抽 mini-mall-product-api 模块 | Product/DTO 放公共 api 模块，所有调用方依赖它 | ✅ 业界标准 ❌ 工程复杂度高 |

⭐ 学习阶段选 ①（最简）；正式项目选 ③（最稳）。

### 19.7 完整调用链路图 <a id="sec097"></a>

```
curl GET http://localhost:9001/user/1/with-product/1

[user-service 9001]
   UserController.getUserWithProduct(1, 1)
     ├─ ① userMapper.selectById(1)
     │     └─ MySQL SELECT * FROM user WHERE id=1
     │        └─ 返回 alice 对象
     │
     └─ ② productFeignClient.getById(1)
            ↓
        Feign 代理对象拦截方法调用
            ↓
        读 @FeignClient(url="http://localhost:9002") + @GetMapping("/product/{id}")
            ↓
        拼出 HTTP 请求 GET http://localhost:9002/product/1
            ↓
        发 HTTP（HttpClient/OkHttp）
                          ▼
                      [product-service 9002]
                          ProductController.getById(1)
                              ↓
                          ProductService.getById(1) (ServiceImpl 白嫖)
                              ↓
                          ProductMapper.selectById(1)
                              ↓
                          MySQL SELECT 拿到小米14
                              ↓
                          Result.success(product) → JSON 响应
                          ▼
        Feign 收到 JSON 响应
            ↓
        用 Jackson 反序列化成 Result<Map<String,Object>>
            ↓
        返给 UserController
     ├─ ③ 拼装 {user, product} 一起返
```

### 20.6.1 mini-mall-product/pom.xml 完整代码 <a id="sec098"></a>

📁 跟 user pom 几乎一样，只是没引 jjwt / bcrypt / validation：

```
<parent>
    <groupId>com.minimall</groupId>
    <artifactId>mini-mall-cloud</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</parent>

<artifactId>mini-mall-product</artifactId>

<dependencies>
    <dependency>
        <groupId>com.minimall</groupId>
        <artifactId>mini-mall-common-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
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

### 20.6.2 MiniMallProductApplication 启动类 <a id="sec099"></a>

📁 文件：mini-mall-product/src/main/java/com/minimall/product/MiniMallProductApplication.java

```
@SpringBootApplication
@ComponentScan("com.minimall")
@MapperScan("com.minimall.product.mapper")
public class MiniMallProductApplication {
    public static void main(String[] args) {
        SpringApplication.run(MiniMallProductApplication.class, args);
    }
}
```
跟 user 启动类一样的套路，没 @EnableFeignClients（product 不调别人）。

### 20.6.3 product/application.yml <a id="sec100"></a>

📁 文件：mini-mall-product/src/main/resources/application.yml

```
server:
  port: 9002

spring:
  application:
    name: mini-mall-product
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/mini_mall?...&serverTimezone=Asia/Shanghai
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
跟 user 几乎一样，差别就 2 处：端口 9002、应用名 mini-mall-product。

⚠️ 用同一个 mini_mall 数据库 = 学习简化（生产环境每个服务一个 schema）。

### 20.6.4 Product 实体类 <a id="sec101"></a>

📁 文件：mini-mall-product/src/main/java/com/minimall/product/entity/Product.java

```
@Getter @Setter
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long categoryId;
    private String name;
    private String description;
    private String detail;
    private BigDecimal price;
    private Integer stock;
    private Integer sales;
    private String coverImage;
    private Byte status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Byte isDeleted;
}

```

### 20.6.5 ProductMapper（含防超卖注解 SQL） <a id="sec102"></a>

📁 文件：mini-mall-product/src/main/java/com/minimall/product/mapper/ProductMapper.java

```
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @Update("UPDATE product SET stock = stock - #{quantity}, sales = sales + #{quantity} " +
            "WHERE id = #{productId} AND stock >= #{quantity}")
    int deductStock(@Param("productId") Long productId,
                    @Param("quantity") Integer quantity);

    @Update("UPDATE product SET stock = stock + #{quantity}, sales = sales - #{quantity} " +
            "WHERE id = #{productId}")
    int restoreStock(@Param("productId") Long productId,
                     @Param("quantity") Integer quantity);
}

```

### 20.6.6 ProductService 接口 + 实现 <a id="sec103"></a>

📁 IProductService.java（接口）

```
public interface IProductService extends IService<Product> {
    // 现阶段空的，靠 IService 白嫖通用 CRUD
}
```
📁 ProductServiceImpl.java（实现）

```
@Service
public class ProductServiceImpl
        extends ServiceImpl<ProductMapper, Product>
        implements IProductService {
    // 自定义业务方法在这里加，现阶段空的
}

```

### 20.6.7 ProductController（被 Feign 调的对象） <a id="sec104"></a>

📁 文件：mini-mall-product/src/main/java/com/minimall/product/controller/ProductController.java

```
@RestController
@RequestMapping("/product")
public class ProductController {

    @Autowired
    private IProductService productService;

    @GetMapping("/{id}")
    public Result<Product> getById(@PathVariable("id") Long id) {
        Product product = productService.getById(id);
        if (product == null) throw new BusinessException("商品不存在");
        return Result.success(product);
    }

    @GetMapping
    public Result<List<Product>> list() {
        List<Product> products = productService.list();
        return Result.success(products.subList(0, Math.min(10, products.size())));
    }
}
```
⭐ Feign Client 的注解必须跟这个 Controller【完全镜像】：

```
ProductController（被调方）            ProductFeignClient（调方）
@RequestMapping("/product")           ─┐
                                       ├─ url + path 拼起来：
@GetMapping("/{id}")                  ─┘   http://localhost:9002/product/{id}
public Result<Product> getById(            └────────────────┬──────────────┘
    @PathVariable("id") Long id)                            │
                                                            ▼
                                       @FeignClient(url="http://localhost:9002")
                                       @GetMapping("/product/{id}")
                                       Result<...> getById(@PathVariable("id") Long id)

```

### 20.6.8 ProductFeignClient（user 端的接口） <a id="sec105"></a>

📁 文件：mini-mall-user/src/main/java/com/minimall/user/client/ProductFeignClient.java

```
package com.minimall.user.client;

import com.minimall.common.core.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "mini-mall-product", url = "http://localhost:9002")
public interface ProductFeignClient {

    @GetMapping("/product/{id}")
    Result<Map<String, Object>> getById(@PathVariable("id") Long id);
}
```
⭐ 不需要实现类，启动时 Spring 用动态代理生成代理实例放进容器。

### 20.6.9 UserController 加 Feign 演示接口 <a id="sec106"></a>

📁 文件：mini-mall-user/src/main/java/com/minimall/user/controller/UserController.java

```
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired private UserMapper userMapper;
    @Autowired private IUserService userService;
    @Autowired private ProductFeignClient productFeignClient;   // ⭐ 新注入 Feign Client

    // ... 原有的 /user/{id} /register /login 接口省略

    /**
     * ④ Feign 调用演示：拼装【用户 + 商品】信息
     * GET http://localhost:9001/user/{userId}/with-product/{productId}
     */
    @GetMapping("/{userId}/with-product/{productId}")
    public Result<Map<String, Object>> getUserWithProduct(@PathVariable Long userId,
                                                          @PathVariable Long productId) {
        // ① 查本地用户
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");

        // ② ⭐ Feign 调远程服务，看起来像调本地方法
        Result<Map<String, Object>> productResp = productFeignClient.getById(productId);
        if (productResp.getCode() != 200) {
            throw new BusinessException(productResp.getMessage());
        }

        // ③ 拼装返回
        Map<String, Object> data = new HashMap<>();
        data.put("user", user);
        data.put("product", productResp.getData());
        return Result.success(data);
    }
}

```

### 20.6.10 MiniMallUserApplication 加 @EnableFeignClients <a id="sec107"></a>

📁 文件：mini-mall-user/src/main/java/com/minimall/user/MiniMallUserApplication.java

```
@SpringBootApplication
@ComponentScan("com.minimall")
@MapperScan("com.minimall.user.mapper")
@EnableFeignClients(basePackages = "com.minimall.user.client")   // ⭐ 新增
public class MiniMallUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(MiniMallUserApplication.class, args);
    }
}
```
⭐ basePackages 必须包含放 Feign Client 接口的包，否则 Spring 扫不到、不生成代理。

### 20.6.11 实测响应 <a id="sec108"></a>

```
# ① 双服务都 OK
GET /user/1/with-product/1
→ {"code":200,"data":{"user":{...alice...}, "product":{...小米14...}}}

# ② 商品不存在（product 服务返 500）
GET /user/1/with-product/999999
→ {"code":500,"message":"商品不存在","data":null}   ← user 透传错误

# ③ 用户不存在（user 自己拦下，不调 product）
GET /user/999999/with-product/1
→ {"code":500,"message":"用户不存在","data":null}

```

### 20.8 旧版代码索引（简略对照） <a id="sec109"></a>

📁 整个 Feign 链路涉及的文件：

```
mini-mall-user/
├── MiniMallUserApplication.java       ← 加 @EnableFeignClients
├── client/
│   └── ProductFeignClient.java        ← Feign 接口
└── controller/
    └── UserController.java
        └── getUserWithProduct(...)    ← 演示接口

mini-mall-product/                     ← 第 2 个微服务整套
├── MiniMallProductApplication.java
├── controller/ProductController.java  ← 被调方
├── service/{IProductService, impl/}   ← 业务层
├── mapper/ProductMapper.java          ← MP Mapper
├── entity/Product.java                ← 实体
├── pom.xml                            ← 跟 user 几乎一样
└── application.yml                    ← 端口 9002
```

### 19.9 常见错误速查 <a id="sec110"></a>

| 错误现象 | 原因 | 修复 |
|---|---|---|
| 启动 user 报 BeanCreation 错 | @EnableFeignClients 没加 / basePackages 写错 | 启动类加注解，检查包路径 |
| Feign 调用返 404 | 接口的 @GetMapping path 跟对方 Controller 不一致 | 对照对方 RequestMapping + 方法注解拼完整路径 |
| Feign 调用 Connection refused | 对方服务没启动 / url 端口写错 | 先 curl 对方接口确认能通 |
| 返回的 data 字段都是 null | Map 的 key 跟对方 Controller 返的字段名对不上 | 看对方实际 JSON，对齐 key |
| @FeignClient name 写错或重复 | Feign Client 标识冲突 | 全项目 name 唯一，一般等于对方 spring.application.name |

### 19.10 一句话总结 <a id="sec111"></a>

Feign = MyBatis 风格的 HTTP 客户端：接口 + 注解 + 动态代理。

现阶段用硬编码 url，接 Nacos 后改用 name=服务名自动发现。

跨服务的【数据契约】是后续重点（DTO 模块化）。

# mini-mall-cloud D 阶段：网关 <a id="sec112"></a>

D1 + D2 完整记录 | 基础知识深入版 + 完整代码 + 架构图 + 三大踩坑实录

2026-06-19  |  与 v7 docx 配套阅读

## 21.0 D 阶段全景 <a id="sec113"></a>

### D 阶段目标一句话 <a id="sec114"></a>

给整个微服务系统加一个【统一入口】mini-mall-gateway。

以前前端要记 N 个端口（9001 / 9002 / 9003...），

以后前端只记一个端口（9080），网关按 path 自动转发到对应下游服务。

### 加网关前 vs 后（架构图） <a id="sec115"></a>

```

加网关之前：

    浏览器
      ├──→ http://localhost:9001/user/login        (登录)
      ├──→ http://localhost:9001/user/{id}         (查用户)
      ├──→ http://localhost:9002/product/{id}      (查商品)
      └──→ http://localhost:9003/order/...         (订单)
              ↑
        前端记 N 个端口
        每个服务自己鉴权 / CORS / 日志 → 复制粘贴地狱

────────────────────────────────────────────────────────

加网关之后：

                  ┌──────────────────────────────────┐
    浏览器 ─→ 9080│ mini-mall-gateway                │
                  │                                  │
                  │  ① 路由：根据 path 转发           │
                  │     /user/**    → 9001            │
                  │     /product/** → 9002            │
                  │                                  │
                  │  ② 全局 Filter（D3 步骤）：       │
                  │     - JWT 鉴权                   │
                  │     - 白名单（登录/注册）         │
                  │     - 解析 token → 塞 X-User-Id   │
                  │     - 跨域 / 限流 / 日志          │
                  └─────┬─────────────────┬──────────┘
                        │                 │
                  ┌─────▼────┐      ┌─────▼──────┐
                  │ user:9001│      │product:9002│
                  └──────────┘      └────────────┘
```

### 4 小步路线图 <a id="sec116"></a>

| 步骤 | 做什么 | 状态 |
|---|---|---|
| D1 | 建 mini-mall-gateway 模块骨架 | ✓ 完成 |
| D2 | yml 配 2 条静态路由 | ✓ 完成 |
| D3 | GlobalFilter 做 JWT 鉴权 + 透传 userId | 进行中 |
| D4 | 三 jar 联调 | 待启动 |

## 21.1 基础：为什么需要网关 <a id="sec117"></a>

| 痛点 | 无网关时 | 有网关后 |
|---|---|---|
| 前端复杂度 | 记 N 个端口，加服务改配置 | 只记 1 个端口 |
| 鉴权重复 | 每个服务都写一遍 JwtInterceptor | 网关一处搞定 |
| 跨域 CORS | 每个服务都配 CORS Filter | 网关统一配 |
| 限流/日志 | 散落各处，没有统一入口 | 网关统一切面 |

核心思想：横切关注点（鉴权/跨域/日志）统一在网关；业务服务只管业务。

## 21.2 单体 JwtInterceptor 怎么变成微服务 Gateway 的 <a id="sec118"></a>

### 21.2.1 单体里 JwtInterceptor 的完整代码 <a id="sec119"></a>

文件：mini-mall (单体)/src/main/java/com/minimall/minimall/interceptor/JwtInterceptor.java

```

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 在 Controller 方法执行【之前】调
     * 返 true 放行，返 false 中断
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        // ① 拿 token
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            response.setStatus(401);
            return false;
        }
        token = token.substring(7);  // 去掉 "Bearer " 前缀

        // ② 解 token（解失败抛异常，被 Catch 转 401）
        try {
            Long userId = jwtUtil.getUserIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);

            // ③ ⭐ 塞 ThreadLocal —— 这是关键
            BaseContext.setCurrentId(userId);
            BaseContext.setCurrentUsername(username);

            return true;
        } catch (Exception e) {
            response.setStatus(401);
            return false;
        }
    }

    /**
     * Controller 方法执行【完毕之后】调
     * 必须清 ThreadLocal，否则下次请求复用线程会污染数据
     */
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        BaseContext.clear();   // ⭐ 清理 ThreadLocal
    }
}
```

### 21.2.2 userContext —— ThreadLocal 的封装 <a id="sec120"></a>

文件：BaseContext.java（单体里的工具类）

```

public class BaseContext {

    // ⭐ 核心：ThreadLocal —— 每个线程一个独立储物柜
    private static final ThreadLocal<Long> CURRENT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USERNAME = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        CURRENT_ID.set(id);              // 塞进【当前线程】的储物柜
    }

    public static Long getCurrentId() {
        return CURRENT_ID.get();          // 从【当前线程】的储物柜取
    }

    public static void setCurrentUsername(String name) {
        CURRENT_USERNAME.set(name);
    }

    public static String getCurrentUsername() {
        return CURRENT_USERNAME.get();
    }

    public static void clear() {
        CURRENT_ID.remove();              // 清理（防内存泄漏 + 防污染）
        CURRENT_USERNAME.remove();
    }
}
```

### 21.2.3 ⭐ ThreadLocal 到底是什么？ <a id="sec121"></a>

一句话：每个线程有一个【专属储物柜】，互相看不见。

```

ThreadLocal 的工作原理：

每个 Thread 对象内部都有一个 ThreadLocalMap
（你可以理解成一个 Map<ThreadLocal对象, 实际值>）

  Thread-A 的 ThreadLocalMap:
     {CURRENT_ID → 100, CURRENT_USERNAME → "alice"}

  Thread-B 的 ThreadLocalMap:
     {CURRENT_ID → 200, CURRENT_USERNAME → "bob"}

当 Thread-A 调 CURRENT_ID.set(100):
     → 找当前线程（Thread-A）的 ThreadLocalMap
     → 塞进去 (CURRENT_ID, 100)

当 Thread-A 调 CURRENT_ID.get():
     → 找当前线程（Thread-A）的 ThreadLocalMap
     → 拿 100 出来

⚠️ Thread-B 完全看不到 Thread-A 的数据，反之亦然。
   两个线程互相隔离。
```

### 21.2.4 真实场景类比 <a id="sec122"></a>

```

餐厅类比：

  ThreadLocal = 每张桌子有一个抽屉
  Thread = 桌子
  set / get = 服务员在【当前桌】的抽屉里放/拿东西

  ┌──── 1 号桌 ────┐  ┌──── 2 号桌 ────┐  ┌──── 3 号桌 ────┐
  │  抽屉:         │  │  抽屉:         │  │  抽屉:         │
  │  - 客人=alice  │  │  - 客人=bob    │  │  - 客人=charlie│
  │  - 总额=58 元   │  │  - 总额=120 元  │  │  - 总额=30 元   │
  └────────────────┘  └────────────────┘  └────────────────┘

  服务员在 1 号桌的抽屉拿东西时，
  完全不会拿到 2 号桌、3 号桌的内容。

  关键点：抽屉里的东西，只有【这张桌子的人】能看见。
```

### 21.2.5 单体里为什么能用 ThreadLocal？ <a id="sec123"></a>

```

单体里的完整流程：

  浏览器 ─→ Tomcat 收到请求
            │
            └─ 从【线程池】挑一个空闲线程 Thread-A 处理
                  │
                  ↓ 这一整个请求【自始至终】都在 Thread-A 上跑
                  │
                  ① JwtInterceptor.preHandle()      ← 在 Thread-A
                  │   BaseContext.setCurrentId(100) ← 塞 Thread-A 的储物柜
                  │
                  ② Controller.method()             ← 还在 Thread-A
                  │   BaseContext.getCurrentId()    ← 从 Thread-A 储物柜拿
                  │   → 100  ✓ 拿到了！
                  │
                  ③ Service.doBusinessLogic()       ← 还在 Thread-A
                  │   BaseContext.getCurrentId()    ← 同一个储物柜
                  │   → 100  ✓ 拿到了！
                  │
                  ④ JwtInterceptor.afterCompletion()← 还在 Thread-A
                      BaseContext.clear()           ← 清掉

为什么 Controller 能拿到？
   因为 Interceptor → Controller → Service 三者跑在【同一个线程】，
   储物柜（ThreadLocal）共享。
```

### 21.2.6 微服务为什么不能用 ThreadLocal？ <a id="sec124"></a>

```

微服务的完整流程：

  浏览器 ─→ Gateway (Netty 进程 :9080)
              │
              └─ Netty 线程池里挑个 Thread-G 处理
                    │
                    ① AuthGlobalFilter.filter()        ← 在 Thread-G
                    │   假设：BaseContext.setId(100)
                    │   ← 塞进 Gateway 进程的 Thread-G 储物柜
                    │
                    ② HTTP 转发到 user-service
                          │  ⚠️ 这里是【跨进程】，是 HTTP 调用！
                          │
                          ▼
              ┌─ user-service (Tomcat 进程 :9001) ─┐
              │     │                              │
              │     └─ Tomcat 线程池挑 Thread-U    │
              │            │                       │
              │            ③ UserController       │
              │               BaseContext.getId() │
              │               → null ❌ 拿不到！  │
              │                                    │
              │   因为 Thread-U 是 user 进程里的，  │
              │   跟 Gateway 的 Thread-G 是【不同  │
              │   JVM、不同线程池、不同储物柜】   │
              └────────────────────────────────────┘

ThreadLocal 是【进程内 + 线程内】的，
跨进程？根本传不过去！
```

### 21.2.7 微服务的解法：HTTP header 透传 <a id="sec125"></a>

既然内存传不过去，那就用 HTTP 本身传 —— HTTP header。

```

新方案：Gateway 把 userId 塞进 HTTP header
        下游服务从 header 读出来

  浏览器 ─→ Gateway (:9080)
              │
              ① AuthGlobalFilter 解 token → userId = 100
              │
              ② ⭐ 改写请求：往 HTTP header 加 X-User-Id = 100
              │
              ▼ HTTP 转发（header 跟着请求一起走）
              │
              用户服务 (:9001)
              │
              ③ Controller 里：
              │   Long userId = request.getHeader("X-User-Id"); // 100 ✓
              │
              ④ 业务正常用

关键：HTTP header 是【HTTP 协议】的一部分，
      跨进程不是问题（HTTP 本就是设计来跨机器的）
```

### 21.2.8 Gateway 里写 Filter 的代码（D3 预告） <a id="sec126"></a>

```

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * filter 方法接收 ServerWebExchange（WebFlux 版的 request+response）
     * 返回 Mono<Void>（响应式数据流）
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // ① 白名单：登录/注册不校验
        if (path.startsWith("/user/login") || path.startsWith("/user/register")) {
            return chain.filter(exchange);
        }

        // ② 拿 token
        String token = request.getHeaders().getFirst("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        token = token.substring(7);

        // ③ 解 token
        Long userId;
        try {
            userId = jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            return unauthorized(exchange);
        }

        // ④ ⭐ 改写请求：往 header 加 X-User-Id
        ServerHttpRequest mutated = request.mutate()
                .header("X-User-Id", String.valueOf(userId))
                .build();

        // ⑤ 继续往下走（带改过的 request）
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100;  // 越小越先执行，确保鉴权先于路由转发
    }
}
```

### 21.2.9 下游 Controller 怎么取 X-User-Id <a id="sec127"></a>

```

方案 A：每个 Controller 直接读 header（笨办法）
  @GetMapping("/me")
  public Result me(HttpServletRequest request) {
      Long userId = Long.valueOf(request.getHeader("X-User-Id"));
      return Result.success(userService.getById(userId));
  }

方案 B：写个 HeaderInterceptor 自动塞回 ThreadLocal（推荐，对齐单体）
  @Component
  public class HeaderInterceptor implements HandlerInterceptor {
      @Override
      public boolean preHandle(HttpServletRequest req, ...) {
          String userId = req.getHeader("X-User-Id");
          if (userId != null) {
              BaseContext.setCurrentId(Long.valueOf(userId));
          }
          return true;
      }
      @Override
      public void afterCompletion(...) {
          BaseContext.clear();
      }
  }

  → 这样下游业务代码可以照旧写 BaseContext.getCurrentId()
    完全不用知道"上游是网关解的 token"。漂亮 ✓
```

### 21.2.10 单体 vs 微服务 完整对比 <a id="sec128"></a>

| 维度 | 单体 + JwtInterceptor | 微服务 + Gateway |
|---|---|---|
| 在哪解 token | user 服务自己内部 | Gateway 统一解 |
| userId 怎么传 | 塞 ThreadLocal | 塞 HTTP header X-User-Id |
| 跨进程？ | 不需要（同进程同线程） | 必须（跨进程） |
| 下游服务感知 | Controller 直接 BaseContext.getId() | HeaderInterceptor 把 header 转回 ThreadLocal |
| JwtUtil 在哪 | user-service 自己一份 | common-security 模块（让 gateway/各服务复用） |
| 业务代码要改吗 | 不用改 | 不用改（HeaderInterceptor 屏蔽了差异） |

⭐ 关键设计思想：业务代码不应该知道自己是单体还是微服务

通过 HeaderInterceptor 桥接，业务代码用 BaseContext.getId() 即可，单体/微服务都不变。

## 21.3 基础：Spring MVC 是什么 <a id="sec129"></a>

### 21.3.1 名字来历 <a id="sec130"></a>

MVC = Model + View + Controller，三个英文单词的首字母。

这不是 Spring 发明的，是 1979 年 Smalltalk 团队发明的【代码分层组织思想】。

核心一句话：别把「数据」、「展示」、「控制」混一锅写。

### 21.3.2 三个角色干什么 <a id="sec131"></a>

| 角色 | 干啥 | 你项目里是谁 |
|---|---|---|
| Model（模型） | 数据 + 业务逻辑 | User 实体、UserService、UserMapper、users 表 |
| View（视图） | 给用户看的东西 | 返回的 JSON（前后端分离后退化了） |
| Controller（控制器） | 接请求、协调、返响应 | UserController、ProductController |

### 21.3.3 用你的 login 接口走一遍 <a id="sec132"></a>

```

浏览器：POST /user/login {"username":"alice", "password":"123456"}
   │
   ↓
┌──── ① Controller 层（前台接待）──────────────┐
│  UserController.login(dto) {                  │ ← 接请求
│      return Result.success(                   │ ← 返响应
│          userService.login(dto)   ← 派给 Service
│      );
│  }
└────────────────────────────────────────────────┘
   │
   ↓
┌──── ② Model 层（真正干活）────────────────────┐
│                                                │
│  Service（业务规则）：                         │
│  UserServiceImpl.login(dto) {                  │
│      User user = userMapper.selectOne(条件);  │
│      if (!ENCODER.matches(密码)) throw ...;   │
│      return jwtUtil.generateToken(...);       │
│  }                                             │
│                                                │
│  Mapper（数据访问）：                           │
│  UserMapper.selectOne(条件) → 数据库           │
│                                                │
│  Entity（数据本身）：                           │
│  User { id, username, password, ... }         │
└────────────────────────────────────────────────┘
   │
   ↓
┌──── ③ View（半个，退化了）──────────────────┐
│  {"code":200,"data":"eyJhbGc..."}            │ ← 给浏览器
└────────────────────────────────────────────────┘
```

### 21.3.4 为什么要这么分层？ <a id="sec133"></a>

| 场景 | 不分层（混一锅写） | MVC 分层 |
|---|---|---|
| MySQL → PostgreSQL | 全项目大改 | 只改 Mapper 层 |
| Web + 移动端 | Web 代码复制粘贴 | Service 不变，写两个 Controller |
| 写单元测试 | 黏在一起没法测 | Service 单独测，Mapper Mock |
| 团队分工 | 谁碰谁吵架 | 前端改 View、后端改 Model |

### 21.3.5 Spring MVC ≠ MVC（最容易混的点） <a id="sec134"></a>

• MVC = 一种【设计思想】（1979 年的老古董）

• Spring MVC = 一个【框架】，实现了 MVC 的【Controller 层】

Spring MVC 只管「接请求→找方法→派活→返响应」，Model 层不归它管（你用 MyBatis-Plus 都行），View 也不归它管（前端用 Vue 都行）。

严格说应该叫 "Spring C"，但历史习惯了。

### 21.3.6 你写的注解全是 Spring MVC <a id="sec135"></a>

```

@RestController              ← MVC：标记 Web 控制器
@RequestMapping("/user")     ← MVC：路径前缀
public class UserController {

    @GetMapping("/{id}")     ← MVC：处理 GET 请求
    @PostMapping("/login")   ← MVC：处理 POST 请求
    @PathVariable("id")      ← MVC：从 URL 路径取值
    @RequestBody             ← MVC：把 JSON Body 反序列化成对象
    @RequestParam            ← MVC：从 query string 取值
}

你以为是 Spring Boot 提供的，其实底层都是 Spring MVC。
Boot 只是帮你自动装配好了 MVC。
```

### 21.3.7 MVC 跑在什么之上 <a id="sec136"></a>

```

┌──────────────────────────────────────┐
│  你的 Controller 代码                 │
├──────────────────────────────────────┤
│  Spring MVC（DispatcherServlet）     │ ← MVC 的入口大脑
├──────────────────────────────────────┤
│  Servlet API（jakarta.servlet）      │ ← Java 的 Web 标准 API
├──────────────────────────────────────┤
│  Tomcat（Servlet 容器）              │ ← 真正监听端口的家伙
└──────────────────────────────────────┘

启动 user-service 时日志里那条：
   "Tomcat started on port 9001"
就是最底层 Tomcat 报到。

spring-boot-starter-web 这个依赖 = MVC + Servlet + 内嵌 Tomcat 一起到位。
```

## 21.4 基础：Spring WebFlux + Reactor <a id="sec137"></a>

### 21.4.1 一句话定位 <a id="sec138"></a>

WebFlux = Spring MVC 的【异步孪生兄弟】，专门为高并发轻量请求设计。

Gateway 必须用 WebFlux，因为网关要处理大量并发请求，每个请求处理都很轻量（只做路由转发）。

### 21.4.2 MVC（同步阻塞）vs WebFlux（异步非阻塞） <a id="sec139"></a>

先理解【阻塞】的痛：

```

MVC（同步阻塞）模型：

  Tomcat 线程池有 200 个线程

  请求 1 进来 → 占用 Thread-1
                Thread-1 调 DB 查询...
                数据库慢，DB 用了 3 秒才返回
                这 3 秒 Thread-1 在干嘛？
                ⚠️ 什么都不干！傻站着等！
                CPU 闲死

  请求 2 进来 → 占用 Thread-2
                Thread-2 也调 DB...也等 3 秒
                Thread-2 也傻站着

  ...

  请求 201 进来 → 没线程了！排队！

  → 高并发场景下：线程被"占着不干活"
  → 想支持更多并发，只能加更多线程（占内存）

WebFlux（异步非阻塞）模型：

  Netty 只有少量"事件循环线程"（默认 = CPU 核数 * 2，比如 16 个）

  请求 1 进来 → 一个线程 Thread-EL 接收
                发起 DB 查询（异步）
                "回调函数我留下，数据回来再调我"
                Thread-EL 立刻空出来！

  请求 2 进来 → 同一个 Thread-EL 接收
                又发一个异步请求
                又空出来！

  ...

  请求 1000 进来 → 还是 Thread-EL 在处理！

  3 秒后，DB 1 返回数据：
                操作系统通知 Netty："请求 1 的数据回来了"
                Netty 拿出之前保存的回调，继续处理
                Thread-EL 接着处理

  → 16 个线程就能撑 10000+ 并发
  → 但代码必须写成"非阻塞"风格（return Mono / Flux）
```

### 21.4.3 真实场景类比 <a id="sec140"></a>

```

餐厅类比：

  MVC：
    每桌一个服务员（200 桌 = 200 个服务员）
    点菜后服务员【守着】客人，菜上完才能去服务别人
    并发能力 = 服务员数量

  WebFlux：
    一个服务员管 20 桌
    点菜后告诉厨房"3 号桌好了喊我"，服务员去服务下桌
    厨房好了喊"3 号桌菜好了"，服务员送过去
    并发能力 = 远高于服务员数量

  关键差异：服务员【不傻站着等】，事件驱动。
```

### 21.4.4 Reactor 是什么 <a id="sec141"></a>

Reactor = Spring 用的【响应式编程库】，提供 Mono 和 Flux 两个核心类型。

WebFlux 完全建立在 Reactor 上 —— 没 Reactor 就没 WebFlux。

### 21.4.5 Mono 和 Flux 是什么 <a id="sec142"></a>

```

Mono<T>  = 一个"承诺会给你 0 或 1 个 T 数据"的盒子
Flux<T>  = 一个"承诺会给你 0~N 个 T 数据"的盒子

类比 JavaScript 的 Promise：

  Java MVC 直接返：
    User user = userService.getById(1);  // 阻塞拿到 User

  Java WebFlux 返 Mono：
    Mono<User> mono = userService.getById(1);  // 返回一个"将来会给你 User 的盒子"
    // 此时 user 还没拿到！

  要等 mono 触发了才能拿到值：
    mono.subscribe(user -> {
        System.out.println(user);  // 数据回来时执行
    });

  或者用链式操作：
    Mono<String> nameMono = mono.map(user -> user.getUsername());

⚠️ Mono / Flux 是"懒"的：
   不订阅（subscribe）不执行。
   类比"你下单了菜单但没付款，厨房不开始做"。
```

### 21.4.6 Mono 常见用法 <a id="sec143"></a>

```

Mono.just("hello")              // 立即返回一个 "hello" 的 Mono
Mono.empty()                    // 空 Mono（什么也不返）
Mono.error(new RuntimeException("出错"))  // 直接抛错

// 链式转换
Mono.just("hello")
    .map(s -> s.toUpperCase())          // "HELLO"
    .map(s -> s + "!")                  // "HELLO!"
    .subscribe(System.out::println);   // 打印 HELLO!

// 错误处理
mono.onErrorReturn("默认值")
    .doOnSuccess(v -> log.info("成功"))
    .doOnError(e -> log.error("失败"))

// 跟其他 Mono 组合
Mono.zip(monoA, monoB, (a, b) -> a + b)
```

### 21.4.7 Gateway 里 Filter 长什么样 <a id="sec144"></a>

```

@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    //          ↑                    ↑                          ↑
    //   返回值是 Mono            WebFlux 版的请求             过滤器链
    //   (响应式)                request+response 二合一

    // 同步代码可以正常写
    String path = exchange.getRequest().getURI().getPath();
    if (path.equals("/user/login")) {
        return chain.filter(exchange);   // 放行 → 返回链上的 Mono
    }

    // 异步代码必须返 Mono
    return someAsyncCheck(token)         // Mono<Boolean>
        .flatMap(valid -> {
            if (!valid) {
                return unauthorized(exchange);
            }
            return chain.filter(exchange);
        });
}

⚠️ filter 方法返回 Mono<Void>，不是 void！
   WebFlux 全链路都是 Mono，写 void 会直接卡住整个请求。
```

### 21.4.8 Netty vs Tomcat <a id="sec145"></a>

| 对比项 | Tomcat（MVC） | Netty（WebFlux） |
|---|---|---|
| 编程模型 | Servlet（同步） | Reactor 模式（异步） |
| 默认线程数 | 200 个工作线程 | CPU 核数 × 2 |
| I/O 模型 | BIO/NIO（一线程一连接为主） | NIO（一线程多连接） |
| 占内存 | 高（200 线程 × 1MB 栈） | 低（16 线程） |
| 适合 | 中等并发，业务复杂 | 高并发，业务简单（如转发） |

### 21.4.9 MVC vs WebFlux 维度对比汇总 <a id="sec146"></a>

| 维度 | Spring MVC（user/product） | Spring WebFlux（Gateway） |
|---|---|---|
| 跑在啥之上 | Servlet API（jakarta.servlet） | Reactor |
| 底层服务器 | Tomcat | Netty |
| 线程模型 | 一请求一线程 | 少量线程处理多请求 |
| 你写啥 | return Result.success(...) | return Mono<Result> |
| 阻塞性 | 阻塞 | 非阻塞 |
| 核心依赖 | spring-boot-starter-web | spring-boot-starter-webflux |
| 典型场景 | CRUD 业务 | 网关、聊天室 |

⚠️ 这两个依赖不能同时引入 —— 一个 JVM 只能跑一种 Web 栈。

所以 gateway 的 pom 绝对不能加 spring-boot-starter-web。

### 21.4.10 spring-cloud-starter-gateway 拉了什么 <a id="sec147"></a>

```

spring-cloud-starter-gateway
   ├── spring-boot-starter-webflux   ← 间接拉来 WebFlux
   │     ├── Netty                   ← 间接拉来 Netty
   │     └── Reactor                 ← 间接拉来响应式核心
   └── spring-cloud-context          ← Spring Cloud 基础

所以 gateway 的 pom 引一个 spring-cloud-starter-gateway 就够了，
WebFlux / Netty / Reactor 全是间接依赖。
```

## 21.5 基础：Spring Cloud Gateway 配置三件套 <a id="sec148"></a>

| 概念 | 作用 | 本阶段用到？ |
|---|---|---|
| routes 路由表 | 声明转发规则数组 | ✓ D2 |
| predicates 条件 | Path/Method/Header 等匹配条件 | ✓ D2 |
| filters 过滤器 | 请求/响应处理链 | D3 用 |

```

一条最简路由：

routes:
  - id: user-route                # 路由唯一标识（自起名）
    uri: http://localhost:9001    # 目标地址
    predicates:                   # 匹配条件
      - Path=/user/**             # path 前缀 /user/ 才匹配
```

| predicates 类型 | 示例 | 含义 |
|---|---|---|
| Path | Path=/user/** | path 前缀匹配，** 通配后续多层 |
| Method | Method=GET,POST | HTTP 方法过滤 |
| Header | Header=X-Tenant,gold-.* | header 值匹配正则 |
| Query | Query=name,bob.* | query 匹配 |

## 21.6 D 阶段完成后的项目结构 <a id="sec149"></a>

```

mini-mall-cloud/                              ← 父项目
│
├── pom.xml                                   ← 父 pom（modules 加 gateway）
│
├── mini-mall-common/
│   └── mini-mall-common-core/
│       └── src/main/java/com/minimall/common/core/
│           ├── domain/Result.java
│           └── exception/
│               ├── BusinessException.java
│               └── GlobalExceptionHandler.java  ← 被改了（去掉 Servlet）
│
├── mini-mall-user/                           ← 用户服务（9001）
├── mini-mall-product/                        ← 商品服务（9002）
│
└── mini-mall-gateway/                        ← ⭐ D 阶段新建
    ├── pom.xml
    └── src/main/
        ├── java/com/minimall/gateway/
        │   └── MiniMallGatewayApplication.java
        └── resources/
            └── application.yml
```

| 文件 | 类型 | 作用 |
|---|---|---|
| mini-mall-gateway/pom.xml | 新增 | 引 starter-gateway + common-core + jjwt |
| MiniMallGatewayApplication.java | 新增 | 启动类 |
| application.yml | 新增 | 端口 9080 + 2 条静态路由 |
| 父 pom.xml | 改 | modules 加 <module>mini-mall-gateway</module> |
| GlobalExceptionHandler.java | 改 | 去掉 HttpServletRequest |

## 21.7 D1：建网关骨架（完整代码 + 行级讲解） <a id="sec150"></a>

### 21.7.1 父 pom 修改：加 modules <a id="sec151"></a>

文件：mini-mall-cloud/pom.xml

```

<modules>
    <module>mini-mall-common</module>
    <module>mini-mall-user</module>
    <module>mini-mall-product</module>
    <module>mini-mall-gateway</module>            <!-- ⭐ 解开 -->
</modules>
```
• 父级 mvn install 会按 modules 顺序编译

• 看到 <module>mini-mall-gateway</module> 就会进对应文件夹找 pom.xml

• 所以下一步必须建好文件夹+pom.xml

### 21.7.2 mini-mall-gateway/pom.xml 完整代码 <a id="sec152"></a>

```

<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <!-- ① 继承父 pom：拿 Spring Boot 3.3.5 + 所有版本锁 -->
    <parent>
        <groupId>com.minimall</groupId>
        <artifactId>mini-mall-cloud</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>mini-mall-gateway</artifactId>
    <name>mini-mall-gateway</name>
    <description>网关：统一入口 + JWT 鉴权 + 路由转发</description>

    <dependencies>

        <!-- ⭐ ② Spring Cloud Gateway 核心 -->
        <!-- 一行话：这一个就拉来 WebFlux + Netty + Reactor 全家桶 -->
        <!-- 所以【千万别再引 spring-boot-starter-web】，跟 Servlet 冲突 -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>

        <!-- ③ 复用公共模块：Result / BusinessException 等 -->
        <dependency>
            <groupId>com.minimall</groupId>
            <artifactId>mini-mall-common-core</artifactId>
        </dependency>

        <!-- ④ JWT 三件套（D3 步骤 GlobalFilter 解 token 要用） -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <scope>runtime</scope>     <!-- 只跑时挂载 -->
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <scope>runtime</scope>
        </dependency>

    </dependencies>

    <build>
        <plugins>
            <!-- 打 fat jar -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

</project>
```

### 21.7.2 pom 关键讲解 <a id="sec153"></a>

| 依赖 | 作用 | 注意点 |
|---|---|---|
| spring-cloud-starter-gateway | 网关核心 | 间接拉 webflux，无需单独引 |
| mini-mall-common-core | 复用 Result/异常类 | 纯 POJO，WebFlux 也能用 |
| jjwt-api | JWT 编程接口 | 编译期要用 |
| jjwt-impl / jjwt-jackson | JWT 实现 | scope=runtime 只跑时挂载 |

没引的（很重要）：

✗ spring-boot-starter-web —— 引了启动报错：MVC 与 Gateway 不兼容

✗ mybatis-plus / mysql —— 网关不碰数据库

✗ lombok —— 网关代码极少，不引也无所谓

### 21.7.3 MiniMallGatewayApplication.java 启动类完整代码 <a id="sec154"></a>

文件：mini-mall-gateway/src/main/java/com/minimall/gateway/MiniMallGatewayApplication.java

```

package com.minimall.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * mini-mall-gateway 网关启动类
 *
 * 关键点：
 *   ① @SpringBootApplication —— 标准 Boot 启动注解
 *
 *   ② @ComponentScan("com.minimall") —— 跟 user/product 一样
 *      扫描扩到上层，能扫到 common-core 的 GlobalExceptionHandler 等 Bean
 *      ⚠️ 但要注意：common-core 里的 @RestControllerAdvice 是给 MVC 用的，
 *      在 WebFlux 网关里【不会生效】（WebFlux 用另一套异常处理机制）
 *      D3 步骤会自己写一个网关版的全局异常返回
 *
 *   ③ 这里【没有】@EnableFeignClients、@MapperScan
 *      网关不直接连数据库、不主动调下游服务（用路由转发，不用 Feign）
 *
 *   ④ 启动后日志会看到 "Netty started on port 9080"
 *      ↑ 注意是 Netty，不是 Tomcat。这是 WebFlux 跟 MVC 最显眼的区别
 */
@SpringBootApplication
@ComponentScan("com.minimall")
public class MiniMallGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniMallGatewayApplication.class, args);
        System.out.println("=========== mini-mall-gateway 启动成功 ===========");
    }
}
```
行级讲解：

• Line 1: 包名 com.minimall.gateway —— 跟 com.minimall.user / com.minimall.product 平级

• Line 7-19: 类注释 —— 重点是【没加】@EnableFeignClients/@MapperScan 的原因

• Line 20: @SpringBootApplication 三合一 = @Configuration + @EnableAutoConfiguration + @ComponentScan

• Line 21: @ComponentScan("com.minimall") 覆盖默认范围，扫整个 com.minimall 树

• Line 25: SpringApplication.run() 启动入口 —— 内部启 Netty、扫包、装配 Bean

• Line 26: println 打印自定义日志，启动成功的可视化标志

### 21.7.4 application.yml D1 骨架版 <a id="sec155"></a>

文件：mini-mall-gateway/src/main/resources/application.yml

```

server:
  port: 9080   # ⭐ Docker Desktop 占了 9000/8080，先用 9080

spring:
  application:
    name: mini-mall-gateway

  cloud:
    gateway:
      routes: []   # D1 阶段空数组，D2 步骤再填

logging:
  level:
    org.springframework.cloud.gateway: debug
    reactor.netty.http.client: info
```
讲解：

• server.port = 9080：选 9080 因为 Docker Desktop 占了 9000/8080

• spring.application.name：服务名，将来 Nacos 注册用

• routes: [] 是空数组占位，D2 步骤会填入具体路由

• logging 开 gateway 包的 debug 日志，启动时能看到匹配的路由

## 21.8 D2：配静态路由（含 YAML 缩进死规则） <a id="sec156"></a>

### 21.8.1 D2 完整 yml <a id="sec157"></a>

```

server:
  port: 9080

spring:
  application:
    name: mini-mall-gateway

  cloud:
    gateway:
      routes:
        - id: user-route                       # 第 1 条路由
          uri: http://localhost:9001
          predicates:
            - Path=/user/**

        - id: product-route                    # 第 2 条路由
          uri: http://localhost:9002
          predicates:
            - Path=/product/**

logging:
  level:
    org.springframework.cloud.gateway: debug
    reactor.netty.http.client: info
```

### 21.8.2 字段含义详解 <a id="sec158"></a>

| 字段 | 本例值 | 含义 |
|---|---|---|
| id | user-route | 路由唯一标识（日志/调试看它） |
| uri | http://localhost:9001 | path 匹配后转发的目标地址（硬编码） |
| predicates | Path=/user/** | 匹配条件，** 通配多级 path |

当前是硬编码 IP:端口。

接 Nacos 后会改成：uri: lb://mini-mall-user（lb = load balance）

由 LoadBalancer 自动从注册中心找实例，不用手写 IP 端口。

### 21.8.3 一次完整请求的旅程 <a id="sec159"></a>

```

浏览器 ──→ GET http://localhost:9080/user/1
   │
   ↓
Gateway (Netty :9080) 收到
   │
   ↓ 遍历 routes 数组：
   │   ① path /user/1 匹配 user-route 的 Path=/user/**？ ✓
   │   ② 取 uri = http://localhost:9001
   │   ③ 改写为 http://localhost:9001/user/1
   ↓
HTTP 转发到 user-service (Tomcat :9001)
   │
   ↓ UserController.getById(1)
   │   userMapper.selectById(1)
   ↓
返回 alice 的 JSON
   │
   ↑ 沿原路返回
Gateway 把响应原样回传
   │
   ↑
浏览器拿到 JSON
```

### 21.8.4 YAML 缩进 4 大死规则（用户连踩 4 坑实录） <a id="sec160"></a>

规则 1：必须用英文冒号 + 必须用空格

```

✗ product-route：/product/**      ← 中文「：」YAML 不认
✓ product-route: /product/**      ← 英文 : + 空格

✗ Tab 缩进                         ← 不少 yaml 解析器不支持
✓ 全部用空格                       ← IDE 一般会自动转
```
规则 2：子项缩进必须比父 key 更深

```

spring:           ← 0 空格
  cloud:          ← 2 空格（比 spring 深 ✓）
    gateway:      ← 4 空格（比 cloud 深 ✓）
      routes:     ← 6 空格（比 gateway 深 ✓）

key 后面接子项，子项必须比 key 更靠右。
```
规则 3：缩进过浅 = 跑错爹

```

✗ 错误写法：
spring:           ← 0
  cloud:          ← 2
    gateway:      ← 4
  routes:         ← 2  ⚠️ 退回 2 空格 = 跟 cloud 同级 = 是 spring 的儿子
    - id: user-route

实际解析结果：
   spring.routes = [...]              ← routes 跑到 spring 下了
   spring.cloud.gateway = null        ← Gateway 找不到 routes

修正：routes: 必须 ≥ 6 空格才是 gateway 的儿子。
```
规则 4：缩进过深 = 整段被吞

```

✗ 错误写法：
server:
  port: 9000

  spring:         ← 2 空格 ⚠️ 变成 server 的儿子了
    cloud:
      gateway:
        routes:
          - ...

实际解析：
   server.spring.cloud.gateway.routes = [...]   ← 整个 spring 树掉进 server 里

修正：spring: 必须是顶级 key（0 空格），跟 server、logging 平起平坐。
```
缩进尺（背下来）：

```

0123456789012
spring:              ← 0 空格
  application:       ← 2 空格
    name: ...        ← 4 空格
  cloud:             ← 2 空格
    gateway:         ← 4 空格
      routes:        ← 6 空格
        - id: ...    ← 8 空格（- 在 8 位，id 在 10 位）
          uri: ...   ← 10 空格（对齐到 id）
          predicates:← 10 空格
            - Path=…← 12 空格
```

## 21.9 D 阶段三大踩坑实录 <a id="sec161"></a>

### 坑 ①：Docker Desktop 占了 9000 / 8080 端口 <a id="sec162"></a>

```

现象：gateway 启动失败 / 访问 9000 返 401（Docker 内部 API）

排查命令：
  netstat -ano | findstr LISTEN | findstr :9000
  ↓
  TCP 0.0.0.0:9000 ... LISTENING  PID 9256
  ↓
  tasklist /FI "PID eq 9256"
  ↓
  com.docker.backend.exe          ← 罪魁祸首！

解决：换端口 9080（保持 90xx 系列：user 9001 / product 9002 / gateway 9080）。

教训：
  • Windows 上 Docker Desktop 会占用 9000、8080、5000、6000 等常用端口
  • 选业务端口前先 netstat 扫一下
```

### 坑 ②：common-core 偷偷绑定了 Servlet（最大坑） <a id="sec163"></a>

现象：gateway 启动报

```

Error creating bean with name 'globalExceptionHandler'
  ↓
Failed to introspect Class [com.minimall.common.core.exception.GlobalExceptionHandler]
  ↓
NoClassDefFoundError: jakarta/servlet/http/HttpServletRequest
```
根因链：

• common-core 的 GlobalExceptionHandler 用了 HttpServletRequest 打 URI 日志

• 这个类来自 jakarta.servlet 包（Servlet API）

• user / product 用 MVC 自带 Servlet，找得到

• gateway 用 WebFlux 不引 Servlet，classpath 没这个类

• Spring 反射这个类时崩溃 → 启动失败

修复对比：

```

─── 修复前（绑 Servlet）───
import jakarta.servlet.http.HttpServletRequest;     ⚠️ 邪恶 import

@ExceptionHandler(BusinessException.class)
public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
    log.warn("[业务异常] 请求路径={}, 错误码={}, 消息={}",
            request.getRequestURI(), e.getCode(), e.getMessage());
    return Result.error(e.getCode(), e.getMessage());
}

─── 修复后（纯 POJO）───
// ⚠️ 不能 import jakarta.servlet.http.HttpServletRequest
// common-core 必须是【纯通用】，绑定 Servlet 会让 WebFlux 网关启动崩溃

@ExceptionHandler(BusinessException.class)
public Result<Void> handleBusinessException(BusinessException e) {
    log.warn("[业务异常] 错误码={}, 消息={}", e.getCode(), e.getMessage());
    return Result.error(e.getCode(), e.getMessage());
}
```
教训（核心）：

• common-* 模块必须保持【纯通用】，绝不能 import 任何 web 框架特定的类

• 想打 URI 日志？用 MDC、Filter、或各业务模块自己扩展，不要污染 common

• common 越纯，复用范围越广

### 坑 ③：fat jar 缓存了旧 common-core <a id="sec164"></a>

```

现象：改了 common-core 源码、重打 gateway，但启动还报同样错。

原因：
  • mvn -pl mini-mall-gateway -am package 用 -am（also make）连同依赖编
  • 但增量编译有时不会把改过的 common-core 重新打入 gateway 的 fat jar
  • Java 用的是 fat jar 嵌进去的 class，不是 .m2 里的

强制重打的命令（推荐保险做法）：

  cd mini-mall-cloud

  # 1) 先把 common-core 重新装到本地仓库
  mvn -pl mini-mall-common/mini-mall-common-core clean install -DskipTests

  # 2) 再 clean package gateway（一定加 clean）
  mvn -pl mini-mall-gateway clean package -DskipTests

教训：
  • 改了下游模块的源码，一定先 clean install 它本身
  • 上游模块用 clean package，不要省 clean
  • mvn -am 不可信，关键时刻手动两步走
```

## 21.10 D2 验证：三 jar 联调 <a id="sec165"></a>

### 21.10.1 启动命令 <a id="sec166"></a>

```

# 🪟 窗口 ①：user-service（9001）
& "D:\jdk-21.0.11\bin\java.exe" -jar `
  "C:\Users\liyuq\OneDrive\桌面\Java学习代码\mini-mall-cloud\mini-mall-user\target\mini-mall-user-0.0.1-SNAPSHOT.jar"
期望日志：Tomcat started on port 9001

# 🪟 窗口 ②：product-service（9002）
& "D:\jdk-21.0.11\bin\java.exe" -jar `
  "C:\Users\liyuq\OneDrive\桌面\Java学习代码\mini-mall-cloud\mini-mall-product\target\mini-mall-product-0.0.1-SNAPSHOT.jar"
期望日志：Tomcat started on port 9002

# 🪟 窗口 ③：gateway（9080）
& "D:\jdk-21.0.11\bin\java.exe" -jar `
  "C:\Users\liyuq\OneDrive\桌面\Java学习代码\mini-mall-cloud\mini-mall-gateway\target\mini-mall-gateway-0.0.1-SNAPSHOT.jar"
期望日志：Netty started on port 9080     ← 是 Netty 不是 Tomcat！
```

### 21.10.2 curl 测试 <a id="sec167"></a>

```

# 测试 1：走网关查用户（之前是 9001，现在走 9080）
curl http://localhost:9080/user/1
期望：{"code":200,"data":{"id":1,"username":"alice",...}}

# 测试 2：走网关查商品（之前是 9002，现在走 9080）
curl http://localhost:9080/product/1
期望：{"code":200,"data":{"id":1,"name":"小米 14 Pro",...}}

# 测试 3：访问没配的路径
curl http://localhost:9080/order/1
期望：{"code":500,"message":"系统繁忙，请稍后再试"}
  ↑ 不是教科书的 404，因为 GlobalExceptionHandler 兜底了
```

### 21.10.3 实测结果（2026-06-19 00:53） <a id="sec168"></a>

```

=== ① /user/1 ===
{"code":200,"message":"操作成功","data":{"id":1,"username":"alice",
"createTime":"2026-05-19T01:18:35",...}}

=== ② /product/1 ===
{"code":200,"message":"操作成功","data":{"id":1,"name":"小米 14 Pro",
"price":4999.00,"stock":201,...}}

=== ③ /order/1 ===
{"code":500,"message":"系统繁忙，请稍后再试"}

✅ 全部通过
```

### 21.10.4 数据链路证明图 <a id="sec169"></a>

```

浏览器/curl
  │
  └─ http://localhost:9080/user/1            ← 走网关 9080
       │
       └─ Spring Cloud Gateway 路由匹配
          │ predicates: Path=/user/**  ✓ 匹配
          │ uri: http://localhost:9001
          ↓
       Netty HttpClient 转发到
          │
          └─ http://localhost:9001/user/1     ← 网关无感转发
               │
               └─ user-service (MVC + Tomcat)
                  │ UserController.getById(1)
                  │ userMapper.selectById(1)
                  ↓
                  alice 数据
       ↑
       Gateway 把响应原样返回
```

## 21.11 接下来：D3 JWT 鉴权预告 <a id="sec170"></a>

```

现在（D2 完）：
  curl http://localhost:9080/user/1
  → 直接返回 alice（裸奔，没鉴权）⚠️

D3 完成后：
  curl http://localhost:9080/user/1
  → 401 未授权（没带 token）

  curl http://localhost:9080/user/login
  → 200 OK（白名单放行）

  # 登录拿 token
  curl -X POST http://localhost:9080/user/login \
       -d '{"username":"alice","password":"123456"}'
  → {"data":"eyJhbGc..."}

  # 带 token 访问
  curl http://localhost:9080/user/1 -H "Authorization: Bearer eyJhbGc..."
  → 200 返用户
  下游 user-service 还能从 request.getHeader("X-User-Id") 拿到 1
```

| D3 知识点 | 做什么 |
|---|---|
| GlobalFilter 接口 | filter() 方法对每个请求做处理 |
| 白名单机制 | 登录/注册等 path 跳过 JWT 校验 |
| JwtUtil 跨模块复用 | 从 mini-mall-user 搬到 common-security（未来） |
| ServerWebExchange 改写请求 | WebFlux 版的请求改写（不是 HttpServletRequest） |
| 透传 X-User-Id 到下游 | 把 token 解出来的 userId 塞 HTTP header |

## 附录：D 阶段常用命令速查 <a id="sec171"></a>

| 场景 | 命令 |
|---|---|
| 扫端口看占用 | netstat -ano | findstr LISTEN | findstr :9080 |
| 看进程 | tasklist /FI "PID eq <PID>" |
| 杀进程 | taskkill /F /PID <PID> |
| 重打 common-core | mvn -pl mini-mall-common/mini-mall-common-core clean install -DskipTests |
| 重打 gateway | mvn -pl mini-mall-gateway clean package -DskipTests |
| 启 jar | & "D:\jdk-21.0.11\bin\java.exe" -jar <jar 路径> |
| 测路由 | curl http://localhost:9080/user/1 |

### 21.12  D3 完整实现: GlobalFilter JWT 鉴权 + X-User-Id 透传 <a id="sec172"></a>

```
D 笔记原文 21.11 只给了 D3 预告。本节是实际代码 + 行级讲解, 跟项目 100% 对应。
```

#### 21.12.1  D3 文件清单

```
mini-mall-cloud/mini-mall-gateway/
└── src/main/
    ├── java/com/minimall/gateway/
    │   ├── MiniMallGatewayApplication.java   ← D1 已有, 无改动
    │   ├── filter/                            ← ➕ 整个包新建
    │   │   └── AuthGlobalFilter.java         ← ➕ D3 核心
    │   └── util/                              ← ➕ 整个包新建
    │       └── JwtUtil.java                  ← ➕ 从 user-service 简化复制
    └── resources/
        └── application.yml                    ← ✏️ 加 jwt.secret 配置
```

#### 21.12.2  application.yml 加 jwt.secret

```
📁 文件: mini-mall-gateway/src/main/resources/application.yml (末尾追加)
jwt:
  secret: my-mini-mall-super-secret-key-1234567890
⚠️ 这个 secret 必须跟 user-service 的 yml 完全一致, 否则解 token 会签名验证失败。
为什么 gateway 也要配 secret? 因为整个微服务集群【唯一一处解 token】就是网关, user-service 不再解 token, 它信任 gateway 透传过来的 X-User-Id。
```

#### 21.12.3  JwtUtil.java 完整代码

```
📁 文件: mini-mall-gateway/src/main/java/com/minimall/gateway/util/JwtUtil.java
package com.minimall.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT 工具类 (gateway 版)
 *
 * ⚠️ 这是从 mini-mall-user 模块直接复制过来的简化版
 *
 * 跟 user 版的区别:
 *   ① 包名不同: com.minimall.gateway.util (vs com.minimall.user.util)
 *   ② 删掉了 generateToken 方法
 *      网关只解析, 不签发(签发还是 user-service login 时干)
 *
 * 跟单体版的区别:
 *   只保留 parseToken 部分, 不签发
 *
 * 将来理想的归处: 抽 mini-mall-common-security 模块,
 *   user 和 gateway 共用一份。现阶段先复制让 D3 跑通。
 */
@Component
public class JwtUtil {

    // 从 gateway/application.yml 读密钥
    // ⚠️ 必须跟 user 服务的 jwt.secret 完全一致! 否则解 token 必失败
    @Value("${jwt.secret}")
    private String secret;

    /**
     * 解析 token, 返回 Claims (含 userId / username 等)
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())     // 验签
                .build()
                .parseSignedClaims(token)         // 解析
                .getPayload();
    }

    /** 从 token 提取 userId (最常用) */
    public Long getUserIdFromToken(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    /** 从 token 提取 username */
    public String getUsernameFromToken(String token) {
        return parseToken(token).get("username", String.class);
    }

    /** 生成 SecretKey (HMAC 算法对称密钥) */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
代码解释逐块:
  ① @Component → 让 Spring 在启动时实例化这个类, 放进容器
  ② @Value("${jwt.secret}") → Spring 从 application.yml 读取 jwt.secret 字段值, 在 Bean 实例化后注入到 secret 字段
  ③ parseToken: Jwts.parser() 链式调用三步: 设密钥 → build → parseSignedClaims, 内部会验签(密钥错抛 SignatureException)+ 验过期(过期抛 ExpiredJwtException)
  ④ getUserIdFromToken: 拿到 Claims 后用 .get(key, type) 取 userId, JJWT 自动把 JSON 数字转成 Long
  ⑤ getSigningKey: HMAC-SHA256 对称加密的 SecretKey 生成方法, 库的固定写法
```

#### 21.12.4  AuthGlobalFilter.java 完整代码(D3 核心)

```
📁 文件: mini-mall-gateway/src/main/java/com/minimall/gateway/filter/AuthGlobalFilter.java
package com.minimall.gateway.filter;

import com.minimall.gateway.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 全局鉴权过滤器 (D3 核心)
 *
 * 工作原理:
 *   ① 每个进入网关的请求, 都会经过 filter() 方法
 *   ② 白名单内的(登录/注册)直接放行
 *   ③ 其他请求: 从 Authorization header 拿 token, 解析校验
 *   ④ 成功 → 把 userId 塞进 X-User-Id header 透传给下游
 *   ⑤ 失败 → 返 401 直接拦下
 *
 * 跟单体 JwtInterceptor 的 90% 一样, 10% 关键不同:
 *   - 返 Mono<Void>(响应式), 不是 boolean
 *   - 用 ServerWebExchange 取/改请求, 不是 HttpServletRequest
 *   - 透传方案: HTTP header(X-User-Id), 不是 ThreadLocal
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtUtil jwtUtil;

    /** 白名单: 这些 path 不校验 token, 直接放行 */
    private static final List<String> WHITE_LIST = List.of(
            "/user/login",
            "/user/register"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        // ① 拿到当前请求对象
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // ② 在白名单 → 不校验, 直接放行
        boolean inWhiteList = WHITE_LIST.stream().anyMatch(path::startsWith);
        if (inWhiteList) {
            return chain.filter(exchange);
        }

        // ③ 从 header 取 Authorization
        String token = request.getHeaders().getFirst("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        token = token.substring(7);   // 去掉 "Bearer " 前缀 = 7 个字符

        // ④ 解析 token, 拿 userId
        Long userId;
        try {
            userId = jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            return unauthorized(exchange);  // 签名错 / 过期 / 任何异常都 401
        }

        // ⑤ 把 userId 塞进 X-User-Id header 透传给下游
        ServerHttpRequest mutated = request.mutate()
                .header("X-User-Id", String.valueOf(userId))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /** 返 401 未授权 (WebFlux 风格的"中断响应") */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    /** 越小越先执行, -100 确保鉴权先于路由转发 */
    @Override
    public int getOrder() {
        return -100;
    }
}
```

#### 21.12.5  AuthGlobalFilter 行级讲解

```
implements GlobalFilter, Ordered (类签名)
  GlobalFilter: Gateway 全局过滤器接口, 对所有路由生效(对比 GatewayFilter 只对单条路由)
  Ordered: 控制多个 Filter 的执行顺序, 提供 getOrder() 方法
Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
  Mono<Void>: Reactor 响应式类型, 表示"将来完成但无返回值"(异步)
  ServerWebExchange: WebFlux 版的 request+response 合体对象
  GatewayFilterChain: 过滤器链, chain.filter(exchange) = 把请求传给下一个 Filter
request.getURI().getPath()
  URI 五段(scheme/authority/path/query/fragment)取 path 部分, 比如 http://localhost:9080/user/me?x=1 → /user/me
WHITE_LIST.stream().anyMatch(path::startsWith)
  方法引用 path::startsWith 等价于 s -> path.startsWith(s)
  anyMatch 一旦匹配立刻返回 true, 不会遍历完
request.getHeaders().getFirst("Authorization")
  getFirst 因为 HTTP header 同一个 key 可以多值, 一般取第一个
  没带 Authorization → 返 null
token.substring(7)
  Bearer + 空格 = 7 个字符, 去掉前缀拿到纯 token
request.mutate().header("X-User-Id", ...).build()
  ServerHttpRequest 是【不可变对象】(像 String), 不能直接改, 只能通过 Builder 复制一份带新 header 的对象
  mutate() = 拿到一个填好旧值的 Builder, header(k,v) = 改一个 header, build() = 产出新对象
exchange.mutate().request(mutated).build()
  同理, exchange 也是不可变, 用 mutate 拿到新的 exchange(带新 request)
  最后传给 chain.filter() 让请求继续往下走
return -100 (getOrder)
  Gateway 内部很多默认 Filter, 鉴权要在所有路由处理【之前】跑, -100 确保最先执行
```

#### 21.12.6  X-User-Id 这个 header 的意义

```
HTTP header 命名约定:
  X- 前缀 = 自定义 header (历史习惯, RFC 6648 后官方建议不加 X- 但实际仍流行)
  关键: 这个 header 名跟下游必须保持一致, 否则下游 @RequestHeader("X-User-Id") 取不到
| 单体 mini-mall              | 微服务 mini-mall-cloud         |
|----------------------------|--------------------------------|
| Interceptor 解 token        | Gateway 解 token                |
| BaseContext (ThreadLocal)   | X-User-Id (HTTP header)         |
| 同进程同线程取               | 跨进程跨线程取                  |
| Controller: getCurrentId()  | Controller: @RequestHeader      |
```

### 21.13  D4 三 jar 联调实录 + 3 个新坑 <a id="sec173"></a>

```
D 阶段终极验证: 启 user + product + gateway 三个 jar, 用 curl 跑 4 个场景, 证明 X-User-Id 真的跨进程透传。
```

#### 21.13.1  user-service 加 /user/me 验证接口

```
📁 文件: mini-mall-user/src/main/java/com/minimall/user/controller/UserController.java (在 getById 前面新增方法)
/**
 * ⭐ D4 验证接口: 检验 X-User-Id 是否真的从网关透传过来
 *
 * 端到端链路:
 *   ① 前端带 Authorization: Bearer xxx 请求 http://localhost:9080/user/me
 *   ② 网关 AuthGlobalFilter 解析 token, 把 userId 塞进 X-User-Id header
 *   ③ 网关转发到 http://localhost:9001/user/me
 *   ④ 这里收到 X-User-Id, 证明跨进程身份透传成功
 *
 * 这是单体 BaseContext (ThreadLocal) 在微服务下的等价方案:
 *   单体:   Interceptor 解 token → 放 BaseContext → Controller 取
 *   微服务: Gateway 解 token → 放 X-User-Id header → 下游 Controller 取
 */
@GetMapping("/me")
public Result<Map<String, Object>> me(
        // @RequestHeader("X-User-Id"): 直接把 header 注入成方法参数
        // Spring 自动把 String 转 Long
        @RequestHeader("X-User-Id") Long userId
) {
    System.out.println("[user/me] 收到的 X-User-Id = " + userId);

    Map<String, Object> data = new HashMap<>();
    data.put("userId", userId);
    data.put("source", "X-User-Id header (透传自 gateway)");
    return Result.success(data);
}
@RequestHeader 注解的两种写法对比:
写法 A (笨办法): 从原生 request 取
  @GetMapping("/me")
  public String me(HttpServletRequest request) {
      String userId = request.getHeader("X-User-Id");
      return "userId=" + userId;
  }

写法 B (推荐): @RequestHeader 直接注入
  @GetMapping("/me")
  public String me(@RequestHeader("X-User-Id") String userId) {
      return "userId=" + userId;
  }

B 的好处:
  - 一行省一句话
  - Spring 自动处理类型转换(写 Long userId 就自动 String→Long)
  - 写 required=false 还能容忍 header 缺失
```

#### 21.13.2  jar 打包 + 启动顺序

```
# Step 1: 重新打 user-service 的 jar (改了 UserController)
cd mini-mall-cloud
mvn -pl mini-mall-user -am clean package -DskipTests

# 命令拆解
#   -pl mini-mall-user    只打这一个模块 (project list)
#   -am                   连它依赖的 common-core 一起打 (also make)
#   clean                 先清旧 target, 关键!
#   -DskipTests           跳单元测试

# Step 2: 按顺序启 3 个 jar (3 个独立窗口)
# 窗口①: user-service (9001)
& "D:\jdk-21.0.11\bin\java.exe" -jar \
  "C:\Users\liyuq\OneDrive\桌面\Java学习代码\mini-mall-cloud\mini-mall-user\target\mini-mall-user-0.0.1-SNAPSHOT.jar"
# 期望日志: Tomcat started on port 9001

# 窗口②: product-service (9002)
& "D:\jdk-21.0.11\bin\java.exe" -jar \
  "C:\Users\liyuq\OneDrive\桌面\Java学习代码\mini-mall-cloud\mini-mall-product\target\mini-mall-product-0.0.1-SNAPSHOT.jar"
# 期望日志: Tomcat started on port 9002

# 窗口③: gateway (9080) — 必须最后启
& "D:\jdk-21.0.11\bin\java.exe" -jar \
  "C:\Users\liyuq\OneDrive\桌面\Java学习代码\mini-mall-cloud\mini-mall-gateway\target\mini-mall-gateway-0.0.1-SNAPSHOT.jar"
# 期望日志: Netty started on port 9080  ← 是 Netty 不是 Tomcat!
```

#### 21.13.3  4 个 curl 测试场景 + 实测结果

```
(实测时间: 2026-06-19 12:08, 验证全部通过 ✓)
测试 1: 无 token 访问受保护接口 (期望 401)
curl -i http://localhost:9080/user/me

实际响应:
HTTP/1.1 401 Unauthorized
content-length: 0

✓ AuthGlobalFilter 拦下了请求
测试 2: 白名单登录接口 (期望 200 + token)
# PowerShell 坑: 单引号字符串传给 curl.exe 时引号会被吞掉
# 修复: 把 JSON body 写入文件, 用 --data-binary @文件
$body = '{"username":"alice","password":"123456"}'
Set-Content -Path "$env:TEMP\login.json" -Value $body -Encoding ascii -NoNewline

curl.exe -s -X POST -H "Content-Type: application/json" \
  --data-binary "@$env:TEMP\login.json" \
  http://localhost:9080/user/login

实际响应:
{"code":200,"message":"操作成功","data":"eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjEs
InVzZXJuYW1lIjoiYWxpY2UiLCJpYXQiOjE3ODE4ODU1NTUsImV4cCI6MTc4MjQ5MDM1NX0.
FvGrIW3yoMSXyeUxibrFSithqHPlyO0If9VzGvamCtI"}

✓ 白名单放行 + user-service 签发了 token
测试 3: 带 token 访问受保护接口 (期望 200 + userId=1)
$token = "eyJhbGciOi...FvGrIW3y..."
curl.exe -s -H "Authorization: Bearer $token" http://localhost:9080/user/me

实际响应:
{"code":200,"message":"操作成功","data":{
  "source":"X-User-Id header (透传自 gateway)",
  "userId":1
}}

✓ 透传成功! userId 真的从 token 解出来 → 进 X-User-Id header → 到达 user-service
测试 4: 日志见证 (从 user-service 控制台看 System.out)
查 user-svc.log:
  [user/me] 收到的 X-User-Id = 1

✓ Controller 里 println 打印了正确的 userId
  证明 @RequestHeader("X-User-Id") 拿到了真实值
```

#### 21.13.4  D4 完整数据流图

```
浏览器/curl
  │ Authorization: Bearer eyJhbGc...
  ↓
Gateway (Netty :9080)
  │
  ├─ AuthGlobalFilter.filter()
  │     ① path = "/user/me"
  │     ② 不在白名单
  │     ③ 取 token, 去掉 Bearer 前缀
  │     ④ jwtUtil.getUserIdFromToken(token) → userId=1
  │     ⑤ request.mutate().header("X-User-Id","1").build()
  │
  ├─ 路由匹配: Path=/user/** → user-route
  ↓
Netty HttpClient 转发
  │ GET /user/me
  │ Authorization: Bearer eyJhbGc...
  │ X-User-Id: 1                              ← ⭐ 网关新加的 header
  ↓
user-service (Tomcat :9001)
  │
  ├─ DispatcherServlet 路由 → UserController.me()
  │
  ├─ @RequestHeader("X-User-Id") Long userId = 1
  │
  ├─ System.out.println("[user/me] 收到的 X-User-Id = 1")
  │
  └─ Result.success({userId:1, source:"..."})
       ↓ JSON
浏览器拿到 {"code":200,"data":{"userId":1,...}}
```

#### 21.13.5  D4 三大新坑(D 笔记没有)

```
坑 ①: Windows PATH 里 Java 8 占先, JDK 21 被遮蔽
现象:
  java -version  → openjdk 1.8.0
  java -jar xxx.jar (Boot 3.3.5 需要 17+) → UnsupportedClassVersionError

排查:
  (Get-Command java).Source
  → C:\Program Files (x86)\Common Files\Oracle\Java\java8path\java.exe
                                            ↑ Oracle 安装 Java 8 时塞进 PATH 的快捷方式

  $env:JAVA_HOME
  → D:\jdk-21.0.11   ← JDK 21 装在这, 但 PATH 没用上

修复:
  用 JAVA_HOME 全路径启动, 不依赖 PATH:
  & "D:\jdk-21.0.11\bin\java.exe" -jar xxx.jar

教训:
  Windows 上 Oracle Java 8 安装时会在 PATH 前面塞一个 javapath 链接,
  即使你重装 Java 21 也无法覆盖。生产环境一律用全路径或者改 PATH 优先级。
坑 ②: PowerShell 单引号 + curl.exe 传 JSON 时引号被吞
现象:
  $body = '{"username":"alice","password":"123456"}'
  curl.exe -X POST -d $body http://localhost:9080/user/login
  → 500 系统繁忙
  → 后台日志: JsonParseException: Unexpected character ('u' ...)

原因:
  PowerShell 把字符串传给原生 exe 时, 内部双引号会被去掉。
  curl.exe 实际收到的 body 不再是合法 JSON。

修复 A (推荐): 把 body 写入文件, 用 -d @文件
  Set-Content -Path "$env:TEMP\login.json" -Value $body -Encoding ascii -NoNewline
  curl.exe -X POST --data-binary "@$env:TEMP\login.json" ...

修复 B: 用 Invoke-RestMethod
  Invoke-RestMethod -Uri "http://..." -Method Post -ContentType "application/json" -Body $body

教训: PowerShell + curl.exe + JSON 是经典坑, 永远走文件路线最稳。
坑 ③: Git Bash 里没有 java 命令 (exit 127)
现象:
  在 Git Bash 里执行 java -jar xxx.jar
  → bash: java: command not found
  → 后台任务 exit code 127

原因:
  Git Bash 的 PATH 不继承 Windows 系统 PATH 里的 java 路径,
  特别是 PATH 里 java 又在 Program Files (含空格) 这类目录里, Git Bash 不认。

修复: 用 PowerShell 跑 jar
  & "D:\jdk-21.0.11\bin\java.exe" -jar xxx.jar

教训:
  Windows 上跑 Java 优先用 PowerShell 或 cmd, 不要图省事用 Git Bash。
  Git Bash 适合 git/grep/sed, 不适合启 Java/Node 应用。
```
# Spring Cloud Gateway 底层完整链路：HTTP 请求从网卡进入网关全过程
Gateway 底层基于 **Spring WebFlux + Reactor-Netty**，完全响应式，和 SpringMVC（Tomcat 同步阻塞）完全两套体系。
整体链路分层：
`网卡TCP连接 → ReactorNetty 服务端 → HttpServer 解码HTTP报文 → WebFlux HttpHandler → Gateway 全局过滤器链 → 路由匹配 → 转发到下游服务`

#、核心底层组件总览
1. **Reactor Netty**：底层网络IO，替代Tomcat，异步非阻塞TCP服务
2. `HttpServer`：Netty封装，监听端口8080，接收TCP数据流
3. `ReactorHttpHandlerAdapter`：把Netty请求转成WebFlux标准 `ServerHttpRequest`
4. `HttpWebHandlerAdapter`：WebFlux顶层入口，持有 `WebHandler`
5. `DispatcherHandler`：WebFlux核心调度器，分发请求
6. `RoutePredicateHandlerMapping`：Gateway专属，匹配路由
7. `FilteringWebHandler`：封装网关全局过滤器 + 路由局部过滤器链
8. `GatewayFilterChain`：你写的 `GlobalFilter` 就在这里执行

---
#### 21.13.6
、完整流程分步拆解（HTTP进来全链路源码走向）
. 启动阶段：Netty服务初始化，监听端口
Gateway 启动类 `@SpringBootApplication` 加载自动配置：
`GatewayAutoConfiguration` → 自动创建 `NettyReactiveWebServerFactory`
```java
// 自动配置创建Netty HTTP服务
public NettyReactiveWebServerFactory reactiveWebServerFactory() {
    NettyReactiveWebServerFactory factory = new NettyReactiveWebServerFactory();
    // 绑定配置文件server.port
    factory.setPort(this.serverProperties.getPort());
    return factory;
}
```
启动时会创建 Reactor Netty 的 `HttpServer`，绑定 0.0.0.0:8080，开启TCP异步监听。

. 浏览器发起HTTP请求：TCP数据流到达网卡
1. 客户端建立TCP连接，发送原始HTTP二进制报文（请求行、Header、Body）
2. Reactor-Netty 的 `Channel` 异步接收ByteBuf字节流，**无阻塞IO**
3. Netty内置HTTP解码器 `HttpServerCodec` 把二进制字节解析成标准HTTP对象：
   - 请求行：`GET /product/list HTTP/1.1`
   - 请求头：`Authorization: Bearer xxx`、`Host`、`Content-Type`
   - 请求体（POST参数）

> 关键点：这里还是Netty原生对象，不是Spring的ServerHttpRequest

ReactorHttpHandlerAdapter：Netty对象 → WebFlux标准请求
适配器转换层，桥接Netty和Spring WebFlux：
```java
public Mono<Void> apply(HttpServerRequest nettyRequest, HttpServerResponse nettyResponse) {
    // 1. Netty原生请求 → 封装成Spring WebFlux ServerHttpRequest
    ServerHttpRequest springReq = new ReactorServerHttpRequest(nettyRequest, this.bufferFactory);
    ServerHttpResponse springResp = new ReactorServerHttpResponse(nettyResponse, this.bufferFactory);
    // 2. 交给顶层WebHandler处理
    return this.httpHandler.handle(springReq, springResp);
}
```
转换后你在过滤器 `exchange.getRequest()` 拿到的就是 `ServerHttpRequest`。

. HttpWebHandlerAdapter：统一入口，包装ServerWebExchange
WebFlux顶层适配器，把 `ServerHttpRequest + ServerHttpResponse` 封装成**上下文对象 ServerWebExchange**
```java
@Override
public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {
    // 封装交换上下文，贯穿整个网关所有过滤器
    ServerWebExchange exchange = createExchange(request, response);
    // 进入核心调度器 DispatcherHandler
    return getDispatcherHandler().handle(exchange);
}
```
你代码里的 `ServerWebExchange` 就是在这里创建，全程传递。
. DispatcherHandler：WebFlux分发核心（类似SpringMVC DispatcherServlet）
三大职责：
1. 遍历所有 `HandlerMapping` 找当前请求对应的处理器
2. 找到后获取 `HandlerAdapter` 执行处理器
3. 处理返回结果写入响应

Gateway 提供专属 `RoutePredicateHandlerMapping`，优先级最高，优先匹配网关路由。
```java
@Override
public Mono<Void> handle(ServerWebExchange exchange) {
    // 1. 查找匹配当前请求的路由处理器
    return this.handlerMappings.flatMap(mapping -> mapping.getHandler(exchange))
            .next()
            .flatMap(handler -> {
                // 2. 获取适配器执行路由逻辑
                return this.handlerAdapters.flatMap(adapter -> adapter.supports(handler)
                        ? adapter.handle(exchange, handler) : Mono.empty()).next();
            });
}
```

. RoutePredicateHandlerMapping：匹配yml里配置的路由
读取 `application.yml` 中 `spring.cloud.gateway.routes` 所有路由规则：
- predicates Path=/user/**
- uri lb://mini-mall-user

遍历所有Route，用断言匹配当前请求path，匹配成功返回 `Route` 对象，包装成 `GatewayWebHandler`。

. FilteringWebHandler：组装过滤器链（你写的AuthGlobalFilter在这里执行）
 过滤器分两类
1. **GlobalFilter 全局过滤器**：你写的 `AuthGlobalFilter implements GlobalFilter, Ordered`，所有请求都会执行
2. **GatewayFilter 路由局部过滤器**：仅当前路由生效，如限流、重写路径

 源码核心逻辑
```java
public Mono<Void> handle(ServerWebExchange exchange) {
    // 1. 获取当前路由所有过滤器：全局 + 路由局部
    List<GatewayFilter> allFilters = getFilters(exchange);
    // 2. 按Ordered排序：getOrder() 值越小越先执行
    allFilters.sort(AnnotationAwareOrderComparator.INSTANCE);
    // 3. 构建过滤器链 GatewayFilterChain
    GatewayFilterChain chain = new DefaultGatewayFilterChain(allFilters);
    // 4. 执行第一个过滤器，就是你的AuthGlobalFilter
    return chain.filter(exchange);
}
```
你代码里 `return chain.filter(exchange)` 就是**责任链模式**，放行到下一个过滤器。

 DefaultGatewayFilterChain 责任链源码极简
```java
class DefaultGatewayFilterChain implements GatewayFilterChain {
    private final int index;
    private final List<GatewayFilter> filters;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange) {
        if (this.index < this.filters.size()) {
            GatewayFilter filter = this.filters.get(this.index);
            // 执行当前过滤器，传入下一级链（index+1）
            return filter.filter(exchange, new DefaultGatewayFilterChain(this.index + 1, this.filters));
        } else {
            // 所有过滤器执行完毕，开始转发请求到下游服务
            return this.route.getHandler().handle(exchange);
        }
    }
}
```
- 鉴权通过：走完所有filter，进入转发逻辑
- 鉴权失败：直接返回 `exchange.getResponse().setComplete()`，终止链路，不再走转发

 过滤器全部执行完：转发请求到下游微服务
路由URI是 `lb://mini-mall-user`，负载均衡处理器 `LoadBalancerWebHandler`：
1. 通过Nacos注册中心拉取服务实例列表
2. 使用负载均衡算法（轮询/随机）选一台服务节点
3. 创建新的HTTP客户端 `ReactorHttpClient`，发起跨服务HTTP调用
4. 下游服务返回响应，原路逐层返回过滤器，最终写回Netty响应

 响应回流流程（反向）
下游服务返回数据 → LoadBalancer → 倒序走完过滤器链 → HttpWebHandlerAdapter → ReactorNetty → TCP缓冲区 → 返回浏览器

---

```
浏览器TCP请求
↓
Reactor Netty HttpServer（底层网络）
↓ ReactorHttpHandlerAdapter 转换
HttpWebHandlerAdapter → 创建 ServerWebExchange
↓
DispatcherHandler（WebFlux调度中心）
↓
RoutePredicateHandlerMapping（路由匹配）
↓
FilteringWebHandler（组装过滤器）
↓ DefaultGatewayFilterChain 责任链
    └── 你的 AuthGlobalFilter（全局鉴权）
↓
LoadBalancerWebHandler（负载均衡转发下游）
↓
ReactorHttpClient 请求下游服务
↓ 响应原路返回
Netty写回TCP，浏览器接收结果
```

1. **Tomcat + Servlet（同步阻塞）**
   一个请求占一个线程，线程池固定，高并发阻塞；用 `HttpServletRequest`
2. **Reactor-Netty + WebFlux（异步非阻塞）**
   少量线程处理大量连接，基于事件驱动；无ThreadLocal，靠 `ServerWebExchange` 透传数据（所以网关不能用ThreadLocal存userId，只能放Header）

1. `getOrder() = -100`：FilteringWebHandler 会按order排序，数值小优先执行，保证鉴权在所有路由过滤器之前
2. `request.mutate().header()`：底层是 `ServerHttpRequestDecorator`，不可变对象，每次修改都生成新请求对象
3. `unauthorized()` 调用 `setComplete()`：直接终止Mono流，过滤器链中断，不会走到负载均衡转发逻辑
4. 全程返回 `Mono<Void>`：响应式流，所有操作都是异步非阻塞链式调用



gateway网关底层代码
一、启动阶段：配置加载 & HttpServer 初始化（配置流转链路）
1. yml 配置
yaml
server:
  port: 8080
2. ServerProperties（Spring 内置配置绑定类）
java
运行
@ConfigurationProperties(prefix = "server")
public class ServerProperties {
	private Integer port;
	public Integer getPort() {return port;}
	public void setPort(Integer port) {this.port = port;}
}
SpringBoot 自动实例化，自动绑定server.port值。
3. NettyReactiveWebServerFactory（Spring 自动配置工厂）
java
运行
public class NettyReactiveWebServerFactory implements ReactiveWebServerFactory {
	private Integer port;
	public NettyReactiveWebServerFactory(ServerProperties serverProperties) {
		this.port = serverProperties.getPort();
	}
	@Override
	public ReactiveWebServer getWebServer(HttpHandler httpHandler) {
		// 1. 创建HttpServer，内部实例空HttpServerConfig
		HttpServer httpServer = HttpServer.create()
				// 2. 将yml端口写入HttpServerConfig
				.port(this.port)
				// 3. 包装Spring顶层HttpHandler，存入config.handler
				.handle(new ReactorHttpHandlerAdapter(httpHandler));
		// 同步绑定端口启动Netty服务
		DisposableServer disposableServer = httpServer.bindNow();
		return new NettyReactiveWebServer(disposableServer);
	}
}
4. HttpServer & HttpServerConfig（Reactor 网络配置载体）
HttpServer
java
运行
public final class HttpServer {
	private final HttpServerConfig config;
	private HttpServer(HttpServerConfig config) {this.config = config;}
	public static HttpServer create() {
		return new HttpServer(new HttpServerConfig());
	}
	public HttpServer port(int port) {
		this.config.port(port);
		return this;
	}
	public HttpServer handle(HttpHandler handler) {
		this.config.handler(handler);
		return this;
	}
	public Mono<DisposableServer> bind() {
		return HttpServerBind.bind(config);
	}
}
HttpServerConfig（全局只读配置，全连接共享）
java
运行
public final class HttpServerConfig extends ServerTransportConfig<HttpServerConfig> {
	private HttpHandler handler;
	private HttpDecoderSpec decoderSpec;
	private boolean wiretap;
	public HttpServerConfig() {
		super();
		this.decoderSpec = new HttpDecoderSpec();
	}
	public HttpServerConfig handler(HttpHandler handler) {
		this.handler = handler;
		return this;
	}
	public HttpHandler handler() {return this.handler;}
	public HttpServerConfig port(int port) {
		super.port(port);
		return this;
	}
	public int port() {return super.port();}
}
5. HttpServerBind 传递全局 Config 到通道初始化器
java
运行
final class HttpServerBind {
	static Mono<DisposableServer> bind(HttpServerConfig config) {
		TcpServer tcpServer = TcpServer.create()
				.port(config.port())
				// 把全局配置传入HttpServerChannelInitializer
				.handle(new HttpServerChannelInitializer(config));
		return tcpServer.bind().map(DisposableServerImpl::new);
	}
}
启动配置流转总结
yml server.port → ServerProperties → NettyReactiveWebServerFactory → HttpServer.port()写入HttpServerConfig
ReactorHttpHandlerAdapter存入HttpServerConfig.handler → HttpServerBind将 config 交给HttpServerChannelInitializer
二、TCP 连接建立：Channel、ChannelPipeline、ChannelInitializer 关系
核心概念释义
Channel：单条客户端 TCP 连接的抽象，一个浏览器连接对应一个独立 Channel，持有 Pipeline
ChannelPipeline：Channel 内部处理器链表，二进制数据入站 / 出站依次经过链内所有 Handler
ChannelInitializer<Channel>：Channel 创建时一次性执行的初始化器，仅执行一次，负责给 Pipeline 装配编解码、业务处理器
HttpServerChannelInitializer 源码（携带全局 HttpServerConfig）
java
运行
final class HttpServerChannelInitializer extends ChannelInitializer<Channel> {
	private final HttpServerConfig config;
	HttpServerChannelInitializer(HttpServerConfig config) {
		this.config = config;
	}
	@Override
	protected void initChannel(Channel ch) throws Exception {
		ChannelPipeline p = ch.pipeline();
		// 1. HTTP二进制解码器
		p.addLast(new HttpServerCodec());
		// 2. 聚合分段请求体
		p.addLast(new HttpObjectAggregator(1024*1024));
		// 3. 业务处理器，传入全局config
		p.addLast(new HttpServerOperationsHandler(config));
	}
}
TCP 连接创建时序
Netty 启动监听 8080 端口，注册HttpServerChannelInitializer
浏览器 TCP 握手成功，Netty 创建Channel ch代表当前 TCP 通道
自动执行initChannel(ch)，给当前通道 Pipeline 装配全套处理器
客户端发送 HTTP 二进制ByteBuf，按顺序流经 Pipeline 内 Handler


TCP 连接建立：Channel、ChannelPipeline、ChannelInitializer 关系
核心概念释义
Channel：单条客户端 TCP 连接的抽象，一个浏览器连接对应一个独立 Channel，持有 Pipeline
ChannelPipeline：Channel 内部处理器链表，二进制数据入站 / 出站依次经过链内所有 Handler
ChannelInitializer<Channel>：Channel 创建时一次性执行的初始化器，仅执行一次，负责给 Pipeline 装配编解码、业务处理器
HttpServerChannelInitializer 源码（携带全局 HttpServerConfig）
java
运行
final class HttpServerChannelInitializer extends ChannelInitializer<Channel> {
	private final HttpServerConfig config;
	HttpServerChannelInitializer(HttpServerConfig config) {
		this.config = config;
	}
	@Override
	protected void initChannel(Channel ch) throws Exception {
		ChannelPipeline p = ch.pipeline();
		// 1. HTTP二进制解码器
		p.addLast(new HttpServerCodec());
		// 2. 聚合分段请求体
		p.addLast(new HttpObjectAggregator(1024*1024));
		// 3. 业务处理器，传入全局config
		p.addLast(new HttpServerOperationsHandler(config));
	}
}
TCP 连接创建时序
Netty 启动监听 8080 端口，注册HttpServerChannelInitializer
浏览器 TCP 握手成功，Netty 创建Channel ch代表当前 TCP 通道
自动执行initChannel(ch)，给当前通道 Pipeline 装配全套处理器
客户端发送 HTTP 二进制ByteBuf，按顺序流经 Pipeline 内 Handler
三、HTTP 报文入站完整执行链路（从字节到网关过滤器）
步骤 1：HttpServerCodec 二进制 ByteBuf → Netty 原生 HttpRequest
public class HttpServerCodec extends CombinedChannelDuplexHandler<HttpRequestDecoder, HttpResponseEncoder> {
    public HttpServerCodec() {
        super(new HttpRequestDecoder(), new HttpResponseEncoder());
    }
}
// HttpRequestDecoder 实际解析字节，组装Netty请求对象
public class HttpRequestDecoder extends ReplayingDecoder<Void> {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        // 解析二进制buffer，按HTTP协议拆分请求行、请求头
        DefaultHttpRequest request = new DefaultHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.GET,
                "/user/login"
        );
        request.headers().add("Authorization", "Bearer xxx");
        // 将解析完成的Netty请求对象传入下一个Handler
        out.add(request);
    }
}
步骤 2：HttpServerOperationsHandler 包装 Netty 对象为 Reactor 请求
java
运行
final class HttpServerOperationsHandler extends ChannelDuplexHandler {
    private final HttpServerConfig config;
    HttpServerOperationsHandler(HttpServerConfig config) {
        this.config = config;
    }
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // msg 是HttpServerCodec解码生成的Netty HttpRequest
        if (msg instanceof HttpRequest) {
            HttpRequest nettyHttpRequest = (HttpRequest) msg;
            // 包装Reactor内部请求载体，传入全局config、连接上下文、原生请求，把上下文绑定到请求对象
            HttpServerOperations ops = new HttpServerOperations(ctx, nettyHttpRequest, config);
            // 从全局config取出ReactorHttpHandlerAdapter，执行跨层转换
            config.handler().handle(ops, ops).subscribe(new ChannelFutureSubscriber(ctx));
        }
    }
}
步骤 3：HttpServerOperations（Reactor 层包装对象，持有底层 Netty 请求）
java
运行
final class HttpServerOperations implements HttpServerRequest, HttpServerResponse {
    private final ChannelHandlerContext ctx;
    private final HttpRequest nettyRequest;
    HttpServerOperations(ChannelHandlerContext ctx, HttpRequest nettyRequest, HttpServerConfig config) {
        this.ctx = ctx;
        this.nettyRequest = nettyRequest;
    }
    @Override
    public String method() {
        return nettyRequest.method().name();
    }
    @Override
    public URI uri() {
        return URI.create(nettyRequest.uri());
    }
    @Override
    public reactor.netty.http.HttpHeaders headers() {
        return ReactorHttpHeaders.from(nettyRequest.headers());
    }
    @Override
    public Flux<ByteBuf> receive() {
        // 流式读取TCP原始body字节流
    }
}
步骤 4：ReactorHttpHandlerAdapter（跨层核心桥接：Reactor 对象 → WebFlux 标准对象）
java
运行
package org.springframework.http.server.reactive;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import reactor.core.publisher.Mono;

public class ReactorHttpHandlerAdapter implements BiFunction<HttpServerRequest, HttpServerResponse, Mono<Void>> {
    private final HttpHandler httpHandler;
    private final DataBufferFactory bufferFactory;

    public ReactorHttpHandlerAdapter(HttpHandler httpHandler) {
        this.httpHandler = httpHandler;
        this.bufferFactory = new NettyDataBufferFactory(ByteBufAllocator.DEFAULT);
    }
    @Override
    public Mono<Void> apply(HttpServerRequest reactorReq, HttpServerResponse reactorResp) {
        // 转换为Spring WebFlux标准请求/响应
        ServerHttpRequest springRequest = new ReactorServerHttpRequest(reactorReq, bufferFactory);
        ServerHttpResponse springResponse = new ReactorServerHttpResponse(reactorResp, bufferFactory);
        // 交给WebFlux顶层处理器继续流转
        return this.httpHandler.handle(springRequest, springResponse);
    }
}
步骤 5：ReactorServerHttpRequest（过滤器 exchange.getRequest () 实际拿到的实现类）
public class ReactorServerHttpRequest extends AbstractServerHttpRequest {
    private final HttpServerRequest reactorRequest;
    private final DataBufferFactory bufferFactory;
    public ReactorServerHttpRequest(HttpServerRequest reactorRequest, DataBufferFactory bufferFactory) {
        super(initUri(reactorRequest), initHeaders(reactorRequest));
        this.reactorRequest = reactorRequest;
        this.bufferFactory = bufferFactory;
    }
    @Override
    public HttpMethod getMethod() {
        return HttpMethod.valueOf(reactorRequest.method());
    }
    @Override
    public String getRawPath() {
        return reactorRequest.uri().getRawPath();
    }
    @Override
    public Flux<DataBuffer> getBody() {
        // Netty ByteBuf → Spring DataBuffer，屏蔽底层字节细节
        return reactorRequest.receive().map(buf -> bufferFactory.wrap(buf.retain()));
    }
    @Override
    public Builder mutate() {
        return new DefaultServerHttpRequestBuilder(this);
    }
}
步骤 6：HttpWebHandlerAdapter 封装全局上下文 ServerWebExchange
java
运行
public class HttpWebHandlerAdapter implements HttpHandler {
    private final DispatcherHandler dispatcherHandler;
    @Override
    public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {
        // 打包请求、响应为唯一上下文，贯穿所有网关过滤器
        ServerWebExchange exchange = new DefaultServerWebExchange(
                request,
                response,
                new DefaultWebSessionManager(),
                new ServerCodecConfigurer(),
                new AcceptHeaderLocaleContextResolver()
        );
        // 进入WebFlux调度中心，匹配网关路由、执行全局鉴权过滤器
        return dispatcherHandler.handle(exchange);
    }
}
四、完整单向执行链路（极简串联）
启动阶段配置链路
yml server.port
→ ServerProperties
→ NettyReactiveWebServerFactory
→ HttpServer 写入端口、绑定ReactorHttpHandlerAdapter到HttpServerConfig
→ HttpServerBind 将全局 config 传入HttpServerChannelInitializer
→ Netty 启动监听 8080 端口
请求入站数据流链路
网卡二进制 ByteBuf
→ HttpServerCodec 解码 → Netty HttpRequest
→ HttpServerOperationsHandler 包装 → HttpServerOperations(Reactor HttpServerRequest)
→ config.handler() 取出ReactorHttpHandlerAdapter.apply()
→ 适配器构造ReactorServerHttpRequest(WebFlux 标准ServerHttpRequest)
→ HttpWebHandlerAdapter 封装ServerWebExchange
→ DispatcherHandler 匹配 yml 路由规则
→ FilteringWebHandler 组装过滤器链，执行你的AuthGlobalFilter鉴权
鉴权通过 → 负载均衡转发下游微服务；鉴权失败直接返回 401 终止链路
五、关键对象层级持有关系（层层包装，源头都是 Netty 解码对象）
plaintext
exchange.getRequest() → ReactorServerHttpRequest
    持有 → HttpServerOperations（Reactor请求）
        持有 → io.netty.handler.codec.http.HttpRequest（字节解码原始对象）
六、核心区分总结
HttpServerConfig：全局只读配置，启动时一次性加载 yml 端口、桥接适配器，所有 TCP 连接共享；
Channel：单条 TCP 连接专属，每个客户端请求独立 Channel，生命周期随 TCP 连接销毁；
ChannelInitializer：一次性初始化工具，仅 Channel 创建时装配 Pipeline 处理器；
HttpServerCodec：底层字节解码器，唯一处理二进制报文的类，产出 Netty 原生 HTTP 对象；
ReactorHttpHandlerAdapter：跨层桥接核心，打通 Netty/Reactor 网络层与 Spring WebFlux 网关业务层。


一、Netty 底层缺失类补齐
1. ByteBuf（网卡二进制载体，省略完整实现，只看作用）
Netty 存放 TCP 二进制字节的容器，客户端 HTTP 报文原始载体
java
运行
package io.netty.buffer;
public abstract class ByteBuf {
    // 存储二进制字节流，HttpServerCodec读取此对象解析HTTP
    public abstract byte readByte();
}
2. DefaultHttpRequest（HttpServerCodec 解码生成的原生 HTTP 对象）
java
运行
package io.netty.handler.codec.http;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.HttpMethod;

public class DefaultHttpRequest implements HttpRequest {
    private final HttpVersion protocolVersion;
    private final HttpMethod method;
    private final String uri;
    private final HttpHeaders headers;

    public DefaultHttpRequest(HttpVersion version, HttpMethod method, String uri) {
        this.protocolVersion = version;
        this.method = method;
        this.uri = uri;
        this.headers = new DefaultHttpHeaders();
    }

    @Override
    public HttpMethod method() {
        return method;
    }

    @Override
    public String uri() {
        return uri;
    }

    @Override
    public HttpVersion protocolVersion() {
        return protocolVersion;
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }
}
3. ChannelHandlerContext ctx（每一条 TCP 连接上下文，handler 入参 ctx 来源）
java
运行
package io.netty.channel;
public interface ChannelHandlerContext {
    // 当前绑定的TCP通道
    Channel channel();
    // 向下传递数据
    ChannelPipeline pipeline();
    // 写出响应数据
    ChannelFuture write(Object msg);
    // 刷新缓冲区
    ChannelFuture flush();
}
ctx：Netty 自动传入 channelRead(ChannelHandlerContext ctx, Object msg)
msg：流水线上游传递过来的数据，这里是解码后的 DefaultHttpRequest
4. Object msg 说明
Object 是所有 Java 对象父类，流水线流转数据统一用 Object 接收，运行时真实类型：
入站：DefaultHttpRequest / HttpContent
代码强转：HttpRequest nettyHttpRequest = (HttpRequest) msg;
二、Reactor 缺失顶层接口：HttpServerRequest、HttpServerResponse
1. reactor.netty.http.server.HttpServerRequest
java
运行
package reactor.netty.http.server;
import reactor.core.publisher.Flux;
import reactor.netty.http.HttpHeaders;
import io.netty.buffer.ByteBuf;
import java.net.URI;

public interface HttpServerRequest {
    // 请求方法 GET/POST
    String method();
    // 请求完整URI
    URI uri();
    // 请求头集合
    HttpHeaders headers();
    // 流式读取原始TCP body字节流
    Flux<ByteBuf> receive();
}
2. reactor.netty.http.server.HttpServerResponse
java
运行
package reactor.netty.http.server;
import reactor.core.publisher.Mono;
import reactor.netty.http.HttpHeaders;
import io.netty.buffer.ByteBuf;

public interface HttpServerResponse {
    // 设置响应状态码
    HttpServerResponse status(int code);
    // 写入响应体字节流
    Mono<Void> send(Flux<ByteBuf> body);
    // 响应头
    HttpHeaders headers();
}
3. BiFunction 函数式接口（ReactorHttpHandlerAdapter 父接口）
java
运行
package java.util.function;
public interface BiFunction<T, U, R> {
    // T=HttpServerRequest U=HttpServerResponse R=Mono<Void>
    R apply(T t, U u);
}


# 补齐缺失：HttpHandler、DefaultServerWebExchange、配套依赖类完整源码
. HttpHandler（WebFlux顶层统一入口接口）
全路径：`org.springframework.web.server.HttpHandler`
所有网络层最终都会调用该接口 `handle`，是Netty和Spring WebFlux的顶层契约
```java
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

public interface HttpHandler {
    // 入参：转换完成的WebFlux标准请求/响应
    Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response);
}
```
### 实现类：HttpWebHandlerAdapter
```java
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSessionManager;
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver;
import org.springframework.web.server.i18n.LocaleContextResolver;
import org.springframework.http.codec.ServerCodecConfigurer;

public class HttpWebHandlerAdapter implements HttpHandler {

    private final DispatcherHandler dispatcherHandler;
    private final WebSessionManager sessionManager = new DefaultWebSessionManager();
    private final ServerCodecConfigurer codecConfigurer = ServerCodecConfigurer.create();
    private final LocaleContextResolver localeResolver = new AcceptHeaderLocaleContextResolver();

    public HttpWebHandlerAdapter(DispatcherHandler dispatcherHandler) {
        this.dispatcherHandler = dispatcherHandler;
    }

    @Override
    public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {
        // 构建全局上下文 ServerWebExchange
        ServerWebExchange exchange = new DefaultServerWebExchange(
                request,
                response,
                this.sessionManager,
                this.codecConfigurer,
                this.localeResolver
        );
        // 交给WebFlux调度中心匹配路由、执行过滤器
        return this.dispatcherHandler.handle(exchange);
    }
}
```

 DefaultServerWebExchange（exchange 实际实现类）
全路径：`org.springframework.web.server.adapter.DefaultServerWebExchange`
贯穿网关全流程的上下文容器，持有request、response、会话、编码、国际化解析器
```java
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.WebSessionManager;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.server.i18n.LocaleContextResolver;
import reactor.core.publisher.Mono;
import java.util.Map;

public class DefaultServerWebExchange implements ServerWebExchange {

    private final ServerHttpRequest request;
    private final ServerHttpResponse response;
    private final WebSessionManager sessionManager;
    private final ServerCodecConfigurer codecConfigurer;
    private final LocaleContextResolver localeResolver;
    // 自定义属性，过滤器中 exchange.getAttribute() / setAttribute()
    private final Map<String, Object> attributes;

    // 就是你看到的五参构造
    public DefaultServerWebExchange(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSessionManager sessionManager,
            ServerCodecConfigurer codecConfigurer,
            LocaleContextResolver localeResolver
    ) {
        this.request = request;
        this.response = response;
        this.sessionManager = sessionManager;
        this.codecConfigurer = codecConfigurer;
        this.localeResolver = localeResolver;
        this.attributes = new java.util.HashMap<>();
    }

    // 过滤器 exchange.getRequest() 底层调用
    @Override
    public ServerHttpRequest getRequest() {
        return this.request;
    }

    @Override
    public ServerHttpResponse getResponse() {
        return this.response;
    }

    // 存取自定义属性（可临时存数据，不推荐存用户信息，无ThreadLocal）
    @Override
    public Map<String, Object> getAttributes() {
        return this.attributes;
    }

    // Session会话
    @Override
    public Mono<WebSession> getSession() {
        return this.sessionManager.getSession(this);
    }

    @Override
    public ServerCodecConfigurer getCodecConfigurer() {
        return this.codecConfigurer;
    }

    @Override
    public LocaleContextResolver getLocaleContextResolver() {
        return this.localeResolver;
    }

    // 复制exchange，替换新request（你添加X-User-Id时 exchange.mutate().request(mutatedReq).build()）
    @Override
    public Builder mutate() {
        return new DefaultServerWebExchangeBuilder(this);
    }
}
```

. 构造传入的三个配套参数类源码
# DefaultWebSessionManager（会话管理器）
```java
import org.springframework.web.server.WebSession;
import org.springframework.web.server.WebSessionManager;
import reactor.core.publisher.Mono;

public class DefaultWebSessionManager implements WebSessionManager {
    @Override
    public Mono<WebSession> getSession(ServerWebExchange exchange) {
        // 创建/读取当前请求会话
        return Mono.just(new DefaultWebSession());
    }
}
```

 ServerCodecConfigurer（JSON/序列化、body编解码配置）
```java
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.ServerCodecConfigurer;

public interface ServerCodecConfigurer extends ClientCodecConfigurer {
    static ServerCodecConfigurer create() {
        return new DefaultServerCodecConfigurer();
    }
}
```

 AcceptHeaderLocaleContextResolver（国际化解析，读取Accept-Language头）
```java
import org.springframework.web.server.i18n.LocaleContextResolver;
import org.springframework.web.server.ServerWebExchange;
import java.util.Locale;

public class AcceptHeaderLocaleContextResolver implements LocaleContextResolver {
    @Override
    public Locale resolveLocaleContext(ServerWebExchange exchange) {
        String acceptLang = exchange.getRequest().getHeaders().getFirst("Accept-Language");
        if (acceptLang == null) {
            return Locale.getDefault();
        }
        return Locale.forLanguageTag(acceptLang.split(",")[0]);
    }
}
```

 顶层接口 ServerWebExchange（过滤器中使用的接口）
```java
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.WebSession;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.server.i18n.LocaleContextResolver;
import reactor.core.publisher.Mono;
import java.util.Map;

public interface ServerWebExchange {
    ServerHttpRequest getRequest();
    ServerHttpResponse getResponse();
    Map<String, Object> getAttributes();
    Mono<WebSession> getSession();
    ServerCodecConfigurer getCodecConfigurer();
    LocaleContextResolver getLocaleContextResolver();
    // 修改request/response，生成新exchange
    Builder mutate();

    interface Builder {
        Builder request(ServerHttpRequest request);
        ServerWebExchange build();
    }
}
```

串联关系说明
1. `ReactorHttpHandlerAdapter.apply()` 拿到 `ReactorServerHttpRequest / ReactorServerHttpResponse`
2. 调用 `this.httpHandler.handle(springReq, springResp)`，这里的 `httpHandler` 实现是 `HttpWebHandlerAdapter`
3. `HttpWebHandlerAdapter.handle` 内部 new `DefaultServerWebExchange`，把请求、响应、会话、编码、国际化全部打包进一个上下文对象
4. `exchange` 完整传递到 `DispatcherHandler` → 路由匹配 → `FilteringWebHandler` → 你的 `AuthGlobalFilter`
5. 过滤器中 `exchange.getRequest()` 拿到包装后的WebFlux请求对象，层层追溯源头是Netty解码的 `DefaultHttpRequest`


















## 22  底层补习总览(本章及之后全是新增) <a id="sec174"></a>

```
v7/D 章节反复出现 @Component / @Autowired / 动态代理 / ClassLoader / Netty 这些词,但都没真讲透。22~31 章把这些底层补齐, 后面看任何 Spring 代码都不慌。
阅读建议: 写代码遇到不懂时随时跳查, 不必按顺序读。
22 章 → 总览
23 章 → Spring IoC (Bean + 容器 + @Autowired 真相)
24 章 → AOP + 代理 (@ExceptionHandler 怎么"无侵入")
25 章 → Spring Boot 自动装配三件套
26 章 → ClassLoader + fat jar 解剖
27 章 → HTTP 协议 + URI 五段
28 章 → Tomcat (BIO/NIO) vs Netty (事件循环)
29 章 → 动态代理统一论 (MyBatis Mapper / Feign / @Transactional 同一招)
30 章 → ThreadLocal 内存模型 + TTL 跨线程拷贝
31 章 → JWT 签名验签 (HMAC-SHA256) + BCrypt 加盐慢哈希
```

### 22.1 从开机到 /user/login: 整条链路全景 <a id="sec175"></a>

```
看完这一节你就知道 java -jar 到 curl 响应之间, 每一层在干嘛。后面的概念都是为这条链路服务。
```

#### 22.1.1 开机阶段(JVM 启动到 Spring 容器就绪)

```
java -jar mini-mall-user-0.0.1-SNAPSHOT.jar
   ↓
① 操作系统启动一个 JVM 进程(PID=11588)
   ↓
② JVM 找到 jar 的 MANIFEST.MF, 读取 Start-Class
       Start-Class: com.minimall.user.MiniMallUserApplication
       Main-Class:  org.springframework.boot.loader.launch.JarLauncher
   ↓
③ JarLauncher 自己当 ClassLoader, 把 BOOT-INF/lib/ 下几十个 jar 注册到自己里
   "看到 org.springframework.xxx 就去 spring-context-x.x.x.jar 里找 class 字节码"
   ↓
④ 调用 main() → SpringApplication.run(MiniMallUserApplication.class, args)
   ↓
⑤ Spring 启动:
   a) 创建 ApplicationContext(IoC 容器, 本质大 Map)
   b) 扫描 @ComponentScan 范围内所有类
       看到 @RestController / @Service / @Component / @Mapper 就【实例化】放进 Map
   c) 完成 @Autowired 注入(谁需要谁, 从容器里查出来塞过去)
   d) 启动 Tomcat(嵌入式, 监听 9001 端口)
   e) DispatcherServlet 注册路由表(@GetMapping 路径 → Controller 方法)
   ↓
⑥ 控制台 "Tomcat started on port 9001"
   主线程 sleep, 等 HTTP 请求
```

#### 22.1.2 请求阶段(curl 到响应返回)

```
curl -X POST -d '{"username":"alice","password":"123456"}' http://localhost:9001/user/login
   ↓
① TCP 三次握手 + HTTP 报文(纯文本):
       POST /user/login HTTP/1.1
       Content-Type: application/json
       Content-Length: 41
       <空行>
       {"username":"alice","password":"123456"}
   ↓
② Tomcat 监听线程接收 → 包装成 HttpServletRequest → 线程池挑 Thread-A
   ↓
③ DispatcherServlet:
   a) 查路由表: /user/login → UserController.login()
   b) 把 JSON body 反序列化成 UserLoginDTO
   c) @Valid 触发校验, 失败抛 MethodArgumentNotValidException
   d) 调用 controller.login(dto)
   ↓
④ Controller → Service → MyBatis Mapper(代理) → MySQL
   userMapper.selectOne(...)
      → MyBatis 拼 SQL: SELECT * FROM user WHERE username='alice' AND is_deleted=0
      → JDBC 发给 MySQL → 返回行 → 映射成 User 对象
   ↓
⑤ ENCODER.matches("123456", user.getPassword())  ← BCrypt 验证
   ↓ true
   jwtUtil.generateToken(user.getId(), user.getUsername())
      → HMAC-SHA256 签名 → "eyJhbGc..."
   ↓
⑥ Result.success(token) → Jackson 序列化成 JSON → Thread-A 写回 response
   ↓ Tomcat 通过 TCP 发回 curl
   ↓
⑦ Thread-A 归还线程池

curl 拿到: {"code":200,"message":"操作成功","data":"eyJhbGc..."}
⭐ 链路里每个箭头都对应后面章节的一个底层概念。
```

## 23  Spring IoC: 容器、Bean、@Autowired 真相 <a id="sec176"></a>

### 23.1 IoC 一句话 <a id="sec177"></a>

```
IoC = Inversion of Control (控制反转)。从前你要用某对象就自己 new, 现在你不 new, Spring 帮你 new 好放进容器, 需要时给你。
传统:
  UserService svc = new UserServiceImpl(new UserMapperImpl(new DataSource(...)));
  // 你要管所有对象的创建 + 依赖

Spring:
  @Autowired UserService svc;
  // 你只声明"我需要", Spring 自己造好塞过来
```

### 23.2 容器到底是个啥 <a id="sec178"></a>

```
ApplicationContext 本质就是一个 Map<String, Object>(简化讲)。每个对象有名字(beanName)。
// Spring 容器 ≈ 这样的 Map
{
    "userController"          : UserController 实例,
    "userServiceImpl"         : UserServiceImpl 实例,
    "userMapper"              : UserMapper 的动态代理实例,
    "jwtUtil"                 : JwtUtil 实例,
    "dataSource"              : HikariDataSource 实例,
    "globalExceptionHandler"  : GlobalExceptionHandler 实例,
    ... 上百个 Bean
}

填充流程:
  ① 启动时 Spring 扫描 @ComponentScan 范围内所有 .class 文件
  ② 看到带这些注解的类就实例化:
       @Component / @Service / @Repository / @Controller / @RestController
       @Configuration (它里面的 @Bean 也算)
       @Mapper (MyBatis 特殊处理: 动态代理生成代理实例)
  ③ 用类名首字母小写作为 beanName, 塞进 Map
```

### 23.3 @Autowired 真相 <a id="sec179"></a>

```
@RestController
public class UserController {

    @Autowired
    private IUserService userService;   // ⬅ Spring 从容器里找 IUserService 类型的 Bean
                                          //    找到 userServiceImpl (它 implements IUserService)
                                          //    用反射把字段赋值进去
}

3 种注入方式:

① 字段注入 (项目里都是这种, 最简单, 但不利于测试)
   @Autowired private XxxService xxxService;

② 构造器注入 (最推荐, Spring 官方建议, Lombok @RequiredArgsConstructor 自动生成)
   private final XxxService xxx;
   public UserController(XxxService xxx) { this.xxx = xxx; }
   // Spring 看到唯一构造器, 自动调用并把 Bean 传进来

③ Setter 注入 (写起来啰嗦, 现在很少用)
   public void setXxx(XxxService xxx) { this.xxx = xxx; }
```

### 23.4 Bean 生命周期(简化版) <a id="sec180"></a>

```
1. 实例化(无参构造调用)
2. 字段填充(@Autowired 一个一个塞进去)
3. BeanPostProcessor 干预(AOP 在这一步偷偷把对象换成代理对象)
4. @PostConstruct 方法被调
5. Bean 进入容器, 可被业务使用
6. (应用关闭时) @PreDestroy 方法被调
⭐ 第 3 步'换成代理对象'就是 AOP 入口, 见下一章。
```

### 23.5 项目里能用 IoC 看穿的代码 <a id="sec181"></a>

```
📁 mini-mall-user/src/main/java/com/minimall/user/controller/UserController.java
@RestController                          ← ① 让 Spring 把这个类装进容器
@RequestMapping("/user")
public class UserController {

    @Autowired                           ← ② 从容器里找 UserMapper 类型的 Bean 塞进来
    private UserMapper userMapper;       //   (实际塞进来的是动态代理对象, 不是真的 UserMapper 类)

    @Autowired
    private IUserService userService;    ← ③ 找 IUserService 类型 → userServiceImpl

    @Autowired
    private ProductFeignClient productFeignClient;  ← ④ Feign 客户端也是代理 (29 章)
}

启动后, Spring 容器里:
  - userController (UserController 实例)
    │
    ├─ userMapper      → MyBatis 生成的代理对象
    ├─ userService     → userServiceImpl 实例
    └─ productFeignClient → Feign 生成的代理对象
```

## 24  AOP + 代理: @ExceptionHandler 怎么'无侵入'生效 <a id="sec182"></a>

### 24.1 AOP 是什么 <a id="sec183"></a>

```
AOP = Aspect Oriented Programming(面向切面编程)。把'横切关注点'(日志/事务/异常/鉴权)从业务代码里抽出来, 统一管理。
没有 AOP, 你要这么写:
  public Result login(UserLoginDTO dto) {
      log.info("开始登录: " + dto.getUsername());        ← 日志
      Connection conn = ...; conn.beginTransaction();    ← 事务
      try {
          // 真正业务...
          conn.commit();
      } catch (Exception e) {
          conn.rollback();
          return Result.error("xxx");                    ← 异常
      }
  }
  → 50% 代码是横切关注点

有 AOP, 你这么写:
  @Transactional
  public Result login(UserLoginDTO dto) {
      // 只写业务, 横切全靠注解
  }
  → 业务代码干净
```

### 24.2 AOP 的原理: 代理对象偷偷换掉你的对象 <a id="sec184"></a>

```
你以为容器里是这样:
  userServiceImpl  → 真正的 UserServiceImpl 实例

实际上是这样:
  userServiceImpl  → 一个【代理对象】(它内部 wraps 真正的 UserServiceImpl)

调用方注入 IUserService:
  代理对象  ←─ @Autowired 注进来
     │
     │ 调用 login(dto)
     │
     ├─ ① 前置: 开事务 / 打日志 / 检查权限
     │
     ├─ ② 调真实方法: realUserServiceImpl.login(dto)
     │
     ├─ ③ 后置: 提交事务 / 回滚 / 记录返回值
     │
     └─ 返回结果
```

### 24.3 代理对象怎么生成(两种) <a id="sec185"></a>

```
方式 A: JDK 动态代理 (基于接口)
  if (你的类实现了某个接口) {
      Proxy.newProxyInstance(classLoader, interfaces, invocationHandler);
      // 生成一个新类, implements 同样的接口
      // 每个方法调用都被路由到 invocationHandler.invoke()
  }

方式 B: CGLib (基于继承)
  if (你的类没接口) {
      Enhancer.create(yourClass, methodInterceptor);
      // 生成一个【子类】, extends 你的类
      // 每个方法 override 一遍, 调用时被 methodInterceptor 拦截
  }

Spring 策略:
  Spring 4 之前: 有接口走 JDK 代理, 无接口走 CGLib
  Spring 4+   : 默认全部走 CGLib (proxy-target-class=true)
```

### 24.4 @ExceptionHandler 怎么工作(AOP 具体案例) <a id="sec186"></a>

```
📁 mini-mall-common-core/src/main/java/com/minimall/common/core/exception/GlobalExceptionHandler.java
@RestControllerAdvice                                ← 本质是 AOP 切面
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)       ← 注册到 Spring 的异常路由表
    public Result<Void> handleBusinessException(BusinessException e) { ... }

    @ExceptionHandler(Exception.class)               ← 兜底
    public Result<Void> handleException(Exception e) { ... }
}

启动时 Spring 做了什么:
  ① 扫描所有 @RestControllerAdvice 类
  ② 把每个 @ExceptionHandler 方法注册到一张表
       Map<异常类, Handler 方法> {
           BusinessException.class                  → handleBusinessException,
           MethodArgumentNotValidException.class    → handleValidException,
           BindException.class                      → handleBindException,
           Exception.class                          → handleException (兜底)
       }

请求执行时:
  Controller.method() 抛出 BusinessException
       ↓
  AOP 拦截到异常
       ↓
  查表: BusinessException → handleBusinessException
       ↓
  调用 handler, 把返回值(Result.error(...))作为响应写回
⭐ 关键: @ExceptionHandler 不需要你 try-catch / throws, 全靠 AOP 拦截。
```

### 24.5 项目里哪些是 AOP? <a id="sec187"></a>

```
你写 / 见过的 AOP:
  @Transactional          ← 事务
  @RestControllerAdvice   ← 全局异常(GlobalExceptionHandler)
  @Cacheable              ← 缓存(@EnableCaching)
  @Async                  ← 异步执行
  @PreAuthorize           ← 权限注解(Spring Security)
  Filter / Interceptor    ← 也算横切

它们全部基于"代理对象"实现, 万变不离其宗。
```

## 25  Spring Boot 自动装配三件套 <a id="sec188"></a>

### 25.1 为什么不用写一堆 XML 配置就能启动? <a id="sec189"></a>

```
Spring Boot 的核心 = '约定大于配置'。引一个 starter 依赖, 一堆 Bean 自动就有了。为啥? 因为有 3 个机制配合: @EnableAutoConfiguration + starter 依赖 + AutoConfiguration.imports 文件。
```

### 25.2 @SpringBootApplication 三合一 <a id="sec190"></a>

```
@SpringBootApplication
= @SpringBootConfiguration  ←  其实就是 @Configuration
+ @EnableAutoConfiguration  ←  ⭐ 触发自动装配的关键开关
+ @ComponentScan            ←  默认扫描启动类同级及子包
```

### 25.3 自动装配的物理触发机制 <a id="sec191"></a>

```
触发链条:

  你引入 spring-cloud-starter-gateway 依赖
       ↓
  这个 jar 包里有 META-INF/spring/
                  org.springframework.boot.autoconfigure.AutoConfiguration.imports
       ↓
  文件内容是几行类名:
       org.springframework.cloud.gateway.config.GatewayAutoConfiguration
       org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration
       ...
       ↓
  Boot 启动时, 由 @EnableAutoConfiguration 触发,
  把这些类全部当 @Configuration 注册到容器
       ↓
  这些 @Configuration 里的 @Bean 方法生成的对象, 全部进容器
       ↓
  你不写一行配置, 路由 Filter 全有了
```

### 25.4 项目里看得到的自动装配证据 <a id="sec192"></a>

```
📁 mini-mall-cloud/pom.xml — 在父 dependencies 加了 spring-cloud-starter-bootstrap, 所有子模块自动启用 bootstrap.yml 加载。
📁 jar 包里(任意 starter): META-INF/spring/AutoConfiguration.imports 这个文件就是约定的入口。
⭐ 这就是为什么 v7 第 6.2 节强调 AutoConfiguration.imports 是 Boot 3+ 替代 spring.factories 的新机制。
```

## 26  ClassLoader + fat jar 解剖 <a id="sec193"></a>

```
D 阶段坑 ② NoClassDefFoundError: jakarta/servlet/http/HttpServletRequest 就是 ClassLoader 找不到类。理解 ClassLoader 才能根治这类问题。
```

### 26.1 ClassLoader 是什么 <a id="sec194"></a>

```
Java 不直接执行 .class 文件, 而是先把 .class 加载进 JVM 内存变成 Class 对象。干这件事的就是 ClassLoader。
4 层 ClassLoader (Java 9+):

  BootstrapClassLoader (C++ 写的, 加载核心 java.* / javax.*)
       ↑
  PlatformClassLoader  (加载 JDK 平台模块)
       ↑
  AppClassLoader       (加载你的代码 + 第三方 jar, classpath 上的所有东西)
       ↑
  (可选) 自定义 ClassLoader, Spring Boot 的 LaunchedURLClassLoader 就是

加载规则: 双亲委派
  AppClassLoader 加载某个类时, 先委托父 → 父再委托父 → 一直委托到 Bootstrap
  父加载不到, 才轮到自己加载。
  好处: 同一个类不会被加载两次, 防 jakarta.servlet.* 被恶意第三方覆盖。
```

### 26.2 NoClassDefFoundError 是什么 <a id="sec195"></a>

```
NoClassDefFoundError ≠ ClassNotFoundException

ClassNotFoundException:
  你显式调 Class.forName("xxx") 找不到类时抛
  受检异常, 编译期就提醒你处理

NoClassDefFoundError:
  类编译时存在, 但运行时 ClassLoader 找不到 .class 字节码
  非受检 Error, 运行期才暴露

D 阶段坑 ② 场景:
  common-core 编译时 jakarta.servlet-api(scope=provided) 在 classpath
  → 编译成功, 字节码里有 jakarta.servlet.http.HttpServletRequest 引用
  gateway 运行时 classpath 没 jakarta.servlet-api(WebFlux 不引)
  → ClassLoader 找不到 HttpServletRequest → NoClassDefFoundError
```

### 26.3 fat jar 解剖 <a id="sec196"></a>

```
Spring Boot 打的 jar 不是普通 jar, 叫 fat jar(uber jar), 内部嵌入了所有依赖。
mini-mall-gateway-0.0.1-SNAPSHOT.jar (解压后)
├── META-INF/
│   └── MANIFEST.MF                ← 关键: 指明启动入口
│       Main-Class: org.springframework.boot.loader.launch.JarLauncher
│       Start-Class: com.minimall.gateway.MiniMallGatewayApplication
│
├── BOOT-INF/
│   ├── classes/                   ← 你的代码 .class 文件
│   │   └── com/minimall/gateway/
│   │       ├── MiniMallGatewayApplication.class
│   │       ├── filter/AuthGlobalFilter.class
│   │       └── util/JwtUtil.class
│   │
│   └── lib/                       ← 全部依赖的 jar
│       ├── spring-cloud-gateway-server-x.x.x.jar
│       ├── spring-boot-x.x.x.jar
│       ├── netty-all-x.x.x.jar
│       ├── ... (一百多个)
│
└── org/springframework/boot/loader/
        └── launch/JarLauncher.class  ← Spring Boot 提供的启动器
启动流程: JarLauncher → 自定义 ClassLoader 把 BOOT-INF/lib/*.jar 全注册 → 调用 Start-Class 的 main()
```

## 27  HTTP 协议本身 + URI 五段 <a id="sec197"></a>

### 27.1 HTTP 是个文本协议 <a id="sec198"></a>

```
一个完整的 HTTP 请求(用 telnet/wireshark 抓出来的样子):

POST /user/login HTTP/1.1[CR][LF]               ← 请求行 (Method Path Version)
Host: localhost:9080[CR][LF]                    ← header 1
Content-Type: application/json[CR][LF]          ← header 2
Authorization: Bearer eyJhbGc...[CR][LF]        ← header 3
Content-Length: 41[CR][LF]
[CR][LF]                                         ← 空行 (header 结束)
{"username":"alice","password":"123456"}        ← body

[CR][LF] = \r\n (回车换行), 这是 HTTP 规定的行结束符
```

### 27.2 HTTP 响应也是文本 <a id="sec199"></a>

```
HTTP/1.1 200 OK[CR][LF]                          ← 状态行 (Version Code Reason)
Content-Type: application/json[CR][LF]
Content-Length: 105[CR][LF]
Date: Fri, 19 Jun 2026 12:08:21 GMT[CR][LF]
[CR][LF]
{"code":200,"message":"操作成功","data":"eyJhbGc..."}
```

### 27.3 URI 五段 <a id="sec200"></a>

```
Uniform Resource Identifier (统一资源标识符), HTTP URL 就是 URI 的子集。
完整 URI: scheme://authority/path?query#fragment

http://alice:secret@localhost:9080/user/me?id=1&type=full#section2
└─┬─┘  └────┬─────┘ └────┬─────┘ └──┬──┘ └─────┬──────┘ └──┬───┘
  │       │              │           │           │           │
  scheme   userInfo      host:port  path        query     fragment
  (协议)   (账号信息)     (主机端口)  (路径)      (查询串)   (锚点)

\__________ authority _________/

Java URI 类 API 取 5 段:
  URI uri = new URI("http://localhost:9080/user/me?id=1");
  uri.getScheme()   → "http"
  uri.getHost()     → "localhost"
  uri.getPort()     → 9080
  uri.getPath()     → "/user/me"
  uri.getQuery()    → "id=1"
  uri.getFragment() → null
项目里 AuthGlobalFilter 用的就是这个 API:
ServerHttpRequest request = exchange.getRequest();
String path = request.getURI().getPath();   ← 只关心 path, 用来判断白名单
                       ▲
              拿到 URI 对象 → 取 path 段
```

## 28  Tomcat (BIO/NIO) vs Netty (事件循环) <a id="sec201"></a>

### 28.1 一句话定位 <a id="sec202"></a>

```
Tomcat = 传统 Servlet 容器, 一请求一线程, MVC 默认用它。
Netty = 事件驱动的高并发 NIO 框架, 少量线程撑万级连接, WebFlux/Gateway 用它。
```

### 28.2 Tomcat 线程模型(BIO 简化版) <a id="sec203"></a>

```
Tomcat (默认 200 个工作线程)

  Acceptor 线程 (监听 9001 端口, 收新连接)
       │
       │ 来一个新 TCP 连接
       ↓
  从 线程池 挑一个空闲 Thread-X
       │
       │ Thread-X 一直跟这个连接绑定:
       │   ① 读完整个 HTTP 请求 (阻塞 socket.read())
       │   ② 派发给 DispatcherServlet
       │   ③ 调 Controller → Service → DB (阻塞 jdbc.executeQuery())
       │   ④ 写回响应 (阻塞 socket.write())
       │ 这中间任何一步卡, Thread-X 都干等
       │
       │ 请求结束
       ↓
  Thread-X 归还线程池

问题:
  - DB 慢 → Thread 干等 → 占着线程不干活
  - 高并发 (>200 同时) → 排队 → 后来的请求超时
  - 加并发只能加线程 → 一个 Thread 1MB 栈 → 内存爆
```

### 28.3 Netty 事件循环模型 <a id="sec204"></a>

```
Netty (默认 CPU 核数 * 2 个 EventLoop 线程, 比如 16)

  Boss EventLoop (监听 9080)
       │ 收新连接 → 注册给某个 Worker
       ↓
  Worker EventLoop (Thread-EL, 处理 N 个连接)
       │
       │ for (;;) {                          ← 事件循环 (永远不停)
       │     events = selector.select();    ← 一次问操作系统"哪些连接有数据可读"
       │     for (event : events) {
       │         if (可读) {
       │             读取数据 (非阻塞);
       │             把数据交给 Pipeline 处理 (Handler 链);
       │             Handler 处理时遇到耗时 IO?
       │                → 不阻塞! 注册回调"数据回来再通知我"
       │                → Thread-EL 立刻去处理下一个连接
       │         }
       │     }
       │ }
       │
       │ 一个 Thread-EL 同时处理 N 个连接 (上千上万都行)

3 秒后, 慢操作 (DB/网络) 数据回来:
       │ 操作系统通知 Netty
       │ Thread-EL 拿出之前留的回调, 接着处理
       │
       │ 但: 你写的代码必须是"非阻塞"风格 → return Mono<>
       │   一旦你在 EventLoop 线程里写阻塞代码 (像 Thread.sleep),
       │   整个 EventLoop 跟着卡 → 所有连接都停摆
```

### 28.4 一图对比 <a id="sec205"></a>

```
                     Tomcat                          Netty
                  ─────────                       ─────────
线程数          200 个工作线程                  CPU * 2 个 EventLoop (16 个)
处理模型        一线程一请求                    一线程多连接
IO 模型         BIO / NIO (默认 NIO)            NIO (基于 selector)
阻塞性          线程会阻塞等待                  线程不阻塞, 用回调
你写的代码      return Result<>(同步)           return Mono<Result> (异步)
适合场景        中等并发, 业务复杂              高并发, 业务简单(转发/聊天室)
项目里          user / product (MVC)            gateway (WebFlux)
日志特征        "Tomcat started on port"        "Netty started on port"
```

## 29  动态代理统一论 (MyBatis Mapper / Feign / @Transactional 同一招) <a id="sec206"></a>

```
项目里 3 处大魔法: UserMapper 接口能注入、ProductFeignClient 接口能注入、@Transactional 能开事务  —— 全是同一招: 动态代理。学一次理解一类。
```

### 29.1 共同套路 <a id="sec207"></a>

```
3 个场景的共同结构:
  ① 你只声明一个【接口】, 不写实现
  ② 框架在启动时扫到这个接口
  ③ 框架用反射 / 字节码增强, 在内存里【生成一个实现类】(代理类)
  ④ 把代理类的实例放进 Spring 容器
  ⑤ 你 @Autowired 时拿到的是这个代理实例
  ⑥ 你调代理实例的方法时, 调用被框架拦截, 干"真正的事"

| 场景             | 拿到接口后干啥                                            |
|------------------|----------------------------------------------------------|
| MyBatis Mapper   | 拼 SQL → JDBC → 映射结果                                  |
| Feign Client     | 拼 HTTP → 发请求 → JSON 反序列化                          |
| @Transactional   | 开事务 → 调真方法 → 成功 commit 失败 rollback              |
| @ExceptionHandler | 调真方法 → 抛异常时查表 → 调对应 handler                  |
```

### 29.2 MyBatis UserMapper 的 selectById 调用过程 <a id="sec208"></a>

```
📁 mini-mall-user/src/main/java/com/minimall/user/mapper/UserMapper.java
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 一行实现都没写
}

启动时:
  ① @MapperScan("com.minimall.user.mapper") 触发扫描
  ② MyBatis 找到 UserMapper 接口
  ③ MyBatis 用 JDK 动态代理生成一个实现类 (假设叫 UserMapper$Proxy0)
  ④ UserMapper$Proxy0 instance 放进 Spring 容器
  ⑤ @Autowired UserMapper userMapper 拿到这个代理实例

调用时:
  userMapper.selectById(1L)
       ↓
  代理拦截 invoke()
       ↓
  内部:
    method = "selectById", args = [1L]
    继承链找到 BaseMapper.selectById 的元信息
    → 拼 SQL: SELECT * FROM user WHERE id=?
    → 自动加 AND is_deleted=0 (逻辑删除)
    → JDBC PreparedStatement.setLong(1, 1L)
    → executeQuery()
    → ResultSet 映射成 User 对象
       ↓
  return user
```

### 29.3 Feign ProductFeignClient 的 getById 调用过程 <a id="sec209"></a>

```
📁 mini-mall-user/src/main/java/com/minimall/user/client/ProductFeignClient.java
@FeignClient(name = "mini-mall-product", url = "http://localhost:9002")
public interface ProductFeignClient {
    @GetMapping("/product/{id}")
    Result<Map<String, Object>> getById(@PathVariable("id") Long id);
}

启动时:
  ① @EnableFeignClients 触发扫描
  ② Spring Cloud OpenFeign 找到这个接口
  ③ 用 JDK 动态代理生成代理对象
  ④ 代理对象进 Spring 容器

调用时:
  productFeignClient.getById(1L)
       ↓
  代理拦截
       ↓
  内部:
    读 @FeignClient.url = "http://localhost:9002"
    读 @GetMapping.path = "/product/{id}"
    拼出 URL: http://localhost:9002/product/1
    用 HttpClient / OkHttp 发 HTTP GET
    收到 JSON 响应
    用 Jackson 反序列化成 Result<Map<String, Object>>
       ↓
  return result
```

### 29.4 @Transactional 怎么开事务 <a id="sec210"></a>

```
@Service
public class UserServiceImpl implements IUserService {

    @Transactional
    public User register(UserRegisterDTO dto) {
        // 业务代码 (你看不到事务在哪开)
    }
}

启动时:
  Spring 看到 @Transactional 注解
       ↓
  用 CGLib 给 UserServiceImpl 生成一个【子类】(代理类)
  子类 override 了 register 方法

调用时:
  userService.register(dto)  ← 实际调到代理的 register
       ↓
  代理类的 register:
    ① 获取 Connection
    ② conn.setAutoCommit(false)        ← 开启事务
    ③ try {
         super.register(dto);           ← 调真正的方法 (父类)
         conn.commit();                  ← 成功提交
       } catch (RuntimeException e) {
         conn.rollback();                ← 失败回滚
         throw e;
       }
    ④ 释放 Connection
```

### 29.5 三大魔法对照表 <a id="sec211"></a>

```
|     | MyBatis Mapper            | Feign Client              | @Transactional             |
|-----|---------------------------|---------------------------|----------------------------|
|接口 | extends BaseMapper        | @FeignClient + 方法注解   | 普通方法上加 @Transactional|
|有实现| ❌                       | ❌                        | 类自身有实现, 但用代理包装 |
|代理类型| JDK Proxy              | JDK Proxy                 | CGLib (默认)               |
|代理干啥| 拼 SQL → JDBC          | 拼 HTTP → JSON            | 开/提/滚 事务               |
|启动开关| @MapperScan / @Mapper  | @EnableFeignClients       | @EnableTransactionManagement|
|你怎么用| @Autowired UserMapper  | @Autowired Feign          | 跟普通调用一样             |
```

### 29.6 看清这一招后 <a id="sec212"></a>

```
以后看到任何 '接口能注入的魔法':
   → 想'是不是动态代理?'
   → '代理拦截后干的什么事?'
   → 'JDK 还是 CGLib?'
学一次理解一类技术。
```

## 30  ThreadLocal 内存模型 + TTL 跨线程拷贝原理 <a id="sec213"></a>

```
v7 第 9 章和 D 笔记 21.2.3~21.2.5 讲了 ThreadLocal '是什么', 但没真讲底层内存结构 + 为什么会泄漏。这一章补齐。
```

### 30.1 ThreadLocal 内存模型(底层) <a id="sec214"></a>

```
误区: 很多人以为 ThreadLocal 自己存着每个线程的值。错。
真相: 值存在 Thread 对象里。ThreadLocal 自己只是个 key。
class Thread {
    ThreadLocalMap threadLocals;  // ⭐ 每个 Thread 对象里有一个 Map
}

class ThreadLocal<T> {

    public void set(T value) {
        Thread t = Thread.currentThread();        ← 拿当前线程
        ThreadLocalMap map = t.threadLocals;       ← 拿那个 Map
        map.set(this, value);                      ← this(ThreadLocal 实例) 当 key
    }

    public T get() {
        Thread t = Thread.currentThread();
        ThreadLocalMap map = t.threadLocals;
        return (T) map.get(this);                  ← 用 this 取
    }
}

所以:
  - 每个 Thread 对象内部都有一个 ThreadLocalMap
  - ThreadLocalMap 的 key 是 ThreadLocal 实例(本身)
  - value 是你 set 进去的数据

  Thread-A 的 Map: { CURRENT_ID → 100, CURRENT_USERNAME → "alice" }
  Thread-B 的 Map: { CURRENT_ID → 200, CURRENT_USERNAME → "bob" }

两个线程的 Map 互相独立 → 数据不串号
```

### 30.2 ThreadLocalMap 的 key 是【弱引用】(关键!) <a id="sec215"></a>

```
ThreadLocalMap 的 Entry 结构:

    static class Entry extends WeakReference<ThreadLocal<?>> {
        Object value;
        Entry(ThreadLocal<?> k, Object v) {
            super(k);          ← key 是弱引用包装
            value = v;         ← value 是强引用
        }
    }

弱引用是啥?
  普通引用(强引用): 只要还指着对象, GC 就不会回收
  弱引用: GC 一旦运行就回收, 不管有没有人指着

后果:
  ThreadLocal<Map> CONTEXT = new ThreadLocal<>();     ← CONTEXT 强引用着 ThreadLocal 实例

  threadLocalMap.set(CONTEXT, someMap):
       Entry 内部:
          key (弱引用) → ThreadLocal 实例 (但 CONTEXT 强引用着)
          value (强引用) → someMap

  如果 CONTEXT 变量被回收 / 不再持有 ThreadLocal:
       key 弱引用没人撑着 → GC 把 ThreadLocal 实例回收 → Entry.key 变 null
       但 value 还在! (强引用) → 内存泄漏 → 必须手动 remove

为什么 SecurityContextHolder.remove() 不能忘?
  Tomcat 线程池复用线程, Thread 对象一直活着 → ThreadLocalMap 也一直活着
  你不调 remove, value 永远不会被回收 → 内存泄漏
```

### 30.3 SecurityContextHolder 项目代码回顾 <a id="sec216"></a>

```
📁 mini-mall-common-core/src/main/java/com/minimall/common/core/context/SecurityContextHolder.java
public class SecurityContextHolder {

    // TTL 容器 (TransmittableThreadLocal 是阿里改造版 ThreadLocal, 支持线程池透传)
    private static final TransmittableThreadLocal<Map<String, Object>> CONTEXT
            = new TransmittableThreadLocal<>() {
        @Override
        protected Map<String, Object> initialValue() {
            return new HashMap<>();   // 首次 get() 自动给空 Map
        }
    };

    public static void set(String key, Object value) {
        CONTEXT.get().put(key, value);
    }

    public static String get(String key) {
        Object value = CONTEXT.get().get(key);
        return value == null ? "" : value.toString();
    }

    // ⭐⭐⭐ afterCompletion 必调
    public static void remove() {
        CONTEXT.remove();
    }
}
```

### 30.4 普通 ThreadLocal 在线程池下的【数据丢失】问题 <a id="sec217"></a>

```
微服务里常见的"莫名其妙拿不到 userId":

主线程 Thread-A:
  SecurityContextHolder.set("userId", 100);
       ↓
  asyncExecutor.submit(() -> {
       ↓ 提交任务给线程池
  });
       ↓
线程池 Thread-Worker (跟 Thread-A 不是同一个!)
       ↓
  SecurityContextHolder.get("userId");  → 拿不到! null
       ↓
  因为 ThreadLocalMap 是 Thread-A 的, Thread-Worker 的 Map 是空的
```

### 30.5 TTL (TransmittableThreadLocal) 的解决思路 <a id="sec218"></a>

```
阿里开源的 TransmittableThreadLocal, 思路是在【提交任务那一刻】把当前线程的 ThreadLocal 复制一份给目标线程。
普通流程 (ThreadLocal):
  主线程 set → ThreadLocalMap-A: {userId=100}
  submit(task)
       任务在 Thread-Worker 上跑 → ThreadLocalMap-Worker: {} → get 返回 null

TTL 流程:
  主线程 set (其实存到一个全局静态 Set + ThreadLocalMap)
  TtlExecutors.getTtlExecutorService(executor).submit(task)
                ↑ 关键: 包装过的 executor
       内部偷偷:
         ① 提交前: 拍照当前线程的所有 TTL 值 → 内存里存一份
         ② 任务跑前: Thread-Worker 复制这份照片到自己的 Map
         ③ 任务跑完: Thread-Worker 还原成空, 防泄漏

  → Thread-Worker 也能 get 到 userId=100 ✓
⭐ 关键: TTL 不需要你改 ThreadLocal 用法, 只需要把 executor 包一下。
```

## 31  JWT 签名验签(HMAC-SHA256) + BCrypt 加盐慢哈希 <a id="sec219"></a>

### 31.1 JWT 是什么(更深一层) <a id="sec220"></a>

```
JWT = JSON Web Token, 一种【自包含】的 token 格式。服务端不存 session, 把所有信息编进 token 里, 客户端每次带回来。
```

### 31.2 JWT 三段式结构 <a id="sec221"></a>

```
eyJhbGciOiJIUzI1NiJ9 . eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWxpY2UiLCJpYXQ... . FvGrIW3y...
└──── header ──────┘   └──────────── payload ────────────────────┘    └─ signature ┘

第 1 段 header (base64 url 编码):
  解码后: {"alg":"HS256"}
  说明算法: HS256 = HMAC-SHA256 对称签名

第 2 段 payload (base64 url 编码):
  解码后: {"userId":1,"username":"alice","iat":1781885555,"exp":1782490355}
  iat = issued at  (签发时间, epoch 秒)
  exp = expiration (过期时间)

第 3 段 signature:
  = HMAC-SHA256(
        base64(header) + "." + base64(payload),    ← 输入数据
        secret_key                                  ← 密钥
    )
  signature 是字节, 再 base64-url 编码
⚠️ 第 2 段是 base64, 不是加密! 任何人都能解码看到 payload。别把密码塞进去。
```

### 31.3 验签算法(secret 在做什么) <a id="sec222"></a>

```
客户端拿来 token = "head.payload.sig"
       ↓
服务端拆三段
       ↓
重新算 HMAC-SHA256(head + "." + payload, secret)
       ↓
跟 sig 比对:
  - 一致 → 没被篡改, 信任 → 取 payload 里 userId
  - 不一致 → 拒绝 (401)

伪代码:
  String[] parts = token.split("\\.");
  String signed = parts[0] + "." + parts[1];
  String expectedSig = hmacSha256(signed, secret);
  if (!expectedSig.equals(parts[2])) {
      throw SignatureException;        // 验签失败
  }
```

### 31.4 JJWT 库的 0.12+ 新 API 演变 <a id="sec223"></a>

```
v7 用的是 JJWT 0.12.6, 跟旧版本写法不一样。
旧 API (0.11 及之前, 已废弃):
  String token = Jwts.builder()
      .setClaims(claims)                     ← setXxx 风格
      .signWith(SignatureAlgorithm.HS256, secret)
      .compact();

  Claims c = Jwts.parser()
      .setSigningKey(secret)
      .parseClaimsJws(token)
      .getBody();

新 API (0.12+, 项目里用的):
  String token = Jwts.builder()
      .claims(claims)                        ← 直接 claims(), 不是 setClaims
      .signWith(secretKey)                   ← 直接传 SecretKey 对象
      .compact();

  Claims c = Jwts.parser()
      .verifyWith(secretKey)                 ← 新方法名
      .build()
      .parseSignedClaims(token)              ← 新方法名
      .getPayload();

为什么变?
  - 类型安全更强 (SecretKey 对象 vs 字符串)
  - WeakKeyException: HS256 要求密钥 >= 32 字节, 库主动校验防误用
  - API 更链式
```

### 31.5 项目里 user-service 的 JwtUtil.generateToken 内部细节 <a id="sec224"></a>

```
public String generateToken(Long userId, String username) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("userId", userId);
    claims.put("username", username);

    return Jwts.builder()
            .claims(claims)                                              ← ① payload 数据
            .issuedAt(new Date())                                        ← ② iat 字段
            .expiration(new Date(System.currentTimeMillis() + expiration)) ← ③ exp 字段
            .signWith(getSigningKey())                                   ← ④ 签名
            .compact();                                                   ← ⑤ 输出
}

⑤ compact() 内部步骤:
  a) 把 header JSON {"alg":"HS256"} 序列化 + base64url
  b) 把 payload claims 序列化 + base64url
  c) signature = HMAC-SHA256(a + "." + b, secret) + base64url
  d) 拼成 a.b.c 字符串返回
```

### 31.6 BCrypt 加盐慢哈希(更深一层) <a id="sec225"></a>

#### 31.6.1 为什么不用 MD5/SHA256 存密码?

```
MD5/SHA256 = 普通哈希
  特点: 同一明文 → 同一哈希
  问题:
    ① 彩虹表: 黑客预计算了 "123456" → "e10adc..." 这样的对照表, 一查就破
    ② 速度太快: GPU 每秒能算几十亿次, 暴力破解可行

BCrypt = 加盐 + 慢哈希
  ① 加盐: 同一明文每次哈希结果【不同】(每次随机生成 salt)
  ② 慢哈希: 故意迭代 2^N 次, 一次哈希要几十毫秒
            → 黑客单 GPU 暴力破解时间从几秒变几年
```

#### 31.6.2 BCrypt 哈希结果的结构

```
ENCODER.encode("123456")
→ "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
   │   │   └───── 22 位 salt ─────┘ └────── 31 位 hash ──────────┘
   │   └─ cost factor (10 = 2^10 = 1024 次迭代)
   └─ 版本标识 ($2a / $2b / $2y 都是 BCrypt 不同实现版本)

cost factor 影响:
  10  → 哈希一次 ~80ms (默认, 项目用这个)
  12  → 哈希一次 ~300ms
  14  → 哈希一次 ~1.2s
  cost factor 每 +1, 时间翻倍
```

#### 31.6.3 BCrypt 验证流程

```
ENCODER.matches("123456", storedHash):
  ① 从 storedHash 取出 cost(10) + salt(N9qo8u...)
  ② 用同样 cost + salt + "123456" 重新跑 BCrypt 哈希
  ③ 比对结果跟 storedHash 是否一致
  ④ 一致 → 密码正确

⭐ 加盐意味着:
  alice 密码 123456 → $2a$10$AAA...hash1
  bob   密码 123456 → $2a$10$BBB...hash2 (跟 alice 不一样!)
  → 彩虹表无效, 必须为每个 salt 单独建表
  → 黑客只能针对单用户暴力试, 不能批量破
```

### 31.7 项目里登录的完整签名/验签流程 <a id="sec226"></a>

```
注册 alice (密码 123456):
  ENCODER.encode("123456") → "$2a$10$N9q...hash_for_alice"
  userMapper.insert(user with password = "$2a$10$N9q...hash_for_alice")

登录 alice (输入 123456):
  ① user = userMapper.selectOne(username="alice")
  ② ENCODER.matches("123456", user.password):
       从 "$2a$10$N9q..." 取出 salt = "N9q..."
       用 salt 重哈希 "123456"
       比对哈希, 一致 → true
  ③ jwtUtil.generateToken(1, "alice"):
       payload = {"userId":1,"username":"alice","iat":...,"exp":...}
       sig = HMAC-SHA256(header.payload, jwt.secret)
       token = "eyJhbGc...eyJ1c2VySWQ...FvGrIW3y..."
  ④ Result.success(token) 返给前端

后续访问 (gateway 验签):
  前端: Authorization: Bearer eyJhbGc...
  gateway AuthGlobalFilter:
       ① 从 header 取 token
       ② jwtUtil.getUserIdFromToken(token):
           verifyWith(gateway.jwt.secret) → 重算 HMAC-SHA256
           跟 token 的 sig 比对 → 一致
           取出 payload 里的 userId = 1
       ③ 把 X-User-Id=1 塞进 header 转发给 user-service
```

## 附录 A1  全阶段命令速查 <a id="sec227"></a>

```
─── Maven ─────────────────────────────────────────────────────────
# 编译整个项目 (不跑测试)
mvn clean install -DskipTests

# 只编译某模块 + 它的依赖
mvn -pl mini-mall-user -am clean package -DskipTests

# 只看依赖树 (排查版本冲突)
mvn dependency:tree

# 跑指定服务 (dev 环境)
mvn spring-boot:run -pl mini-mall-user -Dspring-boot.run.profiles=dev

─── Java jar 启动 (Windows) ───────────────────────────────────────
# 必须用 JAVA_HOME 全路径, 因为 PATH 里 Java 8 占先
& "D:\jdk-21.0.11\bin\java.exe" -jar "<jar 全路径>"

# 后台运行 (PowerShell)
Start-Process -FilePath "D:\jdk-21.0.11\bin\java.exe" `
              -ArgumentList "-jar","<jar 路径>" `
              -RedirectStandardOutput "$env:TEMP\svc.log" `
              -NoNewWindow

─── 端口排查 ──────────────────────────────────────────────────────
# Windows: 看端口占用
netstat -ano | findstr :9080
# 看 PID 对应进程
tasklist /FI "PID eq <PID>"
# 杀进程
taskkill /F /PID <PID>

─── HTTP 测试 (curl + PowerShell) ─────────────────────────────────
# GET (简单)
curl.exe -s http://localhost:9080/user/me -H "Authorization: Bearer <token>"

# POST + JSON (重要: 别在命令行直接传 JSON, 用文件)
$body = '{"username":"alice","password":"123456"}'
Set-Content "$env:TEMP\login.json" $body -Encoding ascii -NoNewline
curl.exe -s -X POST -H "Content-Type: application/json" `
  --data-binary "@$env:TEMP\login.json" `
  http://localhost:9080/user/login
```

## 附录 A2  端口规划 & 启动顺序 <a id="sec228"></a>

```
| 服务                | 端口   | Web 框架  | 备注                              |
|---------------------|--------|-----------|-----------------------------------|
| user-service        | 9001   | Tomcat    | MVC, 连 MySQL                      |
| product-service     | 9002   | Tomcat    | MVC, 连 MySQL                      |
| mini-mall-gateway   | 9080   | Netty     | WebFlux, 不连 DB, Docker 占 9000  |
| MySQL               | 3306   | -         | 本机                              |
| (将来) Nacos         | 8848   | -         | E 阶段加                          |
| (将来) Redis         | 6379   | -         | F 阶段加                          |

启动顺序:
  ① user-service (9001)   ← 含登录接口, 必须先起
  ② product-service (9002)
  ③ gateway (9080)        ← 必须最后, 因为它要等下游可用

外部 URL (用户接入):
  全部走 http://localhost:9080  ← 网关统一入口
  /user/**    → user-service
  /product/** → product-service
```

## 附录 A3  v8 修订说明 <a id="sec229"></a>

```
v8 相比 v7 + D 笔记新增内容:
保留 (不动):
  v7 第 1~20 章   (A/B/C 阶段全部内容)
  D 笔记 21.0~21.11  (D 阶段全景 / D1 / D2)

新增:
  21.12  D3 完整代码 (AuthGlobalFilter + JwtUtil + 行级讲解)
  21.13  D4 三 jar 联调实录 (4 个 curl 场景 + 实测结果 + 3 个新坑)
  22~31  底层补习篇:
    22  开机到 /user/login 全景
    23  Spring IoC: 容器 / Bean / @Autowired 真相
    24  AOP + 代理: @ExceptionHandler 怎么"无侵入"
    25  Spring Boot 自动装配三件套
    26  ClassLoader + fat jar 解剖
    27  HTTP 协议本身 + URI 五段
    28  Tomcat (BIO/NIO) vs Netty (事件循环)
    29  动态代理统一论 (MyBatis / Feign / @Transactional)
    30  ThreadLocal 内存模型 + TTL 跨线程拷贝
    31  JWT 签名验签 (HMAC-SHA256) + BCrypt 加盐慢哈希

  附录 A1 全阶段命令速查
  附录 A2 端口规划 & 启动顺序
  附录 A3 v8 修订说明 (本节)
配套阅读建议:
  ① 第一遍按 1→31 顺序读 (前面是工程, 后面是底层)
  ② 写代码遇到不懂的概念时跳查 22~31 章
  ③ 准备讲解 / 面试时, 重点 23/24/29/30 (Spring + 代理 + ThreadLocal)
```

---

<!-- ===== 以下为后半本：第 32 章起，来自原 md 文件 ===== -->

# mini-mall-cloud E 阶段：Nacos 注册中心 <a id="sec230"></a>

> 接续 v8 (1~31 章) 的下一章节，承接 D 阶段网关后的服务发现能力。
> 完成日期：2026-06-19  |  与 v8.docx 配套阅读

---

## 32.0  E 阶段全景 <a id="sec231"></a>

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

## 32.1  基础：服务发现要解决什么问题 <a id="sec232"></a>

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

## 32.2  Nacos 是什么 <a id="sec233"></a>

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

## 32.3  E0：装 + 启 Nacos（Docker 方案） <a id="sec234"></a>

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

## 32.4  E1：3 服务接入 Nacos <a id="sec235"></a>

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

## 32.5  E2：Gateway 路由 lb:// 改造 <a id="sec236"></a>

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

## 32.6  E3：Feign 去掉 url 硬编码 <a id="sec237"></a>

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

## 32.7  E 阶段三大坑 <a id="sec238"></a>

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

## 32.8  E 阶段成果对比 <a id="sec239"></a>

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

## 32.9  附录 A：实测命令速查 <a id="sec240"></a>

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

# 第三十三章：F1 阶段 Nacos 配置中心 <a id="sec241"></a>

> 接续 E 阶段（32 章），同一个 Nacos Server 复用其【第二个能力】：配置中心。
> 完成日期：2026-06-19  |  F 阶段第一站

---

## 33.0  F 阶段全景 + F1 目标 <a id="sec242"></a>

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

## 33.1  F1 痛点（E 阶段后留下的） <a id="sec243"></a>

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

## 33.2  Nacos 配置中心是什么 <a id="sec244"></a>

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

## 33.3  4 个核心概念（F1 必懂） <a id="sec245"></a>

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

## 33.4  F1 路线图 <a id="sec246"></a>

| 步骤 | 做什么 | 改的文件 |
|---|---|---|
| F1.1 | 父 pom 加 `nacos-config` 依赖 | `pom.xml` |
| F1.2 | user-service 建 `bootstrap.yml` | 新建 |
| F1.3 | 在 Nacos 创建配置项 `mini-mall-user.yaml` | Nacos 控制台 / API |
| F1.4 | 重启验证：远程配置覆盖本地 | - |
| F1.5 | 改 Nacos 配置看是否动态刷新（会失败，留教学时刻）| - |
| F1.6 | 给 JwtUtil 加 `@RefreshScope` + 再验证 | `JwtUtil.java` |

---

## 33.5  F1.1：父 pom 加 nacos-config 依赖 <a id="sec247"></a>

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

## 33.6  F1.2：user-service 建 bootstrap.yml <a id="sec248"></a>

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

## 33.7  F1.3：在 Nacos 创建配置项 <a id="sec249"></a>

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

## 33.8  F1.4：重启 user-service 验证 <a id="sec250"></a>

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

## 33.9  F1.5：默认 @Value 不动态刷新（教学坑） <a id="sec251"></a>

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

## 33.10  F1.6：加 @RefreshScope 实现动态刷新 <a id="sec252"></a>

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

## 33.11  F1 底层原理（两条流程图） <a id="sec253"></a>

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

## 33.12  配置优先级规则 <a id="sec254"></a>

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

## 33.13  F1 三大坑 <a id="sec255"></a>

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

## 33.14  F1 目录结构变化 <a id="sec256"></a>

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

## 33.15  F1 命令速查 <a id="sec257"></a>

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

## 33.16  接下来：F 阶段后续预告 <a id="sec258"></a>

F1 解决了"配置怎么管"，剩下的 F2 / F3 / F4：

| 阶段 | 目标 | 学到 |
|---|---|---|
| **F2** | Sentinel 限流 + 熔断 | 防刷接口、防雪崩、降级 |
| **F3** | SkyWalking 链路追踪 | 全链路可视化、性能瓶颈定位 |
| **F4** | Seata 分布式事务 | 跨服务的 ACID（订单+库存）|

**推荐顺序**：F2 → F3 → F4（按依赖度，F2 最独立、F4 最复杂）。

也可以**先把 F1 扩到 3 个服务都用**（product / gateway 也建 bootstrap.yml + 把敏感配置都迁过去），然后再进 F2。

---

## 33.17  章节地图（更新到 F2） <a id="sec259"></a>

见本文档最末尾的 34.16 节。

---

# 第三十四章：F2 阶段 Sentinel 限流熔断 <a id="sec260"></a>

> 接续 F1（33 章），用同源体系（阿里）的下一个能力：流量治理。
> 完成日期：2026-06-20  |  F 阶段第二站

---

## 34.0  F2 阶段全景 <a id="sec261"></a>

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

## 34.1  F2 痛点 <a id="sec262"></a>

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

## 34.2  Sentinel 是什么 + 4 核心概念 <a id="sec263"></a>

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

## 34.3  F2 路线图（4 阶段 10 步） <a id="sec264"></a>

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

## 34.4  F2.0：装 Sentinel Dashboard（Docker） <a id="sec265"></a>

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

## 34.5  F2.1：父 pom 加 sentinel-starter <a id="sec266"></a>

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

## 34.6  F2.2：3 服务 yml 配置 <a id="sec267"></a>

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

## 34.7  F2.3：重打 + 启动 + Dashboard 验证 <a id="sec268"></a>

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

## 34.8  F2.4：@SentinelResource + BlockHandler + Fallback <a id="sec269"></a>

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

## 34.9  F2.5：配限流规则 + curl 压测 <a id="sec270"></a>

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

## 34.10  Sentinel 工作流程（源码级深入） <a id="sec271"></a>

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

## 34.11  F2 三大坑（真实踩过） <a id="sec272"></a>

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

## 34.12  F2 阶段成果对比 <a id="sec273"></a>

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

## 34.13  F2 目录结构变化 <a id="sec274"></a>

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

## 34.14  命令速查 <a id="sec275"></a>

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

## 34.15  F2.6 实战：异常数熔断完整闭环 <a id="sec276"></a>

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

## 34.16  F2.7 实战：网关全局限流 <a id="sec277"></a>

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

## 34.17  深入：Sentinel 规则到底是什么 <a id="sec278"></a>

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

## 34.18  F2.8 实战：系统保护（整机自动限流） <a id="sec279"></a>

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

## 34.19  F2.9 实战：规则持久化到 Nacos <a id="sec280"></a>

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

## 34.20  F 阶段总结 + 后续方向 <a id="sec281"></a>

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

## 34.21  v8 → v9 完整章节地图 <a id="sec282"></a>

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

# 第 35 章  G 阶段开篇：从"治理就绪"到"业务搬迁" <a id="sec283"></a>

## 35.0  我们处在哪里？ <a id="sec284"></a>

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

## 35.1  G 阶段路线图 <a id="sec285"></a>

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

## 35.2  DDD 拆分提案 (我们目前在用的边界) <a id="sec286"></a>

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


# 第 36 章  G3.0  建 mini-mall-order 空骨架 <a id="sec287"></a>

## 36.1  目标 <a id="sec288"></a>

跑通一个【最简】可启动 + 可注册 Nacos 的微服务壳子, 后续 CartItem / Orders / Seckill 都往这里塞。

## 36.2  文件结构图 <a id="sec289"></a>

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

## 36.3  Step 1 — 父 pom 解开 module 注释 <a id="sec290"></a>

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

## 36.4  Step 2 — `mini-mall-order/pom.xml` <a id="sec291"></a>

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

## 36.5  Step 3 — `application.yml` <a id="sec292"></a>

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

## 36.6  Step 4 — 启动类 `MiniMallOrderApplication.java` <a id="sec293"></a>

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

## 36.7  Step 5 — `HelloController.java` <a id="sec294"></a>

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

## 36.8  Step 6 — 编译 + 启动 + 验证 <a id="sec295"></a>

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

## 36.9  Step 7 — 启动日志关键 3 行 <a id="sec296"></a>

```
✓ nacos registry, DEFAULT_GROUP mini-mall-order 192.168.32.1:9003 register finished
✓ Started MiniMallOrderApplication in 11.204 seconds
✓ =========== mini-mall-order 启动成功 ===========
```

→ 第一行说明 Nacos 注册成功, Nacos 控制台 (http://127.0.0.1:8848/nacos , 账号 nacos/nacos) 服务列表里能看到 `mini-mall-order`。


---


# 第 37 章  G3.1  Category 搬到 product 服务 <a id="sec297"></a>

## 37.1  搬迁清单 <a id="sec298"></a>

| 文件 | 单体路径 | 微服务路径 |
|---|---|---|
| Entity | `com.minimall.minimall.entity.Category` | `com.minimall.product.entity.Category` |
| Mapper | `com.minimall.minimall.mapper.CategoryMapper` | `com.minimall.product.mapper.CategoryMapper` |
| Service 接口 | `com.minimall.minimall.service.ICategoryService` | `com.minimall.product.service.ICategoryService` |
| Service 实现 | `com.minimall.minimall.service.impl.CategoryServiceImpl` | `com.minimall.product.service.impl.CategoryServiceImpl` |
| Controller | `com.minimall.minimall.controller.CategoryController` | `com.minimall.product.controller.CategoryController` |

## 37.2  Entity 代码 + 解析 <a id="sec299"></a>

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

## 37.3  Mapper 代码 + 解析 <a id="sec300"></a>

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

## 37.4  Service 接口 + 实现 + 解析 <a id="sec301"></a>

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

## 37.5  Controller 代码 + 解析 <a id="sec302"></a>

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


# 第 38 章  G3.2  Address 搬到 user 服务 <a id="sec303"></a>

## 38.1  为什么这章是【整轮搬迁的重头戏】？ <a id="sec304"></a>

Category 是【无用户态】的全局数据 (所有人共享), 搬迁等于纯复制粘贴。

Address 是【与用户绑定】的数据, 涉及单体 → 微服务的【**上下文传递机制转换**】, 这是微服务架构的核心知识点。

## 38.2  ⭐ 核心对比：单体 vs 微服务的上下文传递 <a id="sec305"></a>

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

## 38.3  Entity / Mapper / Service / 实现 (4 个文件) — 与 Category 一样的套路 <a id="sec306"></a>

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

## 38.4  Controller 完整代码 + 逐段解析 <a id="sec307"></a>

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


# 第 39 章  G3.3  联调验证 12 步 <a id="sec308"></a>

## 39.1  网关路由配置补充 <a id="sec309"></a>

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

## 39.2  12 步 curl 验证清单 <a id="sec310"></a>

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

## 39.3  关键命令解析 <a id="sec311"></a>

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

## 39.4  跑通后的验证结果 <a id="sec312"></a>

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


# 第 40 章  MyBatis-Plus 在微服务里的角色解析 <a id="sec313"></a>

## 40.1  常见误解 vs 实际事实 <a id="sec314"></a>

| 误解 | 实际事实 |
|---|---|
| "用 MP CRUD 需要建 common-mybatis 公共模块" | ❌ 不需要。CRUD 是 `mybatis-plus-spring-boot3-starter` 给的, **starter 就是能力本身** |
| "公共模块 = 用得了 MP" | ❌ 反了。**有 starter 就能用 MP**, 公共模块只是"再封一层" |
| "建公共模块才能集中分页插件配置" | ✅ 这点对。但 3 行 Bean 散在 2 个服务也不痛 |

## 40.2  MP 在项目里的"分工图" <a id="sec315"></a>

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

## 40.3  Category/Address 5+5 接口 ↔ MP 方法 全映射 <a id="sec316"></a>

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

## 40.4  关于"分页插件"——什么时候才需要补？ <a id="sec317"></a>

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

## 40.5  关于"自动填充 createTime/updateTime" <a id="sec318"></a>

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


# 第 41 章  本轮章节地图 + 待办清单 <a id="sec319"></a>

## 41.1  v9 → v10 (本轮新增) 章节地图 <a id="sec320"></a>

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

## 41.2  待办清单 (按优先级) <a id="sec321"></a>

| 优先 | 任务 | 阻塞依赖 |
|---|---|---|
| ★★★ | **G1 接 Redis** | 阻塞 Favorite / CartItem / Orders / Seckill |
| ★★★ | **G3.4 CartItem → order** | 等 G1 |
| ★★ | G2 接 RabbitMQ | 阻塞 Orders 超时关闭 + Seckill 异步落单 |
| ★★ | G3.5 Orders → order | 等 G1 + G2 |
| ★ | G3.6 Seckill → order (最难) | 等 G1 + G2 + Lua |
| ★ | 顺手补 MybatisPlusConfig (分页插件) | 等真要做"列表分页"那一刻 |
| ★ | 顺手补 MetaObjectHandler (自动填时间) | 等跨数据库或讨厌 DB default 那一刻 |

## 41.3  能力清单 (到本轮结束) <a id="sec322"></a>

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


# 第 42 章  G1  Redis 接入 (order 服务先吃螃蟹) <a id="sec323"></a>

## 42.1  目标 + 设计原则 <a id="sec324"></a>

```
目标:
  在 mini-mall-order 服务接入 Redis, 跑通【最简】set/get/del 三接口
  + redis-cli 验证 value 是 JSON 不是二进制乱码

设计原则 (feedback_concrete_first):
  ❌ 不建 common-redis 公共模块 (虽然 task #54 规划过)
  ✅ 每个用 Redis 的服务自己写一份 RedisConfig
  ⏰ 等【3 个】服务都用了再抽公共模块
```

## 42.2  Redis 已在本机跑 <a id="sec325"></a>

```bash
> "$REDIS_CLI" -h 127.0.0.1 -p 6379 PING
PONG

> "$REDIS_CLI" -h 127.0.0.1 -p 6379 INFO server
redis_version:5.0.10
redis_mode:standalone
os:Windows
```

→ 本机 Windows Redis 5.0.10 单机模式, 没装 Docker 容器。

## 42.3  Step 1 — order/pom.xml 加 Redis starter <a id="sec326"></a>

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

## 42.4  Step 2 — order/application.yml 加 redis 配置 <a id="sec327"></a>

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

## 42.5  Step 3 — 写 RedisConfig (核心) <a id="sec328"></a>

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

## 42.6  Step 4 — 写 RedisTestController <a id="sec329"></a>

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

## 42.7  Step 5 — 联调验证 <a id="sec330"></a>

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


# 第 43 章  G3.5  Favorite 搬到 product 服务 (Redis Set 实战) <a id="sec331"></a>

## 43.1  搬迁清单 + 关键差异 <a id="sec332"></a>

| 文件 | 单体 | 微服务 |
|---|---|---|
| 接口 IFavoriteService | `com.minimall.minimall.service` | `com.minimall.product.service` |
| 实现 FavoriteServiceImpl | 同上 | `com.minimall.product.service.impl` |
| Controller | 同上 | `com.minimall.product.controller` |
| product/pom.xml | — | ⭐ 加 Redis starter (**第二份**) |
| product/RedisConfig | — | ⭐ 新建 (**第二份**, 跟 order 一样) |

⚠️ **没有 entity / mapper**——Favorite **数据全在 Redis**, 不沾 MySQL!

## 43.2  Redis 数据结构选 Set 的理由 <a id="sec333"></a>

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

## 43.3  product 接 Redis (第二份, feedback_concrete_first 实战) <a id="sec334"></a>

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

## 43.4  IFavoriteService 接口 <a id="sec335"></a>

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

## 43.5  FavoriteServiceImpl 核心代码 <a id="sec336"></a>

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

## 43.6  FavoriteController <a id="sec337"></a>

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

## 43.7  网关路由 + 验证 <a id="sec338"></a>

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


# 第 44 章  G3.4  CartItem 搬到 order (⭐ Feign 跨服务调用首次实战) <a id="sec339"></a>

## 44.1  这一章是【整轮搬迁的难度顶点】 <a id="sec340"></a>

| 维度 | Category/Address | Favorite | CartItem ⭐ |
|---|---|---|---|
| 存储 | MySQL | Redis Set | MySQL |
| 跨服务依赖 | 无 | 无 | **Feign 调 product** |
| 越权校验 | ✅ | 不需要 (Redis key 已隔离) | ✅ |
| 容错处理 | 简单 | 简单 | **Feign 失败要 fallback** |
| 拼装 VO | 单表 | 单表 + 本地 service | **跨服务 + 单表** |

## 44.2  改 7 个文件 + 改启动类 <a id="sec341"></a>

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

## 44.3  Step 1 — order/pom.xml 加 MP + MySQL <a id="sec342"></a>

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

## 44.4  Step 2 — order/application.yml 加 datasource <a id="sec343"></a>

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

## 44.5  Step 3 — 启动类加 @MapperScan + @EnableFeignClients <a id="sec344"></a>

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

## 44.6  Step 4 — 写 ProductFeignClient (核心) <a id="sec345"></a>

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

## 44.7  Step 5 — CartItemServiceImpl 核心改动 <a id="sec346"></a>

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

## 44.8  Step 6 — CartItemController <a id="sec347"></a>

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

## 44.9  Step 7 — 网关路由 + 验证 <a id="sec348"></a>

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


# 第 45 章  踩坑实录: 单体留下的 cart 唯一索引 bug <a id="sec349"></a>

## 45.1  现象 <a id="sec350"></a>

```bash
② POST /cart {"productId":2,"quantity":1}
→ {"code":500,"message":"系统繁忙","data":null}
```

但 ①③ POST 商品 1 完全正常, 怪。

## 45.2  排查日志 <a id="sec351"></a>

```
Caused by: java.sql.SQLIntegrityConstraintViolationException:
    Duplicate entry '1-2' for key 'cart_item.uk_user_product'
```

→ 数据库唯一约束 `(user_id, product_id)` 撞了。

## 45.3  根因 <a id="sec352"></a>

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

## 45.4  3 个修法 <a id="sec353"></a>

| 方案 | 改动 | 优 | 劣 |
|---|---|---|---|
| ① 改唯一索引 | `UNIQUE (user_id, product_id, is_deleted)` | 简单 | 删除/恢复多次累计逻辑删行 |
| ② addToCart 兼容老脏数据 | 查时不过滤 is_deleted, 找到删除的就反激活 (set is_deleted=0) | 不积累垃圾行 | 业务逻辑复杂 |
| ③ 改物理删除 | 删 @TableLogic, removeById 真删 | 最简单 | 失去"购物车历史" |

**生产推荐 ②** —— 这是真正符合"购物车"语义的设计。

## 45.5  本次绕过方法 (教学用) <a id="sec354"></a>

```sql
DELETE FROM cart_item WHERE id=17;   -- 物理删脏数据
-- 然后重试 ②, 一切正常
```

→ 这个修法**不在本轮搬迁范围**, 单体里也会炸——但**作为知识点必须记**, 这就是"搬迁过程中暴露老 bug"的真实写照。


---


# 第 46 章  本轮章节地图 + 累计能力 + 待办 <a id="sec355"></a>

## 46.1  v10 → v11 (本轮新增) 章节地图 <a id="sec356"></a>

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

## 46.2  累计能力清单 <a id="sec357"></a>

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

## 46.3  待办清单 (按优先级) <a id="sec358"></a>

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

## 46.4  搬迁实战经验沉淀 <a id="sec359"></a>

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

# Chapter 47 · G2 RabbitMQ 5 大核心概念 <a id="sec360"></a>

> 这一章把 RabbitMQ 的 5 个核心抽象**讲透**, 后面所有 MQ 代码都基于这 5 个概念展开.
> 学完能回答: "为啥不直接 Producer 怼 Consumer? 为啥要拐一道 Exchange?"

## 47.1  四个角色: Producer / Consumer / Broker / Channel <a id="sec361"></a>

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

## 47.2  Queue (队列): 排队的地方 <a id="sec362"></a>

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

## 47.3  Exchange (交换机): 路由器 <a id="sec363"></a>

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

## 47.4  RoutingKey (路由键): 消息的"地址标签" <a id="sec364"></a>

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

## 47.5  Binding (绑定): "RoutingKey → Queue" 的路由规则 <a id="sec365"></a>

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

## 47.6  四种 Exchange 类型 (路由"算法") <a id="sec366"></a>

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

# Chapter 48 · TTL + DLX 延迟队列 (单体最骚的设计) <a id="sec367"></a>

> **RabbitMQ 原生不支持"延迟 N 秒后发"**.
> 用 TTL (消息存活时间) + DLX (死信交换机) 拼出来这个效果, 是面试 + 真业务的高频考点.

## 48.1  为啥要延迟队列? (业务背景) <a id="sec368"></a>

**典型场景**: 订单 30 分钟未付款自动关闭.

❌ 朴素做法: 起一个定时任务每分钟扫一次 `orders` 表, 找出超时未付款的关.
- 问题 1: 浪费数据库 IO (大部分扫描都没结果)
- 问题 2: 关单延迟最大 1 分钟
- 问题 3: 任务节点挂了关单就停了

✅ RabbitMQ 方案: 创建订单时发一条**延迟 30 分钟**的消息, 30 分钟后这条消息自动出现在 close.queue, Listener 收到就关单.
- 0 轮询, 时间精确, 自然分布式.

## 48.2  原理 (TTL + DLX 二合一) <a id="sec369"></a>

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

## 48.3  代码 (args Map 配置) <a id="sec370"></a>

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

## 48.4  实测验证 (G2.6 联调数据) <a id="sec371"></a>

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

# Chapter 49 · G2 代码全集 + 详细解释 <a id="sec372"></a>

## 49.1  pom.xml — 一行 starter 拉一切 <a id="sec373"></a>

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

## 49.2  application.yml — 4 类配置 <a id="sec374"></a>

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

## 49.3  RabbitMQConfig.java — 3 组队列基建 <a id="sec375"></a>

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

## 49.4  MqTestController.java — Producer 三连 <a id="sec376"></a>

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

## 49.5  MqTestListener.java — Consumer 全套 <a id="sec377"></a>

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

# Chapter 50 · G2 踩坑实录 <a id="sec378"></a>

## 50.1  坑 ① Windows PATH 里 JDK 8 干扰 <a id="sec379"></a>

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

## 50.2  坑 ② Docker 容器 Paused 状态 <a id="sec380"></a>

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

## 50.3  坑 ③ guest/guest 不是万能默认 <a id="sec381"></a>

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

## 50.4  3 坑共通: 排查"启动失败"的标准动作 <a id="sec382"></a>

| 顺序 | 动作 | 命令 |
|---|---|---|
| ① 看进程在不在 | `Get-Process -Id $pid` | 死了 = 启动炸 |
| ② 看端口监听 | `netstat -ano \| findstr LISTENING \| findstr ":9003"` | 没监听 = 没起 |
| ③ 看应用日志 | `tail -50 order.log` 找 `Caused by` | 真错误根因 |
| ④ 看依赖服务 | `docker ps`, `redis-cli ping` 等 | 依赖挂了应用启不来 |
| ⑤ 检查 PATH/JAVA | `java -version`, `(Get-Command java).Source` | 跑错版本 |

---

# Chapter 51 · G2 累计能力清单 + 待办 <a id="sec383"></a>

## 51.1  G2 完成后能干啥 <a id="sec384"></a>

| 能力 | 怎么用 |
|---|---|
| 服务间异步通讯 | order 发 MQ, 别的服务监听 (后续阶段) |
| 订单延迟关单 | 创建订单时发 delay 消息, 30 分钟后自动关单 (G3.7 用) |
| 秒杀异步下单 | 扣库存后发 seckill 消息, Listener 慢慢生成订单 (G3.8 用) |
| 消息持久化 + 重启不丢 | `durable=true` 全开 |
| 手动 ACK + 失败处理 | catch 里 NACK, 业务侧决定 requeue / 丢弃 |
| 死信链路 | delay.queue + DLX → close.queue, 完整跑通 |

## 51.2  G2 没做但已铺路的 (后续阶段补) <a id="sec385"></a>

| 待办 | 何时做 |
|---|---|
| 真业务 Producer (`OrdersServiceImpl` 发 delay 消息) | G3.7 搬 Orders 时 |
| 真业务 Consumer (`OrderCloseListener` 真关单) | G3.7 |
| Seckill Producer/Consumer | G3.8 |
| 切 Jackson2JsonMessageConverter (发 POJO/JSON) | 真业务发 OrderEvent DTO 时 |
| Publisher confirms (确认 Broker 收到) | 生产环境必加 |
| 消费幂等 (避免重复消费导致重复关单) | G3.7 关单时加 status 判断 |

## 51.3  G2 决策沉淀 <a id="sec386"></a>

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

## 51.4  G2 搬迁经验沉淀 <a id="sec387"></a>

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

# Chapter 52 · G3.7 Orders 搬迁 - 总述 + 架构图 <a id="sec388"></a>

> 这一章是【MQ 真业务通路】+【订单状态机】+【跨服务事务】的实战.
> 学完能回答: "为啥 createOrder 比 Category 复杂 10 倍? MQ 怎么从 demo 走到真业务? 不扣库存怎么搞?"

## 52.1  G3.7 是啥, 凭啥这么重 <a id="sec389"></a>

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

## 52.2  端到端全景图 (服务交互) <a id="sec390"></a>

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

## 52.3  对比一下: Category (G3.1) vs Orders (G3.7) 的复杂度 <a id="sec391"></a>

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

# Chapter 53 · createOrder 9 步深度解析 <a id="sec392"></a>

> 这一章把 OrdersServiceImpl.createOrder() 这个【最复杂的方法】每一步讲透.
> 学完能回答: "为啥锁要包在事务外? 为啥 MQ 要在事务提交后发? 跨服务事务怎么办?"

## 53.1  全貌 (代码骨架) <a id="sec393"></a>

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

## 53.2  为啥锁要包在事务外? (经典面试题) <a id="sec394"></a>

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

## 53.3  为啥 MQ 要在事务提交后发? <a id="sec395"></a>

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

## 53.4  7 步业务流程逐一解析 <a id="sec396"></a>

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

# Chapter 54 · 真业务 MQ 通路 (G2 延伸到这里才闭环) <a id="sec397"></a>

## 54.1  从 demo 走到真业务 <a id="sec398"></a>

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

## 54.2  closeOrderByMQ 的幂等 (核心代码 + 解析) <a id="sec399"></a>

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

## 54.3  Listener 完整代码 + 解析 <a id="sec400"></a>

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

# Chapter 55 · G3.7 代码全集 <a id="sec401"></a>

## 55.1  17 个新文件 (按职责分类) <a id="sec402"></a>

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

## 55.2  跨服务调用矩阵 <a id="sec403"></a>

| 谁调谁 | 干啥 | Feign 接口 | 返回类型 |
|---|---|---|---|
| order → user | 查地址 | UserFeignClient.getAddress(id, userId) | `Result<Map<String, Object>>` |
| order → product | 查商品 | ProductFeignClient.getById(id) | `Result<Map<String, Object>>` |
| MQ → OrderCloseListener | 关单触发 | (RabbitMQ 投递) | (Long orderId) |

## 55.3  状态机迁移 (5 态 + 3 路径) <a id="sec404"></a>

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

# Chapter 56 · G3.7 踩坑实录 + 累计能力 + 待办 <a id="sec405"></a>

## 56.1  踩坑 ① PowerShell + curl + 中文 → UTF-8 解析炸 <a id="sec406"></a>

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

## 56.2  踩坑 ② yml 全局 logic-delete-field 反扑 cart bug <a id="sec407"></a>

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

## 56.3  G3.7 累计能力清单 <a id="sec408"></a>

| 能力 | 怎么用 |
|---|---|
| ⭐ MQ 真业务通路 | createOrder 发延迟 + OrderCloseListener 收 + 改 DB |
| ⭐ 分布式锁防重复提交 | RedisLockUtil + try/finally + 锁外事务 |
| ⭐ 事务边界精控 | TransactionTemplate.execute lambda + 事务外发 MQ |
| ⭐ 跨服务 Feign 调 user/product | UserFeignClient + ProductFeignClient |
| ⭐ 完整订单状态机 | 5 态 + 3 迁移 + 幂等保护 |
| 商品/地址快照 | 下单瞬间冻结, 后续变化不影响历史 |

## 56.4  G3.7 没做但已铺路的 (后续) <a id="sec409"></a>

| 待办 | 何时做 |
|---|---|
| 扣库存 (跨服务事务) | G4 引入 Seata / TCC / Saga |
| 给 product 加 batch 接口 (替代 N 次 Feign) | 性能优化时 |
| 全局 Feign RequestInterceptor (统一转发 X-User-Id) | 出现第 3 个 Feign Client 时 |
| OrderTimeoutTask 兜底定时任务 | MQ 万一漏掉时的保险, 真生产推荐加 |
| 支付集成 (微信/支付宝) | 真业务才做, 教学跳过 |

## 56.5  G3.7 决策沉淀 <a id="sec410"></a>

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

## 56.6  G3.7 搬迁经验沉淀 <a id="sec411"></a>

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

# Chapter 57 · G3.8 Seckill 搬迁 - 总述 + 架构图 <a id="sec412"></a>

> 这一章是【Lua 原子操作】+【MQ 异步下单】+【前端轮询】的实战.
> 学完能回答: "为啥秒杀要 Lua? 抢到不直接写库为啥还要 MQ? 轮询怎么知道生成了没?"

## 57.1  G3.8 是啥, 它解决的问题 <a id="sec413"></a>

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

## 57.2  端到端架构图 <a id="sec414"></a>

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

## 57.3  vs G3.7 Orders 复杂度对比 <a id="sec415"></a>

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

# Chapter 58 · Lua 脚本深度解析 (G3.8 最炫的 5 行) <a id="sec416"></a>

> 这一章把 `seckill_stock.lua` 5 行【逐行讲透】.
> 学完能回答: "为啥不能用 Java 代码做这件事? Lua 多原子? EVALSHA 是啥?"

## 58.1  为啥要 Lua? 朴素 Java 做不到啥? <a id="sec417"></a>

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

## 58.2  5 行 Lua 逐行讲 <a id="sec418"></a>

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

## 58.3  KEYS 和 ARGV 的设计为啥分开? <a id="sec419"></a>

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

## 58.4  返回值约定 (面试高频) <a id="sec420"></a>

| Lua 返回 | 含义 | service 层处理 |
|---|---|---|
| **1** | 抢到了 | 发 MQ + 返"抢购成功" |
| **0** | 已售罄 | 抛 400 已售罄 |
| **-1** | 已参与过 | 抛 400 您已参与 |
| **-2** | 未预热 | 抛 400 活动未开始 |

为啥用 `-1 / -2` 表示错误而不是 throw? Lua 错误传递成本高, 数字返回最简单.

## 58.5  Spring 怎么注册 Lua 脚本 (EVALSHA 优化) <a id="sec421"></a>

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

# Chapter 59 · MQ 异步下单 + 轮询 3 态查询 <a id="sec422"></a>

## 59.1  为啥 Lua 抢到了不直接写 DB? <a id="sec423"></a>

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

## 59.2  消息体设计 "activityId:userId" <a id="sec424"></a>

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

## 59.3  幂等设计: SeckillOrderListener 的核心 <a id="sec425"></a>

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

## 59.4  3 态轮询查询 (前端用户体验关键) <a id="sec426"></a>

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

# Chapter 60 · G3.8 代码全集 <a id="sec427"></a>

## 60.1  11 个新文件 + 1 Lua 资源 (按职责分类) <a id="sec428"></a>

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

## 60.2  Lua + Java 调用 (核心 5 行代码) <a id="sec429"></a>

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

## 60.3  跨服务调用矩阵 (vs G3.7 比) <a id="sec430"></a>

| 谁调谁 | 干啥 | G3.7 | G3.8 |
|---|---|---|---|
| order → product | 查商品 | 用了 (createOrder 算总价) | 用了 (publishActivity 校验 + listActivities 拿原价) |
| order → user | 查地址 | 用了 | **没用** (秒杀订单不要地址) |
| order → Redis Lua | 原子操作 | 没用 | **核心** |
| order → MQ | 异步事件 | 延迟关单 (TTL+DLX) | 即时下单 (普通 queue) |

## 60.4  端到端实测时间线 (G3.8.9 真实数据) <a id="sec431"></a>

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

# Chapter 61 · G3.8 决策 + 累计能力 + 待办 <a id="sec432"></a>

## 61.1  G3.8 关键决策 <a id="sec433"></a>

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

## 61.2  G3.8 累计能力 <a id="sec434"></a>

| 能力 | 怎么用 |
|---|---|
| ⭐ Lua 原子操作 | 高并发抢购 / 限流 / 计数等场景的标准方案 |
| ⭐ MQ 削峰填谷 | 突发流量场景 (秒杀/抢券/活动通知) |
| ⭐ 异步下单 + 轮询 | 任何"长操作 + 用户等待"的场景 (大文件上传, AI 任务等) |
| Redis 库存懒加载 | 把 DB 库存映射到 Redis 时用 |
| @Bean 化 Lua 脚本 | EVALSHA 优化, 启动时一次性加载 |
| 跨服务调用 (Feign 调 product) | 跟 G3.7 一致 |

## 61.3  G3.8 没做但已铺路的 (后续) <a id="sec447"></a>

| 待办 | 何时做 |
|---|---|
| 高并发压测 (10000 并发) | 验证 Lua 真原子 + MQ 削峰效果 |
| seckill_order 加 (user_id, activity_id) UNIQUE | 终极幂等防线 |
| 库存非懒加载, 改 activity 启动时定时灌 | 避免首次 race |
| Lua 脚本扩展: 限速 (令牌桶), 风控 | 防机器人 |
| 真支付接入 (微信/支付宝) | 秒杀订单走真支付 |
| 秒杀订单超时关单 | 跟 G3.7 同款 delay+DLX |
| seckill_order 用普通 Orders 表 | 而不是单独一张 (架构简化讨论) |

## 61.4  G3.8 搬迁经验沉淀 <a id="sec448"></a>

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

# 62. G3.9 — 补 Product 模块缺失的 5 个方法 <a id="sec449"></a>

## 62.1  背景: 全面 diff 发现的洞 <a id="sec450"></a>

G 阶段搬完后做了一次单体 vs 微服务**最严谨的全面对比**, 发现 Product 服务**只搬了壳**:

| 单体 IProductService | 微服务 IProductService |
|---|---|
| 5 个业务方法 (getProductDetail / updateProduct / deleteProduct / searchProducts / getHotSearch) | **0 个** (空接口) |

| 单体 ProductController | 微服务 ProductController |
|---|---|
| 6 端点 (分页搜+详情+POST/PUT/DELETE+热搜) | 3 端点 (getById + list 前 10 条 + flaky) |

**意味着**: 当前微服务版用户**根本搜不了商品**, 只能 `getById(123)` 一个个看. 这是核心电商功能的断点.

## 62.2  搬的 5 个方法逐个解释 <a id="sec451"></a>

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

## 62.3  MybatisPlusConfig (分页插件) <a id="sec452"></a>

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

## 62.4  G3.9 大坑: MP 3.5.9 把分页插件拆包了 <a id="sec453"></a>

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

## 62.5  G3.9 第二个大坑: Jackson 默认不会序列化 LocalDateTime <a id="sec454"></a>

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

## 62.6  G3.9 累计能力 <a id="sec455"></a>

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

# 63. G3.10 — 补扣库存断点 (业务真闭环) <a id="sec456"></a>

## 63.1  背景: G3.7 留的债 <a id="sec457"></a>

读 G3.7 搬过来的 OrdersServiceImpl, 类注释明写:

```java
// ④ productMapper.deductStock / restoreStock 全部去掉
//    = 简化版【不扣库存】, 后续 G4 上分布式事务再补
```

**实际状态**: 用户下 100 单也不扣 1 件库存. 业务有断点.

但 product 表的 stock 字段还在, ProductMapper 的 deductStock/restoreStock SQL 也搬过来了, **缺的只是 order 跨服务的调用**.

## 63.2  方案: order 通过 Feign 调 product 扣库存 <a id="sec458"></a>

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

## 63.3  product 服务暴露 2 个内部端点 <a id="sec459"></a>

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

## 63.4  ProductFeignClient 加 2 方法 <a id="sec460"></a>

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

## 63.5  createOrder 加扣库存循环 <a id="sec461"></a>

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

## 63.6  cancelOrder + closeOrderByMQ 加回库存 <a id="sec462"></a>

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

## 63.7  原子扣库存 SQL 的一致性 <a id="sec463"></a>

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

## 63.8  G3.10 累计能力 <a id="sec464"></a>

| 维度 | 状态 |
|---|---|
| 下单真扣库存 | ✅ |
| 取消订单 / 自动关单 回库存 | ✅ |
| 库存不足拒绝下单 | ✅ |
| 单 product 内部强一致 | ✅ (SQL 守卫) |
| 跨服务事务一致性 | ❌ (留 G5 Seata 解) |

---

# 64. H1 — Feign Fallback 服务降级 <a id="sec465"></a>

## 64.1  问题: 下游挂了, 上游裸抛 5xx <a id="sec466"></a>

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

## 64.2  Feign Fallback 是什么 <a id="sec467"></a>

为 @FeignClient 接口注册一个**实现类**, 当 Feign 调用失败时, **Spring 自动回退到这个实现类**返兜底响应:

```
正常: order → Feign 代理 → HTTP 请求 → product 返 200 → 拿到数据
降级: order → Feign 代理 → 调用失败 → 调 Fallback Bean → 返兜底 Result
                                       ↑
                                  Sentinel 接管
```

## 64.3  3 个 Fallback 类全部代码 <a id="sec468"></a>

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

## 64.4  关键开关: feign.sentinel.enabled <a id="sec469"></a>

```yaml
feign:
  sentinel:
    enabled: true
```

**没这开关**: `@FeignClient(fallback=...)` **写了不生效**, 仍抛 FeignException.

**有这开关**: Spring Cloud Alibaba 用 `SentinelFeign` 替换默认 Feign 工厂, 把 Sentinel 接进 Feign 的代理过程, **Sentinel 触发熔断时调 fallback**.

注意: yml 顶级 `feign:`, **不是 `spring.cloud.openfeign.feign:`** (这是 SCA 历史包袱, 没改).

## 64.5  验证: 杀 product, 看 fallback 日志 <a id="sec470"></a>

```bash
$ curl http://127.0.0.1:9001/user/1/with-product/1
{"code":500,"message":"商品服务暂不可用","data":null}    ← message 来自 fallback
```

user 服务日志:

```
WARN c.m.u.c.f.ProductFeignClientFallback : [Feign-Fallback] (user→product).getById 降级 id=1
```

确认 Fallback **被自动调用**了, 链路完整.

## 64.6  H1 累计能力 <a id="sec471"></a>

| 场景 | 原状 (H1 前) | 现状 (H1 后) |
|---|---|---|
| product 挂 → 调演示接口 | 抛 FeignException → 500 | fallback → "商品服务暂不可用" |
| product 挂 → 创建订单 | 整个 createOrder 抛异常 | fallback → 友好提示, 不下单 |
| product 挂 → 取消订单回库存 | cancel 跟着炸 | fallback log warn, cancel 继续 |
| user 挂 → 创建订单查地址 | 抛 FeignException | fallback → "用户服务暂不可用" |

---

# 65. H2 — 过时注释纠错 (小但重要) <a id="sec472"></a>

## 65.1  背景 <a id="sec473"></a>

G3.10 已经补完扣库存, H1 已经加 fallback, **但** OrdersServiceImpl 类顶部的注释还是 G3.7 时的:

```java
// 订单服务实现 (G3.7 - 微服务版, 简化版不扣库存)
// ④ productMapper.deductStock / restoreStock 全部去掉
//    = 简化版【不扣库存】, 后续 G4 上分布式事务再补
```

读者看了**会以为还没扣**, 跟代码冲突. **代码跟注释脱节** 是新人接手项目最常见的坑.

## 65.2  改成准确版 <a id="sec474"></a>

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

## 65.3  H2 经验 <a id="sec475"></a>

| 经验 | 解释 |
|---|---|
| 注释要跟代码同步, 不然误导维护者 | 班级里"代码即文档" 的现实版 |
| 每次跨阶段改造, 类顶部的"故事"也要更新 | 否则就是技术债 |

---

# 66. G5 — Seata AT 分布式事务 <a id="sec476"></a>

## 66.1  问题再现: 单体的招在微服务为啥失灵 <a id="sec477"></a>

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

## 66.2  Seata AT 模式核心原理 <a id="sec478"></a>

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

## 66.3  Seata Server 部署 (Docker file 模式) <a id="sec479"></a>

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

## 66.4  客户端依赖 (BOM 锁版本) <a id="sec480"></a>

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

## 66.5  客户端 yml 配置 <a id="sec481"></a>

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

## 66.6  @GlobalTransactional 注解 <a id="sec482"></a>

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

## 66.7  SeataTestController (端到端验证神器) <a id="sec483"></a>

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

## 66.8  端到端验证 5 步 <a id="sec484"></a>

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

## 66.9  XID 怎么从 order 透传到 product <a id="sec485"></a>

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

## 66.10  G5 大坑回顾 <a id="sec486"></a>

| 坑 | 表现 | 解 |
|---|---|---|
| Spring Boot 3 + Seata WARN BeanPostProcessorChecker | 启动有一堆 WARN | 已知问题, 不影响功能, 忽略 |
| 配 `tx-service-group` 写错 | 启动报"can not get available servers" | 跟 yml 的 `vgroup-mapping` 必须键对得上 |
| undo_log 没建 | RM 启动报 "Table undo_log doesn't exist" | 每个业务库 (mini_mall) 都要建 |
| Seata 容器 IP 是 docker 内 IP | XID 里能看见 172.17.0.4 | 客户端连 127.0.0.1:8091 即可, XID 内含的 IP 不影响 |
| @GlobalTransactional 加在 service 内部方法 | 同类自调用不生效 | 必须加在 Controller 或 Service 公开入口方法 |

## 66.11  AT vs TCC vs SAGA 简表 (面试问到) <a id="sec487"></a>

| 模式 | 原理 | 业务侵入 | 适用 |
|---|---|---|---|
| **AT** | 框架自动写 undo_log, 二阶段反推 SQL 回滚 | 0 (透明) | 一般 CRUD 业务, 我们就用这个 |
| **TCC** | 业务写 try / confirm / cancel 3 个方法 | 高 (3 倍代码) | 强一致 + 跨非 DB 资源 (如调外部支付) |
| **SAGA** | 长事务拆成多段, 每段定补偿动作 | 中 | 长流程 (旅游订单 N 个步骤) |
| **XA** | 二阶段提交协议, 资源管理器原生支持 | 0 | 性能差 (锁表整个二阶段) |

**简历可以聊**: 选 AT 是因为 "业务无侵入 + 性能合理 + 框架成熟 + 单库内的强一致" 综合最优.

## 66.12  G5 累计能力 <a id="sec488"></a>

| 维度 | 状态 |
|---|---|
| 跨服务事务一致性 | ✅ (Seata AT) |
| order 抛异常自动回滚 product 库存 | ✅ (rollback status: Rollbacked) |
| undo_log 自动清理 | ✅ |
| XID 自动透传 (Feign) | ✅ |
| 业务代码零侵入 (只加 @GlobalTransactional) | ✅ |
| Seata Console UI | ✅ http://127.0.0.1:7091 |

## 66.13  G5 经验沉淀 <a id="sec489"></a>

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

# 67. G6 — 物流 / 签收 (开篇 + 状态机图) <a id="sec490"></a>

## 67.1  G6 是啥, 凭啥做 <a id="sec491"></a>

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

## 67.2  状态机文字版图 <a id="sec492"></a>

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

## 67.3  G6 整体决策 (3 个关键) <a id="sec493"></a>

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

# 68. 有限状态机 FSM 工程实现 <a id="sec494"></a>

## 68.1  什么是有限状态机 (FSM) <a id="sec495"></a>

正式定义: 一个系统在任意时刻只能处于**有限个状态**之一, 状态间通过**事件**触发**有规则的**转换.

举几个例子帮记:
- 红绿灯: 红 → 绿 → 黄 → 红 (循环), 不能跳过.
- 订单: 上面那张图.
- TCP 连接: CLOSED → SYN_SENT → ESTABLISHED → ... 一共 11 个状态.

## 68.2  FSM 在工程里就是 4 步前置校验 <a id="sec496"></a>

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

## 68.3  为啥要 `.equals` 不用 `==` <a id="sec497"></a>

```java
if (!order.getStatus().equals(OrderStatus.PAID)) { ... }   // ✅ 推荐
if (order.getStatus() != OrderStatus.UNPAID) { ... }       // ⚠ 当前能跑
```

- `OrderStatus.PAID` 是 `byte` 字面量 (=1).
- `order.getStatus()` 是 `Byte` 包装类对象 (MP entity 默认包装类).
- 用 `==` / `!=`: Byte 会自动拆箱再比, 当前能跑.
- **但**如果哪天有人把常量类型改成 `Integer`, `==` 就变成**引用比较**, 100% 出 bug.
- 所以**好习惯就是用 `.equals`**, 不依赖自动拆箱.

## 68.4  G6 用 FSM 的 2 个真实例子 <a id="sec498"></a>

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

## 68.5  ship vs sign 差异对照表 (理解为啥这么设计) <a id="sec499"></a>

| 区别点 | shipOrder | signOrder | 为啥不同 |
|--------|-----------|-----------|---------|
| userId 参数 | ❌ 没有 | ✅ 有 | admin 操作不需要锁定用户 |
| 越权校验 | ❌ 跳过 | ✅ 必须做 | 用户不能签别人的单 |
| 前置状态 | PAID (1) | SHIPPED (2) | 状态机方向 |
| 改成状态 | SHIPPED (2) | COMPLETED (3) | 状态机方向 |
| 时间字段 | shipTime | finishTime | 业务语义 |
| 物流字段 | 填 2 个 | 不填 | 发货才有物流 |

## 68.6  状态机要不要上 Spring StateMachine? <a id="sec500"></a>

**不上!** 太重了.

- Spring StateMachine 是个完整框架, 自带 DSL 配置 + 持久化 + 监听器.
- 我们这套订单状态机才 5 个状态, 4 步前置校验就够.
- 上了反而难维护. 上 StateMachine 的合理场景: 状态数 >= 10 + 转换关系复杂 + 需要可视化.

简历讲法: "评估过 Spring StateMachine, 当前状态规模 (5 状态 4 转换) 不需要, 用 4 步前置校验即可."

---

# 69. @Scheduled 定时任务深度解析 <a id="sec501"></a>

## 69.1  Spring 定时调度 3 件套 <a id="sec502"></a>

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

## 69.2  cron 表达式 6 字段速记 <a id="sec503"></a>

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

## 69.3  @Scheduled 4 种触发方式 <a id="sec504"></a>

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

## 69.4  单机调度的致命局限 ⚠️ <a id="sec505"></a>

```
order-service 实例1 (跑定时) ┐
order-service 实例2 (跑定时) ┤── 3 个实例都到点了同时跑
order-service 实例3 (跑定时) ┘
                              ↓
                同一个订单被签收 3 次! finishTime 被改 3 次!
```

**当前**: 微服务只跑 1 个 order 实例 → 没事.
**生产**: 多实例必须配合 **互斥机制**, 否则数据乱.

## 69.5  分布式调度 3 大方案 (面试常问) <a id="sec506"></a>

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

# 70. 单体 MQ 延迟队列 vs 微服务 @Scheduled (选型对比) ⭐⭐⭐ <a id="sec507"></a>

这一章是 G6 教学价值最高的部分, 简历可以专门讲这个对比.

## 70.1  两个场景, 选型截然不同 <a id="sec508"></a>

| 场景 | 用啥 | 落地代码 |
|------|------|---------|
| **下单 30 分钟没付款 → 关单** | RabbitMQ 延迟队列 (TTL+DLX) | `OrderCloseListener` (G3.7 已搬) |
| **发货 7 天没签收 → 自动签收** | Spring `@Scheduled` 扫表 | `LogisticsScheduledTask` (G6.7 刚写) |

## 70.2  选型决策表 <a id="sec509"></a>

| 维度 | MQ 延迟队列 | @Scheduled 扫表 |
|------|-----------|----------------|
| 触发精度 | **秒级精准** (每订单独立 timer) | 看 cron, 我们 1 小时一次 |
| 性能 | O(1) 每订单 | O(N) 每次扫全表 |
| 适用延迟 | 短 (秒~分钟级) | 长 (天级) |
| 业务规模 | 海量订单 | 状态批量推进 |
| 实现复杂度 | 中 (RabbitConfig + Listener + TTL 配置) | 低 (1 个方法 + cron 注解) |
| 用错代价 | 长延迟会堆百万消息在 MQ | 短延迟会被扫表性能拖垮 |

## 70.3  为啥 30 分钟关单选 MQ? <a id="sec510"></a>

**业务要求**:
- 用户体验: 倒计时一到必须立刻关单 (前端会显示倒计时).
- 30 分钟差 5 秒, 用户看到的是 "倒计时到 0 了, 我还能付款" → 灾难.
- 海量订单: 双 11 期间 10 万订单同时倒计时, 不能扫表.

**MQ 延迟队列设计**:
- 下单时发一条"30 分钟后关单"的消息到 DLX 队列.
- TTL 一到, RabbitMQ 自动转发到主队列, Listener 消费.
- 每条订单一个独立 timer, 互不影响.
- 哪怕单消息处理慢, 别的订单照常关.

## 70.4  为啥 7 天签收选 @Scheduled? <a id="sec511"></a>

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

## 70.5  错配的代价 <a id="sec512"></a>

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

## 70.6  万能选型法则 (记住这 3 句话) <a id="sec513"></a>

1. **要精准 + 短延迟 → MQ 延迟队列**, 每事件独立 timer.
2. **不要精准 + 长延迟 → @Scheduled 扫表**, 批量推进状态.
3. **不要精准 + 短延迟 → @Scheduled (fixedDelay)**, 比 cron 简单.

**简历讲法**: "评估过两种方案. 30 分钟关单用 MQ 延迟队列 (倒计时业务要求秒级精准), 7 天签收用 @Scheduled (长延迟+批量推进, MQ 反而堆积). 关键看`延迟长短`和`精度要求`."

## 70.7  单体里这件事的真相 <a id="sec514"></a>

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

# 71. G6 代码全集 + 解释 <a id="sec515"></a>

## 71.1  改了哪些文件 (一图速览) <a id="sec516"></a>

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

## 71.2  SQL DDL <a id="sec517"></a>

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

## 71.3  Orders entity 加字段 <a id="sec518"></a>

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

## 71.4  ShipOrderDTO (NEW) <a id="sec519"></a>

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

## 71.5  OrderDetailVO 加 2 字段 <a id="sec520"></a>

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

## 71.6  IOrdersService 接口 <a id="sec521"></a>

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

## 71.7  OrdersServiceImpl 实现 (已在 68.4 节展示, 此处略) <a id="sec522"></a>

详见 68.4 的 shipOrder + signOrder 完整代码.

## 71.8  OrdersController 加端点 <a id="sec523"></a>

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

## 71.9  启动类加 @EnableScheduling <a id="sec524"></a>

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

## 71.10  LogisticsScheduledTask (最炫的一段) <a id="sec525"></a>

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

# 72. G6 踩坑实录 + 累计能力 + 待办 <a id="sec526"></a>

## 72.1  坑 ① Seata AOP 把 BusinessException 包成了 RuntimeException ⭐⭐⭐ <a id="sec527"></a>

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

## 72.2  坑 ② Controller 路径前缀重复 <a id="sec528"></a>

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

## 72.3  坑 ③ @PathVariable / @RequestHeader 用错 <a id="sec529"></a>

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

## 72.4  坑 ④ Java 8 path 干扰 (重复出现) <a id="sec530"></a>

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

## 72.5  G6 端到端验证完整记录 <a id="sec531"></a>

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

## 72.6  G6 完成后能干啥 <a id="sec532"></a>

- ✅ 状态机的工程实现 (4 步前置校验模板)
- ✅ Spring @Scheduled 调度 + cron 表达式
- ✅ MQ 延迟队列 vs @Scheduled 选型 (业务核心能力)
- ✅ 单机调度的局限 + 3 种分布式调度方案对比
- ✅ Seata + 全局异常处理器的兼容性陷阱及修复方法
- ✅ BeanUtils.copyProperties 字段名匹配的自动拷贝能力
- ✅ DTO + @Valid + @NotBlank 校验闭环

## 72.7  G6 决策沉淀 <a id="sec533"></a>

| 决策 | 选什么 | 为啥 |
|------|--------|------|
| 物流表结构 | 加 2 列, 不独立表 | N=1, 符合 feedback_concrete_first |
| 物流查询 | 走详情接口, 不单独端点 | 业务能复用就复用, 别为对称造端点 |
| 7 天签收方案 | @Scheduled, 不 MQ | 长延迟 + 不精准 |
| 状态机 | 4 步前置校验, 不 Spring StateMachine | 状态规模小不需要 |
| 单机调度 | 暂用单机, 注释 TODO | 当前 1 实例没必要分布式 |
| BusinessException 解包 | 兜底 handler 递归 cause | 通用方案, 自动惠及所有 @GlobalTransactional |

## 72.8  G6 没做但已铺路的 (后续阶段补) <a id="sec534"></a>

| 待办 | 啥时候做 | 怎么做 |
|------|---------|--------|
| admin 网关 + RBAC | G10 后台 | shipOrder 端点应该挂 admin 后面 + 角色鉴权 |
| 分布式锁定时任务 | order 多实例时 | LogisticsScheduledTask 第一步 SETNX Redis 锁 |
| 物流轨迹查询 | 真接物流公司 | 接顺丰/中通 API 拉轨迹, 现在物流公司只是字符串 |
| 用户端倒计时 UI | 前端 | shipTime 给前端, 计算 "还剩 X 天自动签收" |

## 72.9  累计 G6 关键文件 <a id="sec535"></a>

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

# 73. G7 商品评价 (mini-mall-review, 9004) <a id="sec536"></a>

> 单体 mini-mall 没做评价, 微服务版【独有】. 这是第一次"无单体参考"完全从零写的服务.

## 73.1  G7 目标 & 设计决策 <a id="sec537"></a>

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

## 73.2  G7.1 SQL DDL (踩坑: 存储过程报错) <a id="sec538"></a>

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

## 73.3  G7.2~G7.6 模块骨架 + 业务实现 <a id="sec539"></a>

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

## 73.4  ⭐ 业务规则编排 - 4 步校验链 <a id="sec540"></a>

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

## 73.5  ⭐⭐ G7.E2E 踩的最关键的坑 - 跨服务事务可见性 <a id="sec541"></a>

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

## 73.6  Cache Aside 写策略 (顺手实现) <a id="sec542"></a>

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

## 73.7  G7 完整端到端验证记录 <a id="sec543"></a>

| 测试 | 期望 | 实际 |
|---|---|---|
| 创建评价 (合规) | 成功 + 评分回写 | data=2, product.avg=5.0 ✓ |
| 查商品评价列表 (公开) | 返列表 | 1 条 5 星 ✓ |
| 查我的评价 | 返列表 | 1 条 ✓ |
| 重复评价 | 被拒 | "该商品已评价过, 请勿重复" ✓ |
| 用 PAID(1) 订单评价 | 被拒 | "只有已完成订单可评价" ✓ |
| rating=10 超范围 | 400 | "rating 评分最多 5 星" ✓ |

## 73.8  常见踩坑速查 <a id="sec544"></a>

| 坑 | 表现 | 原因 | 修 |
|---|---|---|---|
| **跨事务可见性** | refreshRating 把 avg 刷成 0 | T2 Feign 早于 T6 commit | afterCommit 回调 |
| Java 8 PATH 干扰 | java -jar 启不动 SB3 | which java → Java 8 | 用 D:\jdk-21.0.11\bin\java |
| Git Bash 中文 JSON | 500 Invalid UTF-8 0xba | GBK→UTF-8 转换错 | 测试用英文 content |
| 网关白名单 startsWith 匹配 | POST /review 也被白名单放 | "/review/product" 前缀也匹配不到 POST /review | 路径分隔够清晰即可 |
| product entity 缺字段 | UPDATE 不动 avg_rating | 加了 SQL 列没同步 Java | Product.java 同步加字段 |

## 73.9  N=3 决策回顾 - 为啥还没抽 common-redis <a id="sec545"></a>

按 `feedback_concrete_first` 严格 N=3 阈值:

| 服务 | 用 Redis? |
|---|---|
| order | ✅ (G1.3 RedisConfig) |
| product | ✅ (G3.5.3 RedisConfig) |
| review | ❌ 暂无缓存需求 |

**N=2, 暂不抽**. 等 review 真出现"热门商品评价列表"等缓存场景时 (那时 N=3), 再抽 common-redis 才自然.

## 73.10  G7 累计文件 <a id="sec546"></a>

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

## 73.11  G7 教学速查 - 4 个零散知识点补全 <a id="sec547"></a>

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

# 74. G8 优惠券 (跨服务用券抵扣) <a id="sec548"></a>

## 74.1  G8 目标 & 设计决策 <a id="sec549"></a>

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

## 74.2  G8 核心业务流 <a id="sec550"></a>

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

## 74.3  ⭐ 教学要点 - 3 处 CAS 思想 <a id="sec551"></a>

G8 写了 3 处 "UPDATE ... WHERE 条件" 实现乐观锁:

| 位置 | SQL | 防什么 |
|---|---|---|
| `coupon.deductRemainStock` | `UPDATE coupon SET remain=remain-1 WHERE remain>0` | 超发 (两人同抢最后 1 张) |
| `user_coupon.useCoupon` | `UPDATE SET status=1 WHERE status=0` | 同张券被两笔订单都用 |
| (G3.10) `product.deductStock` | `UPDATE SET stock=stock-? WHERE stock>=?` | 库存超卖 |

口诀: **"高并发安全 = WHERE 加状态条件 + 看 rows==1"**.

## 74.4  ⭐⭐ 教学限制 - user 没接 Seata 的脏数据风险 <a id="sec552"></a>

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

## 74.5  G8 端到端验证记录 (7 项全过) <a id="sec553"></a>

| 测试 | 期望 | 实际 |
|---|---|---|
| 列可领券 (公开) | 2 张 | ✅ |
| 领券 → DB | user_coupon 新行 + remain 100→99 | ✅ |
| 我的券 + expired 标志 | 显示模板信息 + 是否过期 | ✅ |
| ⭐ 下单抵扣 | 5999 → 5989 (减 10), status=1 | ✅ |
| ⭐ 取消订单退券 | status 1→0, order_id NULL | ✅ |
| 回库存 | product.stock 还原 | ✅ |
| 重复领券拦截 | "您已领取过该券" | ✅ |

## 74.6  G8 踩的小坑速查 <a id="sec554"></a>

| 坑 | 表现 | 原因 | 修 |
|---|---|---|---|
| Windows mysql cli 中文 INSERT | `ERROR 1366 Incorrect string value '\xA1100'` | mysql cli 默认 GBK | 加 `--default-character-set=utf8mb4` + `SET NAMES utf8mb4` |
| 网关 404 找不到 /coupon/** | 加了路由还是 404 | gateway 没重启 jar | 改 yml = 必须 jar 重打+重启 (yml 打进 jar) |
| Git Bash 中文 JSON body | `Invalid UTF-8 0xba` | curl 命令行 GBK→UTF-8 转换错 | 测试用英文 content |
| 列名编译期对不上 | 编译过运行 Where 不命中 | 用 QueryWrapper 字符串 | 改 LambdaQueryWrapper + 方法引用 |

## 74.7  G8 教学速查 - 新出现的 4 个 API <a id="sec555"></a>

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

## 74.8  G8 累计文件 <a id="sec556"></a>

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


# 75. G9 - ES 商品搜索服务 (mini-mall-search) <a id="sec557"></a>

> 端口 9005, 服务名 `mini-mall-search`. 从 0 建一个新微服务模块, 把 product 数据灌进 ES 给前端搜.

## 75.0 总览 - G9 干了啥 <a id="sec558"></a>

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

## 75.1 mini-mall-search 模块脚手架 <a id="sec559"></a>

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

## 75.2 ProductDocument - ES 索引映射类 <a id="sec560"></a>

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

## 75.3 DTO / VO / 数据载体三剑客 <a id="sec561"></a>

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

## 75.4 ⭐ 微服务铁律 - ProductSource + Feign <a id="sec562"></a>

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

## 75.5 Spring Data ES Repository - ES 版的 MyBatis-Plus <a id="sec563"></a>

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

## 75.6 IProductSearchService 接口 + 实现分离 <a id="sec564"></a>

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

## 75.7 ProductSearchServiceImpl - sync 系列 (写 ES) <a id="sec565"></a>

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

## 75.8 ⭐ search 方法 - G9 最难一步 (读 ES) <a id="sec566"></a>

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

## 75.9 ProductSearchController + 网关路由 <a id="sec567"></a>

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

## 75.10 端到端验证 (curl 实测) <a id="sec568"></a>

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

## 75.11 G9 踩的小坑速查 <a id="sec569"></a>

| 坑 | 表现 | 原因 | 修 |
|---|---|---|---|
| ⭐ **改代码后 jar 没重打** | product `/internal/all` 返 `NoResourceFoundException` (404) | fat jar 是旧版本, 没有 G9.3.1 加的端点 | kill 进程 → `mvn install` → 重启 |
| ⭐ IDE 自动 import 坑 (3 次) | `org.w3c.dom.Text` / `org.yaml.snakeyaml.Event.ID` / `org.aspectj.bcel.Code` | IDE 弹候选选错 | 永远先看包名 `com.minimall.*` / `java.*` / `org.springframework.*` |
| BoolQuery must/filter 用反 | 精确过滤被打分影响 | 把 categoryId 放 must 了 | 改 filter (不打分, 有缓存) |
| PageRequest 从 0 开始 | 第 1 页拿到第 2 页数据 | 直接传 page 没减 1 | `PageRequest.of(page - 1, size)` |
| 包装类 == 拆箱 NPE | `result.getCode() == 200` 偶发 NPE | code 为 null 时拆箱失败 | 先判 `code == null` |
| Bash 起 java 失败 (exit 127) | `command not found` | Git Bash PATH 里 java 是 Java 8 | 用 PowerShell + `$env:JAVA_HOME\bin\java.exe` 全路径 |
| product 一直连不上 Seata | 日志刷 `Connection refused 8091` | Seata 容器停了 | `docker start seata-server` (虽然只是干扰日志, 不阻塞业务) |

## 75.12 G9 教学速查 - 关键 API & 模式 <a id="sec570"></a>

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

## 75.13 G9 累计文件 <a id="sec571"></a>

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

## 75.14 ⭐ G9 学到的 10 个核心知识点 <a id="sec572"></a>

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



# 76. common-swagger - Knife4j 公共配置 <a id="sec573"></a>

> 抽到公共库, 5 个业务服务 (user/product/order/review/search) 一行依赖搞定 API 文档.

## 76.0 总览 - 做了啥 <a id="sec574"></a>

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

## 76.1 4 个相关概念关系 (必须区分) <a id="sec575"></a>

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

## 76.2 mini-mall-common-swagger 子模块 <a id="sec576"></a>

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

## 76.3 SwaggerConfig.java (核心) <a id="sec577"></a>

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

## 76.4 5 个业务服务 pom 加依赖 <a id="sec578"></a>

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

## 76.5 ⭐ /doc.html 为啥不用配置就能访问 (Servlet 3.0 规范) <a id="sec579"></a>

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

## 76.6 端到端验证 <a id="sec580"></a>

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

## 76.7 踩的小坑速查 <a id="sec581"></a>

| 坑 | 表现 | 修 |
|---|---|---|
| **改完 pom 后没重启 jar** | `/doc.html` 404 / 老内容 | kill 进程 + mvn install + 重启 |
| **mvn clean 报错占用** | `Failed to delete xxx.jar` | 先 kill 所有 java 进程 (`Stop-Process java`) |
| 父 pom 解多了 module 注释 | `Could not find module 'mini-mall-common-security'` | 没建的不要解开 |
| 包目录手敲错字 | `con**fog**` 而不是 `con**fig**` | IDEA 右键 New > Package 自动建嵌套, 别手动 mkdir |
| import 选错版本 | `io.swagger.annotations.*` (Swagger 2 注解) | 必须 `io.swagger.v3.oas.models.*` (OpenAPI 3) |
| @EnableSwagger 找不到 | 找了一个不存在的注解 | 没有这个东西! 全是 Spring Bean 扫描机制 |

## 76.8 教学速查 - 5 个新概念 <a id="sec582"></a>

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

## 76.9 累计文件 <a id="sec583"></a>

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

## 76.10 ⭐ G9 + common-swagger 学到的核心知识 (合集) <a id="sec584"></a>

延续 G9 的 10 大知识, common-swagger 再补 3 条:

11. **Spring Bean 扫描 + @ComponentScan("com.minimall")** — common 库的配置类被业务自动扫到, 不需要任何 @EnableXxx 注解
12. **Servlet 3.0 META-INF/resources/** — jar 里的静态资源自动映射到 web 根, Knife4j / WebJars 都用这个机制
13. **OpenAPI 3 + Knife4j** — Spring Boot 3 标准: knife4j-openapi3-jakarta-spring-boot-starter + io.swagger.v3.oas.models.* 注解

---

# Chapter 77 · SEC 阶段 · common-security 抽取 <a id="sec435"></a>

> 📎 **完整源码+逐行解释见同目录 docx**:[`Chapter77_common-security_抽取.docx`](./Chapter77_common-security_抽取.docx)

## 77.1  这次做了什么 <a id="sec436"></a>

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

## 77.2  一次完整跨服务调用的链路 <a id="sec437"></a>

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

## 77.3  common-security 4 大组件 <a id="sec438"></a>

| 组件 | 位置 | 职责 |
|---|---|---|
| `JwtUtil` | `util/JwtUtil.java` | 生成/解析 token (合并自 user+gateway 两份) |
| `JwtProperties` | `properties/JwtProperties.java` | `@ConfigurationProperties` 绑 yml `jwt.*` |
| `HeaderInterceptor` | `interceptor/HeaderInterceptor.java` | 进站: `X-User-Id` 头 → `SecurityContextHolder` |
| `FeignAuthInterceptor` | `interceptor/FeignAuthInterceptor.java` | 出站: `SecurityContextHolder` → `X-User-Id` 头 |

加上自动装配 3 件套:`SecurityAutoConfiguration` + `WebMvcConfig` + `AutoConfiguration.imports`。

## 77.4  HeaderInterceptor 源码 (进站) <a id="sec439"></a>

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

## 77.5  FeignAuthInterceptor 源码 (出站) <a id="sec440"></a>

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

## 77.6  ⭐ 最深的坑 — @ConditionalOnClass + @Import 不能混用 <a id="sec441"></a>

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

## 77.7  Controller 改造范围 (SEC.10) <a id="sec442"></a>

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

## 77.8  Feign Client 改造范围 (SEC.11) <a id="sec443"></a>

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

## 77.9  端到端验证 (SEC.12) <a id="sec444"></a>

启动 4 个服务(gateway + user + product + order),用 `alice/123456` 登录:

| # | 端点 | 验证项 | 结果 |
|---|---|---|---|
| ① | POST /user/login | 白名单跳过 + JWT 生成 | ✅ token |
| ② | GET /user/me | gateway 解 JWT → 塞 X-User-Id → user 服务 HeaderInterceptor → SecurityContextHolder.getUserId()=1 | ✅ userId=1 |
| ③ | GET /user/address | user 服务 SecurityContextHolder 工作 + 查 alice 2 地址 | ✅ |
| ④ | GET /order/my | order 服务【独立】走通同样链路 + 查 alice 14 订单 | ✅ |
| ⑤ | GET /user/1/with-product/1 | user → Feign 出站调 product, FeignAuthInterceptor 不破坏链路 | ✅ |

## 77.10  踩坑实录 <a id="sec445"></a>

1. **`@ConditionalOnClass + @Import` 不能混用** — 详见 77.6
2. **`provided` scope 不传递** — common-security pom 必须显式加 `jakarta.servlet-api` (`provided`)
3. **Windows 多 JDK PATH 顺序坑** — JAVA_HOME=Java 21 但 PATH 里 Java 8 在前,要用绝对路径 `& "D:\jdk-21.0.11\bin\java.exe"`

## 77.11  本轮累计能力 <a id="sec446"></a>

| 能力 | 适合场景 |
|---|---|
| 共享 jar 自动配置 (`@AutoConfiguration` + imports) | 抽任何"业务无关的横切机制"(认证/限流/日志/链路追踪) 成 starter |
| `@ConditionalOnClass(name="...")` 按 classpath 选激活 | 一个 jar 兼容 MVC + WebFlux 等场景, 按宿主项目自动选合适部分 |
| `ThreadLocal` (`SecurityContextHolder`) 替代显式参数透传 | 解耦业务代码与 HTTP/RPC 细节, 业务层只关心"当前用户是谁" |
| 进站 + 出站拦截器对称设计 | 认证信息在跨服务调用链里自动跟随, 业务代码 0 改动 |
| 分清 MVC (HandlerInterceptor) 与 WebFlux (GlobalFilter) | 网关用 GlobalFilter, 业务服务用 HandlerInterceptor, 两套不互通 |

---

# Chapter 78 · OAUTH 阶段 · GitHub 第三方登录 + Java 通用知识深挖 <a id="sec700"></a>

## 78.1  这次做了什么 <a id="sec701"></a>

实现 mini-mall-cloud **第一个第三方登录**: 用户用 GitHub 账号一键登录, 后端签发我们自己的 JWT, 后续访问受保护接口跟普通账号一模一样.

涉及组件:

| 层 | 变更 |
|---|---|
| 数据库 | `user` 表加 `oauth_provider` + `oauth_id` 列; 复合唯一索引 `uk_oauth (oauth_provider, oauth_id)`; `password` 改为允许 NULL (OAuth 用户没本地密码) |
| user 服务 | `properties/GithubOAuthProperties` (@ConfigurationProperties("oauth.github"))<br>`config/RestTemplateConfig` (注册 RestTemplate Bean)<br>`controller/OAuthController` (2 端点: `/github/login` + `/github/callback`) |
| 网关 | `AuthGlobalFilter` 白名单加 `/user/oauth` (callback 时用户还没登录, 不能拦) |
| 配置 | `application.yml` 加 `oauth.github.*` 段 (client-id 公开, client-secret 私密 - 真 yml 在 .gitignore 里, .example 用占位符) |

## 78.2  Authorization Code Flow 4 步链路 <a id="sec702"></a>

```
浏览器                            user 服务                         GitHub
  │                                 │                                 │
  │ ① 点 "GitHub 登录" 按钮           │                                 │
  │ GET /user/oauth/github/login    │                                 │
  │ ──────────────────────────────► │                                 │
  │ 返 { url: "github.com/authorize?..." }                            │
  │ ◄────────────────────────────── │                                 │
  │                                                                   │
  │ ② 前端 window.location.href = url                                 │
  │ ────────────────────────────────────────────────────────────────► │
  │                                                                   │
  │ ③ GitHub 网页用户点 "同意授权"                                     │
  │                                                                   │
  │ ④ GitHub 302 跳回                                                  │
  │    /user/oauth/github/callback?code=xxxxxx                        │
  │ ◄─────────────────────────────────────────────────────────────────│
  │                                                                   │
  │ ⑤ GET /callback?code=xxx        │                                 │
  │ ──────────────────────────────► │                                 │
  │                                 │ ╔══ githubCallback 4 步 ═════╗  │
  │                                 │ ║                            ║  │
  │                                 │ ║ ⑥ POST /access_token        ║──┤
  │                                 │ ║ {code,client_id,secret}    ║  │
  │                                 │ ║ ◄──{access_token:"gho_xx"} ║──┤
  │                                 │ ║                            ║  │
  │                                 │ ║ ⑦ GET api.github.com/user   ║──┤
  │                                 │ ║ Authorization: Bearer xxx  ║  │
  │                                 │ ║ ◄─{id,login,email,avatar}  ║──┤
  │                                 │ ║                            ║  │
  │                                 │ ║ ⑧ MySQL                     ║  │
  │                                 │ ║ SELECT WHERE oauth_id=xxx  ║  │
  │                                 │ ║ 找不到 → INSERT             ║  │
  │                                 │ ║                            ║  │
  │                                 │ ║ ⑨ JwtUtil.generateToken    ║  │
  │                                 │ ╚════════════════════════════╝  │
  │ 返 { token, user }              │                                 │
  │ ◄────────────────────────────── │                                 │
  │ 前端 localStorage.setItem('token'), 跳首页                         │
```

⭐ **关键:** ⑥和⑦是后端**直接调** GitHub (不经过浏览器), `client_secret` 永远不出后端 — 这是 OAuth 安全设计的核心.

## 78.3  关键安全设计 (为什么是 code 而不是 access_token) <a id="sec703"></a>

**反例 (不安全):** 假如 GitHub 直接给我们 `access_token`:

```
http://localhost:9080/.../callback?access_token=gho_xxxxxxxxxxxx
                                                  ▲
                                          长期有效, 一旦泄露随便用
```

`access_token` 跑到浏览器地址栏 → 历史记录/Referer/服务器日志全留下 → 抓一条记录盗用账号几个月.

**正例 (OAuth 设计):** GitHub 给临时 `code`, 后端拿 `code + client_secret` 私下换 `access_token`:

```
浏览器 URL: ?code=8d1b6c9e2f3a4b5c   ← 短期一次性, 暴露也无害
                                       (没 client_secret 换不到 token)

后端 → GitHub: 走 HTTPS, 浏览器看不到 access_token
```

| OAuth 概念 | 类比 (快递场景) |
|---|---|
| `code` | **取件码** (短期, 给你能取一次) |
| `client_secret` | **身份证** (只在家里, 不出门) |
| `access_token` | **包裹** (真正想要的东西) |

取件码挂小区公告栏(浏览器 URL)没问题, 因为快递柜还要验**身份证**(client_secret)才给包裹(access_token).

## 78.4  踩坑实录 (8 大坑) <a id="sec704"></a>

| # | 现象 | 根因 | 解决 |
|---|---|---|---|
| 1 | GitHub 报 `redirect_uri_mismatch` | yml callback URL 跟 OAuth App 注册的不一致 | GitHub Settings 改 callback URL 跟 yml 完全一致 |
| 2 | 复制 URL 中间多 `%20` (空格) | 聊天框换行符被复制成空格 → URL 编码成 %20 | 改用 `Start-Process $url` 直接打开浏览器, 绕开复制 |
| 3 | `Cannot invoke JsonNode.asText()` because get(String) is null | `tokenNode.get("xxx")` 字段不存在时返 **Java null**, 再调 `.asText()` NPE | `get()` → `path()`<br>(`path()` 找不到字段返 MissingNode 不会 NPE) |
| 4 | `incorrect_client_credentials` | yml `client-secret` 填错 或 GitHub 重新生成过 | GitHub Generate new secret → 复制 → 改 yml → 重启 |
| 5 | `Field 'password' doesn't have a default value` | user 表 password NOT NULL, OAuth 用户没本地密码 | `ALTER TABLE user MODIFY COLUMN password VARCHAR(100) NULL` |
| 6 | `spring-boot:run` 启动报 `ClassNotFoundException: MiniMallUserApplication` | 中文路径 + fork 子进程 classpath 传输出错 | 改用 `mvn package` + `java -jar fat.jar` |
| 7 | Bash 里 `java` 是 JDK 8 (PATH 优先级), Spring Boot 3 要 JDK 17+ | OS PATH 里 Java 8 在前 | PowerShell 用 `& "D:\jdk-21\bin\java.exe"` 绝对路径 |
| 8 | `Out-File` 报 `C:\Temp 不存在` | 写日志路径不存在 | 改用 `$env:TEMP\xxx.log` |

## 78.5  📄 Java 通用知识深挖 (独立 docx) <a id="sec705"></a>

围绕 OAuth 开发沉淀的 **4 大基础知识块**, 整理成独立 docx:

📄 **[散笔记/Chapter78_OAUTH_Java通用知识深挖.docx](./散笔记/Chapter78_OAUTH_Java通用知识深挖.docx)**

包含 5 大章节:

1. **反射 (java.lang.reflect)** — Java 元编程能力源头, Spring/MyBatis/Jackson 的底层共同点
2. **反射 + 序列化的关系** — Jackson (JSON) / MyBatis (DB) / Serializable (字节流) 三套对比 + Unsafe 绕过构造的特殊点
3. **HTTP 入站** (本章最重) — Tomcat Coyote/Catalina 双子模块 / RequestFacade 门面源码 / connector.Request 11 个字段逐条释义 / **JSON 反序列化两层转换链路** / **Tomcat ≠ 反序列化** 经典误区拆解 / InputStream 一次性大坑 + ContentCachingRequestWrapper / Filter vs Interceptor / ArgumentResolver / **入站没有 HttpEntity** (头/体分开拿) / RequestContextHolder 全局获取
4. **HTTP 出站 (RestTemplate)** — 三方法 (getForObject/postForObject/exchange) / HttpHeaders / HttpEntity / ResponseEntity / **HttpHeaderUtil 工具类** / POST JSON / GET / Form 表单 + MultiValueMap / Content-Type vs Accept 区分 / **get() vs path()** 防 NPE
5. **入站 vs 出站镜像对照表** + 10 个面试问答总结

## 78.6  本轮累计能力 <a id="sec706"></a>

| 能力 | 适用场景 |
|---|---|
| OAuth2 Authorization Code Flow 套路 | 接入任何第三方登录 (微信/支付宝/Apple/Google) 一套思路 |
| `@ConfigurationProperties` + kebab-case → camelCase 自动绑 | 把外部配置安全注入 Java 字段, 业务零硬编码 |
| `RestTemplate.exchange` + `Bearer Token` | 调任何带 OAuth 鉴权的第三方 REST API |
| `Jackson JsonNode.path()` 防御 | 处理对方 JSON 字段不稳定/可空的接口 |
| MySQL 复合唯一索引 `(oauth_provider, oauth_id)` | 多 OAuth 平台共享同一张 user 表的设计模式 |
| 网关白名单跳过 JWT | callback / 公开页等未登录访问的鉴权豁免 |
| `application.yml` (gitignore) + `.example` (commit) | 防止 `client-secret` 进 git history 的标准做法 |

---

**OAUTH 阶段完毕**. mini-mall-cloud 解锁第三方登录能力, 用户多了一条 "零密码注册" 入口.
下一步可选: ① 微信/支付宝 OAuth 复用同套模板 ② 前端 Vue3 集成 GitHub 登录按钮 ③ `/user/me` 接口加完整资料 (目前只返 userId)

**common-swagger 完毕**. mini-mall-cloud 至此基础库 3/4: **core / redis / swagger**, 只剩 common-security 抽鉴权. 业务 100% + 文档统一, 项目"能展示" + "能讲清楚架构".
---

# Chapter 79 · AUTH 阶段 · 抽认证服务 mini-mall-auth <a id="sec800"></a>

## 79.1  这次做了什么 (1 句话) <a id="sec801"></a>

把分散在 user 服务里的【本地登录 / 注册 / OAuth】三件套统一抽到独立微服务 **mini-mall-auth** (端口 9006), user 服务从此只管 user 表 CRUD, 不碰认证业务.

> 接 Chapter 78 OAUTH 阶段的尾声 — 当时为了快速跑通 OAuth 验证可行性, 把 OAuthController 临时放在 user 服务里. 本章把架构修正到位.

## 79.2  抽完后的服务架构图 <a id="sec802"></a>

```
                 浏览器 / 前端
                       │
                       ▼
            ┌──────────────────────┐
            │  mini-mall-gateway   │  :9080
            │  WHITE_LIST: /auth   │  ← 整段放行 (login/register/oauth)
            │  BLACK_LIST: /user/internal  ← 403, 外部不可达
            └──────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
 ┌──────────────┐ ┌────────────┐ ┌──────────────────┐
 │ mini-mall-   │ │ user :9001 │ │ product/order/..│
 │ auth :9006   │ │            │ │                  │
 │              │ │ ├ /user/me │ │                  │
 │ /auth/login  │ │ ├ /user/{id}│ │                  │
 │ /auth/register│ │ ├ /user/internal/* ← 仅 Feign 入│
 │ /auth/oauth/*│ │            │ │                  │
 │ (有 JwtUtil) │ │            │ │                  │
 └──────┬───────┘ └────▲───────┘ └──────────────────┘
        │              │
        │   Feign      │
        │   ──────────►│  GET  /user/internal/by-username/{name}
        │              │  GET  /user/internal/by-oauth/{prov}/{id}
        │              │  POST /user/internal             (新建)
        │              │
        └──────────────┘
```

**端口分配**:gateway 9080 / user 9001 / product 9002 / order 9003 / review 9004 / search 9005 / **auth 9006** (新).

**Sentinel client 端口**:user 8719 / gateway 8721 / **auth 8720** (新, 不能撞).

## 79.3  internal 接口模式 (auth 不直连 DB, Feign 调 user) <a id="sec803"></a>

抽服务最经典的设计选择题 — **auth 怎么访问 user 表?**

| 方案 | 做法 | 选不选 |
|---|---|---|
| A | auth 自己连 MySQL, 自己有 UserMapper | ❌ 破"一服务一表"原则, 出 bug 难定位 |
| **B** ✅ | auth 通过 Feign 调 user 服务 internal 接口 | 干净, 可复用, 学到 internal 模式 |
| C | 把 user 表抽到独立 DB 服务 | 过度设计, 跳过 |

**internal 接口设计要点**:

1. **路径前缀 `/user/internal/`** — 跟业务接口 `/user/{id}` 区分, 一眼能看出 "这是给服务间调的"
2. **网关黑名单** — `AuthGlobalFilter` 加 `BLACK_LIST = ["/user/internal"]`, startsWith 匹配直接返 403, **外部绝对调不到**
3. **Feign 不走网关** — auth → user 走 Nacos 直连 (`lb://mini-mall-user`), 不经过网关, 所以 internal 接口对 auth 可达, 对外部不可达
4. **返 `Result<User>` 不抛 404** — 查不到返 `Result.success(null)`, 让 auth 自己判 null 决定 "用户不存在" 或 "首次 OAuth 建账号"

3 个端点 (`UserInternalController`):

```java
GET  /user/internal/by-username/{name}      // 本地登录用 (含 password 密文)
GET  /user/internal/by-oauth/{prov}/{id}    // OAuth 回调用
POST /user/internal                          // 注册 / OAuth 首次 (回填 id)
```

auth 这边的 `UserFeignClient` 三方法一一对应, 加 fallback 返 `Result.error(503, "用户服务暂不可用")` 区分 "user 服务挂了" vs "用户不存在".

## 79.4  踩坑实录 (5 大坑) <a id="sec804"></a>

### 坑 1: `@JsonIgnore` 在 password 字段上, Feign 跨服务密文丢失

`user.entity.User.password` 原来加了 `@JsonIgnore` (防泄密码到前端). 但抽 auth 后:

- **注册**: auth 算 BCrypt 密文 → Feign POST `/user/internal` 序列化 User → `@JsonIgnore` 把 password 跳过 → user 服务收到 password = null → 入库密码为空 ❌
- **登录**: user internal 返 User → 序列化跳过 password → auth 拿不到密文比对 ❌

**修复**:
- 拿掉 `entity.User.password` 上的 `@JsonIgnore`
- auth.model.User.password 也不能加 `@JsonIgnore`
- **改为兜底方案**: Controller 在返给前端前 **手动** `user.setPassword(null)`
  - auth 的 `AuthController` 两处, `OAuthController` 一处
  - user 服务的 `UserController.getById` / `with-product` 两处

设计权衡: **从"框架自动兜底" 退化到 "调用方手动兜底"**. 因为跨服务序列化反而需要密文流通, `@JsonIgnore` 不再适合.

### 坑 2: fat jar 改 yml 不重新打包不生效

抽完 auth 第一次启动 → `Port 8080 was already in use` (Spring Boot 默认端口).

原因: `application.yml` 是 mvn package **之后** 才 `cp .example .yml` 的, 这次拷贝的文件**不在 jar 里**. Spring Boot 读 classpath 找不到 `server.port: 9006`, 默认 8080, 撞了别的进程.

**修复**: 重新 `mvn package`, 让 yml 进 jar; 或把 yml 放 jar 同目录 (Boot 会从 `file:./application.yml` 加载, 优先级高于 classpath).

教训: **改 src/main/resources 下任何配置都得重新打包**. 这是 fat jar 模型的天然代价, 单体 web 容器不存在这个坑 (因为是直接读 webapps/ 下的 classes/).

### 坑 3: 网关 application.yml 加路由但 jar 没重打

跟坑 2 同根源. AUTH.12 我改了 `gateway/application.yml.example` 加 `auth-route`, 但用户的真实 `application.yml` 改了, jar 没重打, 启动后 auth-route 不存在 → 所有 `/auth/**` 返 404.

教训: 抽服务必须**网关 yml 一改就重打 gateway**, 否则路由表脱节.

### 坑 4: Sentinel client port 必须错开

auth 的 `application.yml` 一开始抄了 user 的 `transport.port: 8719`, 跑起来跟 user 撞了 (本机多服务时一台机器开多个 Sentinel client). 改成 8720 (跟 user 的 8719 / gateway 的 8721 都不同).

教训: 本机部署多个服务, **Sentinel client 端口规划要跟服务端口一样提前定**.

### 坑 5: `cp application.yml.example application.yml` 容易漏

抽服务流程一长, AUTH.13 里 5 件手动事用户漏了 2 件: yml 没复制, gateway 路由没加. 导致 auth 启动撞端口 + gateway 路由 404.

教训: 抽服务的检查清单要明确**列出每个 src/main/resources 下要建的文件名**, 否则 `.example` 文件常被忽略.

## 79.5  端到端验证 (6/6 通过) <a id="sec805"></a>

跑了 6 项, 全过:

```
✅ POST /auth/register                注册新本地账号, userId 回填, token 签出, password 兜底 null
✅ POST /auth/login                   同账号登录, 验证 BCrypt 比对走通
✅ GET  /user/me                      JWT → 网关解 → X-User-Id 透传到 user 服务 (跨服务身份)
✅ GET  /user/internal/by-username    外部访问被网关黑名单 403 (internal 不可达)
✅ GET  /auth/oauth/github/login      授权页 URL 拼对 (client_id + redirect_uri)
✅ 浏览器走完 GitHub OAuth → callback  4 步全通, 返 AuthResponse(token, user)
```

OAuth 浏览器流程验证了 `userFeignClient.getByOauth("github", oauthId)` 链路 (查到旧 OAuth 用户 → 直接签 JWT), 本地注册验证了 `userFeignClient.createUser(user)` 链路 (新 userId=7).

## 79.6  本轮累计能力 + 设计反思 <a id="sec806"></a>

| 抽出来后多了的能力 | 说明 |
|---|---|
| **服务边界清晰** | user 不再"既管用户表又管认证", auth 单一职责 |
| **认证服务可独立扩展** | 加微信/支付宝 OAuth 只改 auth, 不动 user |
| **internal 接口模式** | 后续 product/order 要操作 user 也走同款 internal + Feign + 网关黑名单 |
| **跨服务密文流通** | `@JsonIgnore` → "调用方手动兜底", 是抽服务后绕不开的模式 |

**设计反思** — 抽服务时 3 个问题想清再动:

1. **新服务连不连库?** — 不连最干净, 但要确认能不能容忍 Feign 一跳的延迟 (auth 每次登录都多一次内网调用)
2. **internal 接口怎么防外部?** — 网关黑名单 + Feign 不走网关 = 双层保护. 真生产环境再加 "调用方身份校验" (验 X-Service-Name 之类)
3. **`@JsonIgnore` 等"框架自动兜底"在跨服务时还成立吗?** — 不一定. 抽服务前要扫一遍 entity 上的注解, 哪些跟跨服务传输冲突, 提前定方案

---

**AUTH 阶段完毕**. mini-mall-cloud 至此**认证业务独立成服务**, 微服务架构进一步合理化. 下一步可选: ① 微信/支付宝 OAuth 复用 auth ② 抽 `user-api` 模块解决 `model.User` 重复定义 ③ 给 internal 接口加调用方身份校验 (X-Service-Name)

---
---

# 第 80 章 · C 端商城 + 越权安全专题 <a id="sec810"></a>

> 承接第 79 章「AUTH 阶段完毕」。时间线: ADMIN 后台(6-25) → FILE/MinIO(6-26) → 前端联调补全(6-27) → 越权安全修复。
> 覆盖: RBAC 角色权限 → 对象存储 MinIO → DTO/VO 分层 → MyBatis-Plus 进阶 → 越权安全专题(含网关白名单/WebFlux/needAdmin) → 上线 Docker 化。

## 80.1 RBAC 角色权限体系(ADMIN 阶段地基) <a id="sec811"></a>

### 背景:为什么要引入 role

第 79 章之前系统只分「登录 / 未登录」。要做后台, 必须再分「普通用户 / 管理员」, 于是 user 表加一列 role:

```
role = 0  →  普通用户(C 端, 买东西)
role = 1  →  管理员(后台, 管商品/管用户)
```

**这一个字段, 是后面所有垂直越权判断的依据。**

### 后台接口的真实样子(AdminUserController)

```java
@RestController
@RequestMapping("/admin/user")     // ⭐ 关键: 挂 /admin/ 前缀
public class AdminUserController {
    // static final: 整个类共享一个加密器, 不用每次 new(BCrypt 加密器线程安全)
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    @Autowired
    private UserMapper userMapper;
```

类注释道破整个 RBAC 设计的精髓:

```java
/**
 * 全部走网关 /admin/** 路径 → 网关 AuthGlobalFilter 已经做了:
 *   ① 必须带 JWT (否则 401)
 *   ② role 必须 = 1 (否则 403)
 * 所以这里不用再校验权限, 信任网关传来的 X-User-Id.
 */
```

- `@RequestMapping("/admin/user")` —— 只要路径以 `/admin/` 开头, 网关就自动拦「非管理员 403」。Controller 里一行权限代码都不写, 权限收口在网关一处。
- 「信任网关传来的 X-User-Id」—— 下游不再自己解 token, 网关解完把 userId 塞 header 传下来。下游端口(9001)不对外暴露, 外部只能走 9080 网关, 所以信任安全。
- 这就是「约定优于配置」: 管理接口挂 `/admin/` 是团队约定; 约定成立权限就自动生效。将来加十个后台接口, 只要挂对前缀, 零成本。

### MyBatis-Plus 条件分页(真实代码)

```java
@GetMapping("/page")
public Result<IPage<User>> page(AdminUserPageDTO query) {   // 参数从 query string 自动绑定
    Page<User> p = Page.of(query.getPage(), query.getSize());       // ① 造分页对象
    QueryWrapper<User> wrapper = new QueryWrapper<>();              // ② 造条件构造器
    // ③ 关键词模糊: username 或 nickname 命中即可
    if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
        wrapper.and(w -> w.like("username", query.getKeyword())
                          .or().like("nickname", query.getKeyword()));
    }
    // ④ 状态/角色: 传了才加条件, null 就不筛
    if (query.getStatus() != null) wrapper.eq("status", query.getStatus());
    if (query.getRole()   != null) wrapper.eq("role",   query.getRole());
    wrapper.orderByDesc("id");
    IPage<User> result = userMapper.selectPage(p, wrapper);        // ⑤ MP 自动拼 LIMIT + 查 count
    result.getRecords().forEach(u -> u.setPassword(null));         // ⑥ ⭐ 兜底: 清掉密文
    return Result.success(result);
}
```

- `page(AdminUserPageDTO query)` 没有 `@RequestBody` —— GET 请求参数在 URL 里(`?page=1&size=20&keyword=alice`), Spring MVC 自动把 query string 映射进 DTO。这是 GET 和 POST 收参的根本区别。
- `wrapper.and(w -> w.like(...).or().like(...))` —— 这层嵌套很关键。生成 SQL 是 `AND (username LIKE ? OR nickname LIKE ?)`。如果不套 `and(...)`, 会变成 `... AND username LIKE ? OR nickname LIKE ?`, OR 把前面 status/role 条件「漏出去」(SQL 里 AND 优先级高于 OR)。**套括号是防逻辑短路的常见坑。**
- `if (xxx != null)` 才加条件 —— 动态查询: 前端没传的筛选项就不拼进 SQL, 一个方法应付所有筛选组合。
- `selectPage(p, wrapper)` —— 分页插件(PaginationInnerInterceptor)拦截, 自动加 LIMIT 并额外发 `SELECT COUNT(*)`。没配这个插件, selectPage 会查全表。

### ⚠ 坑:@JsonIgnore 拿掉后, 密文必须手动兜底

第 79 章为了让 auth 服务能通过 Feign 拿到 password 密文, 拿掉了 `User.password` 上的 `@JsonIgnore`。代价是: 所有对外返回 User 的地方, 必须手动 `setPassword(null)`, 否则密文泄露。

```java
user.setPassword(null);   // ⭐ 兜底: 不返密文给前端
```

设计权衡记牢: `@JsonIgnore` 是「框架自动兜底」, 跨服务传输时它挡了该传的密文, 只能退化成「调用方手动兜底」。**抽微服务时实体上的序列化注解要重新审视。**

## 80.2 对象存储 MinIO(FILE 服务) <a id="sec812"></a>

### 为什么单独建一个服务存文件

图片/视频不能存数据库(BLOB 撑爆库、备份慢、CDN 用不上)。正确做法: 文件放对象存储(MinIO = 自建版 AWS S3), 数据库只存访问 URL。

### MinioConfig:把 MinioClient 交给 Spring 管

```java
@Configuration
public class MinioConfig {
    @Resource
    private MinioProperties minioProperties;   // 从 yml 读的配置(endpoint/key/bucket)

    @Bean                                       // ⭐ 注册成 Spring Bean, 全局复用一个连接客户端
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())      // MinIO 地址 http://localhost:9000
                .credentials(minioProperties.getAccessKey(),  // 账号密码
                             minioProperties.getSecretKey())
                .build();
    }
}
```

- `@Configuration` + `@Bean` —— 第三方类(MinioClient 是 minio SDK 的, 没法加 @Component)要交给 Spring 管理, 就在配置类里 @Bean 手动 new 一个返回。Spring 纳入容器, 别处 @Autowired 就能拿。
- 为什么做成单例 Bean —— MinioClient 内部维护 HTTP 连接池, 全局一个就够, 别每次上传 new。

### MinioService.upload:上传核心(真实代码)

```java
public String upload(MultipartFile file, String bizType) {
    if (file == null || file.isEmpty()) {                    // ① 防空
        throw new BusinessException(500, "文件为空");
    }
    // ② 构造对象名: {业务类型}/{日期}/{UUID}.{扩展名}
    String fileName = file.getOriginalFilename();
    String suffix   = fileName.substring(fileName.lastIndexOf("."));   // 取 ".png"
    String objectName = bizType + "/" + LocalDate.now() + "/"
                        + UUID.randomUUID() + suffix;
    try (InputStream is = file.getInputStream()) {           // ③ try-with-resources 自动关流
        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(props.getBucket())               // 桶名 mini-mall
                .object(objectName)                       // 对象路径
                .stream(is, file.getSize(), -1)           // 数据流 + 大小
                .contentType(file.getContentType())       // MIME 类型 image/png
                .build());
    } catch (Exception e) {
        throw new BusinessException(500, "上传失败: " + e.getMessage());
    }
    return props.getPublicUrl() + "/" + objectName;          // ④ 返完整 URL
}
```

- `bizType + "/" + LocalDate.now() + "/" + UUID` —— 对象名设计有讲究: bizType(product/avatar)一级目录分业务; 日期二级目录按天分桶, 避免单目录几十万文件; UUID 防重名。两个人都传 phone.png, UUID 保证不覆盖。**这就是为什么库里绝不能存裸 phone.png——真实存储名是 UUID。**
- `try (InputStream is = ...)` —— try-with-resources, 块结束自动 close, 不写 finally 也不漏流。
- `.stream(is, file.getSize(), -1)` —— 第三个参数 -1 是「分片大小用默认」, 要传总大小让 MinIO 知道内容长度。
- `contentType` 必须设, 否则浏览器访问图片 URL 可能当成下载而不是显示。
- 返 `publicUrl + "/" + objectName` —— 拼成 `http://localhost:9000/mini-mall/product/2026-06-30/xxx.png`, 这个完整 URL 才是存库、给前端 `<img src>` 用的。

### FileController:URL 和 objectName 都返回

```java
@PostMapping("/upload")
public Result<Map<String, String>> upload(
        @RequestParam("file") MultipartFile file,                          // 文件本体
        @RequestParam(value = "bizType", defaultValue = "product") String bizType) {
    String fileUrl = minioService.upload(file, bizType);
    String objectName = fileUrl.replace(props.getPublicUrl() + "/", "");   // 从 URL 反推 objectName
    return Result.success(Map.of("url", fileUrl, "objectName", objectName));
}
```

- 为什么返两个字段: `url` 是完整地址前端直接渲染; `objectName` 是相对路径, 将来删文件要用它定位对象, 换域名/迁 CDN 时用它重拼 URL。
- `@RequestParam("file") MultipartFile` —— 文件上传是 `multipart/form-data`, 用 `@RequestParam` 不是 `@RequestBody`。`defaultValue="product"` 让前端不传时默认归 product 目录。

## 80.3 DTO / VO 分层 + 参数校验(/user/me) <a id="sec813"></a>

### 设计思想:实体不出边界

```
前端 ─[UserProfileUpdateDTO 只放能改的]→ Controller ─[User]→ DB
前端 ←[UserProfileVO 只放能看的]────────  Controller ←[User]─ DB
```

数据库实体 User(含 password/role/status)只在后端内部流转, 进出口各换一层。

### 入参 DTO(真实代码, 含校验)

```java
@Data
public class UserProfileUpdateDTO {
    @Size(max = 20, message = "昵称不超过 20 字")
    private String nickname;
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不对")
    private String phone;
    @Email(message = "邮箱格式不对")
    private String email;
    @Size(max = 255, message = "头像 URL 太长")
    private String avatar;
}
```

DTO 类注释道破两层防护: 故意没放 username/role/status/password(字段白名单防越权); null 表示这次不改这个字段(配合 MP 空字段不更新, 实现局部更新)。

- `@Size(max=20)` —— 字符串长度上限, 防止塞 10000 字昵称撑爆库/页面。
- `@Pattern(regexp="^1[3-9]\\d{9}$")` —— 正则校验手机号: 1 开头, 第二位 3-9, 后跟 9 位数字, 共 11 位。`\\d` 双反斜杠是 Java 字符串转义(实际正则 `\d`)。
- `@Email` —— 内置邮箱格式校验。
- **⚠ 一个坑**: null 会跳过 @Pattern/@Email(不传就不校验, 合理); 但传 `""` 空串会失败(空串不匹配手机号正则)。前端要改就传值, 不改就别传这个字段, 别传空串。
- **为什么「不放 role 字段」就防住越权**: 前端 body 塞 `{"role":1}`, 反序列化成 DTO 时这个类根本没有 role 字段, Jackson 直接丢弃。用户从根上没法通过这个接口提权。

### Controller:@Valid 触发校验 + 字段白名单落库

```java
@PutMapping("/me")
public Result<Void> updateMe(@Valid @RequestBody UserProfileUpdateDTO dto) {  // ⭐ @Valid
    Long userId = SecurityContextHolder.getUserId();   // ① userId 从网关透传的 header 拿, 绝不信前端
    User update = new User();                           // ② 组装更新对象, 只放允许字段
    update.setId(userId);              // 主键 = 当前登录用户, 不给前端指定
    update.setNickname(dto.getNickname());
    update.setPhone(dto.getPhone());
    update.setEmail(dto.getEmail());
    update.setAvatar(dto.getAvatar());
    // role/status/password 这里根本不 set → 永远不会被改
    userService.updateById(update);                    // ③ MP 局部更新: null 字段不进 UPDATE SQL
    return Result.success();
}
```

三层防护叠在一起:
1. `@Valid` —— 触发 DTO 校验注解, 不合格自动抛 `MethodArgumentNotValidException` → 全局异常处理器返 400。
2. `update.setId(userId)` —— 主键强制用网关传来的 userId, 前端没机会指定改谁。
3. 只 set 4 个字段 —— 即使 DTO 侧防线被绕过, Controller 也只把这 4 个字段落库, role/status/password 物理上不参与 UPDATE。

### 出参 VO(me 方法)

```java
@GetMapping("/me")
public Result<UserProfileVO> me() {                  // ← 返 VO 不返 User
    Long userId = SecurityContextHolder.getUserId();
    User user = userMapper.selectById(userId);
    if (user == null) throw new BusinessException("用户不存在");
    UserProfileVO vo = new UserProfileVO();
    BeanUtils.copyProperties(user, vo);              // 同名字段自动拷, password 因 VO 没这字段被丢弃
    return Result.success(vo);
}
```

`BeanUtils.copyProperties(user, vo)` —— Spring 工具, 按字段名相同从 user 拷到 vo。user 有 password, vo 没这字段 → 自动跳过, 顺手完成脱敏。比手写十几行 setXxx 省事。注意: 浅拷贝、走反射性能一般, 高频接口用 MapStruct(编译期生成拷贝代码)。

## 80.4 MyBatis-Plus:一个字段存多张图(商品缩略图) <a id="sec814"></a>

商品有多张缩略图, 不想为此单独建一张 product_image 表, 用一个字段存 JSON 数组:

```java
// Product.java 真实代码
@TableField(typeHandler = JacksonTypeHandler.class)
private List<String> thumbnails;
```

- **写库**: MP 用 JacksonTypeHandler 把 `List<String>` 序列化成 JSON 字符串 `["a.png","b.png"]`, 存进 varchar/text 列。
- **读库**: 反序列化回 `List<String>`。数据库里那一列就是普通字符串, 不是特殊类型。
- **适用场景**: 数量少、不需要单独按图片查询的集合(缩略图、标签)。如果要「按某张图反查商品」, 就得拆表。
- **⚠ 坑提醒**: 字段级 typeHandler 对 insert/update 天然生效; 但查询自动映射要类上加 `@TableName(autoResultMap=true)`, 否则 SELECT 出来 thumbnails 可能不反序列化(拿到 null)。当前 Product 类只有 `@Getter @Setter`, 如果查出来 thumbnails 为 null, 先去类上补 `@TableName(value="product", autoResultMap=true)`。

## 80.5 地址默认互斥(MP null 不更新 + 事务) <a id="sec815"></a>

### 现象与根因

前端截图: 一个用户有两个地址都标「默认」。根因: 新增地址时前端 body 里 isDefault=1 被直接落库, 绕过了「设默认」的专用接口, 导致多个默认并存。

### 三层修复(真实代码)

**① 新增强制非默认**(AddressController.create):

```java
address.setUserId(userId);       // ⭐ 强制盖 userId, 防伪造别人地址
address.setIsDefault((byte) 0);  // ⭐ 新地址必为非默认, 想设默认走专用接口
addressService.save(address);
```

**② 改内容时不动 is_default**(AddressController.update):

```java
address.setId(id);
address.setUserId(userId);
address.setIsDefault(null);      // ⭐ null → MP 不把 is_default 拼进 UPDATE SQL
addressService.updateById(address);
```

这里用到 MP 核心机制: **FieldStrategy.NOT_NULL(默认策略)—— 实体里为 null 的字段, 不会出现在 UPDATE 语句里。** 所以把 isDefault 设 null, 这次 update 只改地址内容, 碰都不碰 is_default 列。「改内容」和「改默认」两个动作彻底解耦。

**③ 设默认走专用接口 + 事务两条 SQL**(AddressServiceImpl):

```java
@Override
@Transactional(rollbackFor = Exception.class)   // ⭐ 显式指定所有异常都回滚
public void setDefaultAddress(Long userId, Long addressId) {
    addressMapper.clearDefaultByUserId(userId);      // ① 先把我名下所有地址 is_default=0
    addressMapper.setDefaultById(addressId, userId); // ② 再把指定这条设 1
}
```

```java
@Update("UPDATE address SET is_default = 0 WHERE user_id = #{userId}")
void clearDefaultByUserId(@Param("userId") Long userId);

@Update("UPDATE address SET is_default = 1 WHERE id = #{addressId} AND user_id = #{userId}")
void setDefaultById(@Param("addressId") Long addressId, @Param("userId") Long userId);
```

三个知识点:
1. **`@Transactional(rollbackFor = Exception.class)` 为什么显式写** —— Spring 默认只对 RuntimeException 和 Error 回滚, checked 异常(如 IOException)不回滚。显式写上, 保证第②步失败第①步也回滚, 不会出现「全清 0 但没设新默认」的脏状态。两条 UPDATE 必须同生共死。
2. **第②条 SQL 的 `AND user_id = #{userId}` 是水平越权防线** —— 别人的 addressId 传进来, user_id 对不上, 匹配不到行, 更新 0 条, 设不了默认。哪怕 Controller 层漏判, SQL 这层也兜住。
3. **`#{}` 与 `@Param`** —— `#{userId}` 是 MyBatis 预编译占位符(PreparedStatement 的 ?), 防 SQL 注入; 多参数必须 @Param 命名, SQL 里才能 `#{userId}` 引用。

## 80.6 越权安全专题 ⭐(本会话核心) <a id="sec816"></a>

### 两种越权

| 类型 | 定义 | 例子 | 防线 |
|---|---|---|---|
| 水平越权 | 同级用户互碰数据 | A 改 B 的地址/订单 | 校验归属: 资源 owner == 当前用户 |
| 垂直越权 | 低权碰高权接口 | C 端用户改商品/发秒杀 | 校验角色: role == 1 |

**铁律: userId / role 只从 token(网关透传的 X-User-Id / X-User-Role header)拿, 永远不信前端 body。**

### 鉴权链路全图(两道关)

```
外部请求 (Authorization: Bearer <token>)
   │
   ▼
┌──── 网关 AuthGlobalFilter(粗粒度: 你是谁 / 能不能进这类接口)────┐
│ ① 黑名单 /user/internal → 直接 403(服务间接口不对外)          │
│ ② 白名单(登录、GET 商品) → 放行                              │
│ ③ 解析 token → userId + role                                  │
│ ④ needAdmin(path, method)? → 要 role==1                       │
│ ⑤ userId、role 塞进 X-User-Id / X-User-Role 透传              │
└──────────────────────┬────────────────────────────────────────┘
                       ▼
┌── Controller/Service(细粒度: 这条具体数据是不是你的)──────────┐
│ getAndCheckOwn(id, userId) / order.getUserId().equals(userId) │
└────────────────────────────────────────────────────────────────┘
```

为什么必须两层: 网关只知道「你是登录用户 / 管理员」, 它不可能知道 address id=7 是不是你的——那要查库比对, 只能在业务服务做。**垂直越权在网关拦, 水平越权在 Service 拦。**

### 网关第一关:白名单(method + path 双维度)

鉴权前先放行免登录接口。早期白名单只按 path 放行, 有个洞: `/product` 放行了, 会连 `POST /product`(改商品)一起放行。正解是路径 + 方法一起判断:

```java
// 用 record 描述一条白名单规则: 前缀 + 允许的方法集合
private record WhitelistRule(String prefix, Set<HttpMethod> methods) {}

private static final List<WhitelistRule> WHITE_LIST = List.of(
        new WhitelistRule("/auth", null),                       // 登录注册, methods=null 表示不限方法
        new WhitelistRule("/category", Set.of(HttpMethod.GET)), // 只放行 GET
        new WhitelistRule("/product", Set.of(HttpMethod.GET)),  // ⭐ 只放行 GET, 写操作仍要鉴权
        new WhitelistRule("/search/product", Set.of(HttpMethod.GET)),
        new WhitelistRule("/review/product", Set.of(HttpMethod.GET)),
        new WhitelistRule("/coupon/available", Set.of(HttpMethod.GET))
);
```

```java
boolean inWhiteList = WHITE_LIST.stream().anyMatch(r ->
        path.startsWith(r.prefix())                              // 前缀命中
        && (r.methods() == null || r.methods().contains(method)) // 且方法命中(null=不限)
);
```

- `methods() == null` 表示「不限方法」—— /auth 登录注册 GET/POST 都要放行, 用 null 偷懒。
- `Set.of(HttpMethod.GET)` —— 商品/分类只放行浏览(GET), POST/PUT/DELETE 掉进后面的鉴权 + needAdmin。**这正是垂直越权修复能成立的前提: 白名单只开了读口子, 写口子留给了 needAdmin。**
- **Java record 是什么**: Java 16+ 的「不可变数据载体」。一行等价于自动生成 private final 字段、全参构造器、prefix()/methods() 访问器、equals/hashCode/toString。适合纯数据、不可变的小对象(配置规则、DTO), 省掉几十行样板。注意访问器是 `prefix()` 不是 `getPrefix()`。

### 网关写法:响应式 WebFlux 风格(和单体拦截器的区别)

网关是 Spring Cloud Gateway, 基于 WebFlux(响应式), 不是传统 Servlet。写法和单体 JwtInterceptor 不同:

| 维度 | 单体 JwtInterceptor | 网关 AuthGlobalFilter |
|---|---|---|
| 返回值 | boolean(true 放行) | Mono<Void>(响应式流) |
| 拿请求 | HttpServletRequest | ServerWebExchange / ServerHttpRequest |
| 传 userId | ThreadLocal | HTTP header(X-User-Id) |

```java
// 中断响应(返 401): 不调 chain.filter() = 请求到此为止, 不转发下游
private Mono<Void> unauthorized(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    return exchange.getResponse().setComplete();   // setComplete = 结束响应链
}

// 放行并透传: mutate() 改请求, 塞 header
ServerHttpRequest mutated = request.mutate()
        .header("X-User-Id", String.valueOf(userId))
        .header("X-User-Role", role == null ? "0" : String.valueOf(role))
        .build();
return chain.filter(exchange.mutate().request(mutated).build());
```

- `request.mutate()` —— 请求对象是不可变的, 想加 header 必须 mutate() 造一个新的。这是响应式/函数式风格的常态。
- `chain.filter(exchange)` = 放行到下游; 不调它 = 拦下。返回 setComplete() 就是「到此为止」。
- `getOrder()` 返 -100 —— filter 执行顺序, 越小越先。鉴权要在路由处理之前跑, 所以给个很小的值抢先执行。

### 垂直越权修复:needAdmin(真实代码)

```java
private boolean needAdmin(String path, HttpMethod method) {
    if (path.startsWith("/admin/")) {           // ① 后台接口: /admin/** 一律要管理员
        return true;
    }
    boolean isWrite = method == HttpMethod.POST  // ② 只拦写操作; GET 放行(C 端要浏览商品)
            || method == HttpMethod.PUT
            || method == HttpMethod.DELETE;
    if (!isWrite) {
        return false;   // GET 短路, 不走后面前缀判断
    }
    return path.startsWith("/product")          // ③ 管理资源写操作: 命中任一前缀 → 要管理员
            || path.startsWith("/seckill/activity");
}
```

```java
if (needAdmin(path, method)) {
    if (role == null || role.intValue() != 1) {    // 老 token 没 role → null → 按非管理员
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);   // 403
        return exchange.getResponse().setComplete();
    }
}
```

修复的两个漏洞:
- **商品写接口裸奔**: POST/PUT/DELETE /product 挂在 /product 下(不是 /admin/), 原来网关只拦 /admin/, 导致任何登录用户都能改任意商品。
- **秒杀发布裸奔**: POST /seckill/activity 同理, 普通用户能自己发布「1 分钱抢 iPhone」。

### ⭐ 前缀锁定的坑(最该记住的一点)

`startsWith` 是前缀匹配。秒杀这里三个端点全是 POST, 必须精确区分:

| 端点 | 谁用 | /seckill/activity 命中? |
|---|---|---|
| POST /seckill/activity | 管理员发布活动 | ✅ 命中 → 要 admin |
| POST /seckill/123 (抢购) | C 端用户 | ❌ 不命中(123≠activity)→ 放行 |
| POST /seckill/pay/xxx (支付) | C 端用户 | ❌ 不命中 → 放行 |

**如果图省事写成 `path.startsWith("/seckill")`, 三条全命中, 用户抢购和支付全部 403, 秒杀功能直接瘫痪。** 这就是「前缀要锁到刚好那一段」的血泪教训。

### 水平越权防护:归属校验模板(真实代码)

**模板 A:查 + 校验归属**(地址, 写得很规范):

```java
private Address getAndCheckOwn(Long id, Long currentUserId) {
    Address addr = addressService.getById(id);
    if (addr == null) {
        throw new BusinessException(404, "地址不存在");        // 不存在
    }
    if (!addr.getUserId().equals(currentUserId)) {
        throw new BusinessException(403, "无权访问该地址");     // 不是你的 → 越权
    }
    return addr;
}
```

为什么显式传 currentUserId 而不在方法里 getUserId() —— 让方法对外部状态零依赖, 单元测试直接传参, 不用 mock ThreadLocal, 更清晰更易测。

**模板 B:列表强制过滤**:

```java
w.eq("user_id", userId)   // ⭐ 强制只查自己的, 不给前端任何传 userId 的机会
```

订单同理: getOrderDetail / cancelOrder / payOrder / signOrder 每个都有 `if (!order.getUserId().equals(userId)) throw 403`; listMyOrders 用 `eq("user_id", userId)`。**核心: 光在 Controller 传 userId 不算数, 必须在 Service 层真的拿它做比对/过滤。**
总流程
AuthGlobalFilter 从上到下的完整流程

一个请求进来 filter(exchange):

① 黑名单 BLACK_LIST     → 命中(/user/internal)→ 直接 403,结束
        │ 没命中
        ▼
② 白名单 WHITE_LIST     → 命中(path+method)→ 直接放行,不验 token!
   (path + method 判断)         例: GET /product 命中 → 免登录浏览
        │ 没命中(说明这请求需要登录)
        ▼
③ 取 Authorization      → 没有 / 不是 Bearer → 401
        ▼
④ 解析 token            → 拿 userId + role;解析失败 → 401
        │                    ↑↑↑ 这一步才叫「鉴权/认证」——证明你是谁
        ▼
⑤ needAdmin(path,method)→ 需要 admin 且 role≠1 → 403
   (path + method 判断)         ↑↑↑ 这一步叫「授权」——判断你够不够格
        │ 通过
        ▼
⑥ 塞 X-User-Id/X-User-Role header → 放行到下游

### 安全思想:消除攻击面 > 加固攻击面

订单有个裸奔的 `PUT /order/{orderId}/ship`(无任何身份校验), 但排查发现已存在正规入口 `PUT /admin/order/{id}/ship`(走 /admin/ 受网关保护, 调的是同一个 shipOrder())。处理方式: 直接删掉裸奔那个, 不打补丁。

```java
// ⑥ 发货接口已删除 (安全修复):
//    发货是管理员操作, 已由 AdminOrderController 的 PUT /admin/order/{id}/ship 提供,
//    受网关 role=1 保护; 原来这里的 PUT /order/{orderId}/ship 无校验且功能重复 → 删除。
```

**能消除一个入口, 就别去加固它。少一个入口 = 少一处要维护、将来可能忘记校验的地方。** 删方法后顺手清掉没人用的 import(Java 未使用 import 不报错, 但留着是垃圾、误导读者)。

### 本轮排查总账

| 资源 | 越权类型 | 结果 |
|---|---|---|
| 商品 增/删/改 | 垂直 | ❌→✅ 网关 /product 写要 admin |
| 秒杀活动发布 | 垂直 | ❌→✅ 网关 /seckill/activity 要 admin |
| 订单发货 | 垂直 | ❌→✅ 删冗余端点, 走 /admin/order/{id}/ship |
| 地址 增删改查 | 水平 | ✅ 本就有 getAndCheckOwn |
| 订单 取消/支付/签收 | 水平 | ✅ 本就有 order.getUserId().equals() |

**规律: 垂直越权几乎清一色是「管理接口没走 /admin/ 前缀」。以后新增管理功能, 路径挂 /admin/ 下就自动安全。**

## 80.7 数据规范:分类图标该存什么 <a id="sec817"></a>

category.icon 现存 phone.png(裸名)、svsv(乱码)都是脏数据。三种合法形态:

| 形态 | 存的内容 | 前端渲染 | 判定 |
|---|---|---|---|
| 完整 URL(推荐) | http://localhost:9000/mini-mall/x.png | `<img :src="icon">` | ✅ 走 MinIO, 正规 |
| emoji | 📱 | `<span>{{icon}}</span>` | 偷懒/纯学习 |
| 裸文件名 | phone.png | 浏览器同目录找 → 404 | ❌ 永远错 |

结论: 已经有 file-service + MinIO, 就该走完整 URL——上传图 → 拿 {url, objectName} → `UPDATE category SET icon='<完整url>'`。既修数据, 也把 MinIO 链路端到端验证一遍。

## 80.8 上线准备:Docker 化(刚起步) <a id="sec818"></a>

### Dockerfile 模板

```dockerfile
FROM eclipse-temurin:21-jre-jammy        # ① JRE 21, 比 JDK 小 100MB+; jammy=Ubuntu22.04 基座
WORKDIR /app                             # ② 容器内工作目录 = cd /app
COPY target/mini-mall-user-*.jar app.jar # ③ 拷 jar, * 通配版本号(升版不改 Dockerfile)
EXPOSE 9001                              # ④ 仅文档作用, 真映射靠 compose 的 ports:
ENTRYPOINT ["java", "-jar", "app.jar"]   # ⑤ JSON 数组=exec form, kill 时能优雅关 JVM
```

### 环境变量抽取(上线前最关键改造)

```yaml
# 改前(只能本机跑): localhost 在容器里指容器自己, 连不到 MySQL
url: jdbc:mysql://localhost:3306/mini_mall

# 改后(本机/容器/服务器通用)
url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DB:mini_mall}
```

`${MYSQL_HOST:localhost}` 语法 = 有环境变量用环境变量, 没有就用冒号后默认值。本机跑完全不受影响; 上容器时 compose 注入 MYSQL_HOST=mysql(容器名, Docker 内部 DNS 能解析)。

**服务器选型结论**: 阿里云香港轻量 4C8G(免备案、年付 ¥1899)+ Ubuntu 22.04 + Docker Compose(不上 K8s, 4C8G 跑 K8s 自己吃掉 2GB)+ Let's Encrypt 免费证书。先本机 Docker 化跑通, 再上服务器, 别在按天烧钱的机器上装环境。

## 80.9 环境 / 打包 踩坑速查 <a id="sec819"></a>

| 坑 | 现象 | 正解 |
|---|---|---|
| 系统 JDK 8 | java -jar 跑不起 SB 3.3.5 | 用 `D:\jdk-21.0.11\bin\java.exe` 绝对路径 |
| Get-NetTCPConnection -State Listen | 服务在跑却返回 0 条, 误判 DOWN | `netstat -an \| findstr LISTENING` / Test-NetConnection |
| Start-Process 日志中文路径 | silent fail | 日志目录用 ASCII `C:\mini-mall-logs\` |
| .ps1 脚本 | 被杀软自动删 | 用内联 PowerShell, 不落地 .ps1 |
| fat jar 改 yml | 改了配置不重打包不生效 | 改 src/main/resources 下任何文件都得重 mvn package |
| 网关 yml 加路由没重打 | /auth/** 全 404 | 抽服务/改路由, 网关 yml 一改就重打 gateway |

## 80.10 这一章的主线(一条链, 不是散点) <a id="sec820"></a>

```
ADMIN 加 role 字段 ──► JWT 带 role claim ──► 网关白名单只开 GET ──► needAdmin 拦写操作
   (RBAC 地基)          (身份载体)          (读写分离)           (垂直越权修复)

DTO 限能填的字段 ──► 防提权    VO 限能看的字段 ──► 防泄露
        └────────────► 配合 Service 归属校验 ──► 水平越权防护

FILE/MinIO ──► 文件存对象存储、库里只存 URL ──► 修分类图标脏数据
```

看得出: ADMIN 铺的 role、白名单铺的读写分离、DTO/VO 铺的字段边界, 全是为本会话的越权修复做的铺垫。**安全不是最后补的, 是一层层设计出来的。**

---

**C 端商城 + 越权安全阶段完毕**。mini-mall-cloud 至此具备 RBAC 权限、对象存储、DTO/VO 边界、系统性越权防护。下一步: 上线 Docker 化(80.8 已起步)+ 沙箱支付接入。
