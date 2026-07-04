# mini-mall-ai 智能客服 Agent 设计（第一期）

- 日期：2026-07-04
- 状态：已确认，待实现
- 所属项目：mini-mall-cloud（Spring Cloud Alibaba 电商微服务）

## 1. 目标与定位

给 mini-mall-cloud 电商新增一个 AI Agent 微服务 `mini-mall-ai`。终极定位是**全能 AI 客服**（客服问答 + 导购 + 订单助手混合），但**分三期实现**，避免一次堆成半成品。

本 spec 只覆盖**第一期**：RAG 智能客服问答 + 商品查询 function-calling 工具。第一期把 agent 的两条腿——**RAG（静态知识检索）**和 **function-calling（动态数据工具）**——都立起来，作为后续加工具的地基。

学习目标：借此练 RAG / Agent（对齐阿里 Java 面试准备方向），技术栈保持 Java 以贴合项目和岗位。

## 2. 技术选型（已确认）

| 维度 | 选型 | 理由 |
|------|------|------|
| 语言/框架 | **Java + LangChain4j** | 新增一个 Java 微服务无缝进现有 Nacos/Gateway/Feign 体系；LangChain4j 的 agent/RAG/工具抽象成熟、社区例子多；面试 Java 岗更贴合 |
| 生成模型 | **DeepSeek API** | 用过、function-calling 成熟、中文强、便宜；OpenAI 兼容接口 Java 好接 |
| embedding | **本地 bge-m3 @ Ollama** | 中文向量效果好、免费、用上本地 GPU；DeepSeek 无 embedding 接口，必须单独选 |
| 向量库 | **Redis Stack（RediSearch）** | 复用 Redis 技术栈；普通 Redis 无向量模块，用 Redis Stack；独立容器不动业务 Redis |
| 集成方式 | **新增 mini-mall-ai 微服务**（端口 9009 / Sentinel 8726） | 与现有 9 个微服务并列 |

> **关键技术点**：DeepSeek 只提供对话（chat）接口，**没有 embedding 接口**。RAG 里「文本→向量」这一环独立于 DeepSeek，由本地 bge-m3 承担。

## 3. 架构总览

```
C端商城(web) 悬浮聊天窗 ──POST /ai/chat──▶ 网关 ──▶ mini-mall-ai (:9009)
                                                      │
                          ┌───────────────────────────┴──────────────────────┐
                          │  LangChain4j AiServices (声明式 Agent 接口)        │
                          │      │ DeepSeek 判断: 该查知识库? 还是调工具?       │
                          │      ├──▶ RAG 检索器 ─▶ Redis向量库 (政策/FAQ)      │
                          │      └──▶ @Tool 商品查询 ─Feign─▶ product/search    │
                          │      最后 DeepSeek 把检索结果/工具结果组织成回答    │
                          └────────────────────────────────────────────────────┘
      依赖: DeepSeek API(生成) · bge-m3@Ollama:11434(embedding,1024维) · Redis Stack:6380(向量)
```

## 4. mini-mall-ai 微服务分层

| 层 | 组件 | 职责 |
|----|------|------|
| controller | `AiChatController` | `POST /ai/chat` 收问题、返回答复 |
| agent | `ShoppingAssistant`（LangChain4j `AiServices` 接口） | agent 大脑，声明式绑定 LLM + RAG + Tools + 记忆 |
| tool | `ProductQueryTool`（`@Tool` 方法） | 商品查询工具，内部 Feign 调 product |
| rag | `KnowledgeRetriever` + `KnowledgeImportService` | RAG 检索器 + 知识库灌入 |
| config | `DeepSeekConfig` / `EmbeddingConfig` / `RedisVectorConfig` | 三个基础设施的 Bean |
| client | `ProductFeignClient`（+ fallback） | 调 product 搜索接口 |

模块结构遵循现有微服务惯例（`com.minimall.ai` 包，`@MapperScan`/`@EnableFeignClients`/`@ComponentScan("com.minimall")` 启动类，Nacos 注册，Sentinel）。

## 5. 两条核心数据流

**RAG 链路（静态知识）**：
问题 → bge-m3 转 1024 维向量 → Redis 检索最相似政策片段 → 拼进 prompt → DeepSeek 生成回答

**Tool 链路（动态数据）**：
问题 → DeepSeek 判断需要商品数据 → 自动调 `@Tool` 商品查询 → Feign 查实时库存/价格 → 结果回给 DeepSeek 组织话术

> **核心设计判断（静态 RAG / 动态工具分工）**：静态政策（退货/运费/优惠券规则）走 RAG 向量检索；动态数据（商品/价格/库存）走 function-calling 实时查库。**商品不进向量库**——因为价格库存会变，灌进向量库会过期。这条分工是整个设计的关键。

## 6. 知识库设计（第一期）

手写几篇 markdown 政策文档，内容覆盖：退货退款规则、运费说明、支付方式、优惠券使用、秒杀规则。

`KnowledgeImportService` 在服务启动时：读取 md → 切片（chunk）→ bge-m3 embedding → 存入 Redis 向量库。第一期用「启动时灌入」的简单方式，不做后台管理 UI。商品信息**不进**知识库（走工具）。

## 7. 前端交互

`mini-mall-cloud-web` 加一个右下角悬浮聊天窗组件 `AiChat.vue`，调 `POST /ai/chat`。

**第一期非流式**：一次返回完整答案（实现简单）。打字机流式输出（SSE）作为第二期增强，本期不做。

## 8. 网关与鉴权

网关 `AuthGlobalFilter` 加 `/ai/**` 路由 → `lb://mini-mall-ai`。第一期 `/ai/chat` **要求登录**（网关校验 JWT 后塞 `X-User-Id` 转发），为第二期订单助手和多轮记忆按用户隔离铺路。属于 C 端写操作，进 `isCEndWrite` 白名单（不需要 admin 权限）。

## 9. 部署依赖与已验证参数

| 组件 | 地址/参数 | 状态（2026-07-04 实测） |
|------|-----------|------------------------|
| Ollama + bge-m3 | `http://localhost:11434`，模型 `bge-m3`，输出 **1024 维** | ✅ 已验证出向量 |
| Redis Stack | `localhost:6380`（容器内 6379），RediSearch `search` 模块 ver 21020 | ✅ 已验证 PING + 模块加载 |
| DeepSeek API | OpenAI 兼容 endpoint + API key | ⏳ 写代码时配 |

> Redis 向量索引建索引时必须声明 `DIM 1024`，与 bge-m3 输出对齐。

## 10. 配置与密钥外置

`mini-mall-ai/application.yml` 关键配置：Ollama 地址、Redis Stack 地址、DeepSeek base-url + api-key。

**DeepSeek api-key 走已有约定**（同支付宝私钥）：真实 `application.yml` 进 `.gitignore`，配套 `application.yml.example` 占位；生产用环境变量 `DEEPSEEK_API_KEY` 覆盖。

## 11. 第一期范围与 YAGNI 边界

**本期做**：
- mini-mall-ai 微服务骨架（Nacos/Sentinel/Feign）
- RAG：知识库灌入 + Redis 向量检索 + bge-m3 embedding
- function-calling：ProductQueryTool（Feign 调 product 搜索）
- LangChain4j AiServices 编排（LLM + RAG + Tool + 内存 ChatMemory）
- `POST /ai/chat` 接口 + 网关路由/鉴权
- 前端悬浮聊天窗（非流式）

**本期不做（明确划界）**：
- ❌ 订单助手（第三期）
- ❌ 流式输出 SSE（第二期增强）
- ❌ 多轮对话持久化（第一期用内存 ChatMemory，重启清空）
- ❌ 知识库后台管理 UI（第一期手写 md + 启动灌入）

## 12. 分期路线图

- **第一期（本 spec）**：RAG 客服问答 + 商品查询工具 —— 立地基（框架 + RAG + function-calling）
- **第二期**：导购增强 —— 更多商品相关工具 + 流式输出
- **第三期**：订单助手 —— function-calling 调 order 接口（查订单/取消，带权限透传）

## 13. 测试策略

- **地基连通**（已完成）：bge-m3 出向量、Redis Stack RediSearch 就绪
- **RAG 链路**：灌入政策后，问「怎么退货」应检索到退货政策片段并生成对应回答
- **Tool 链路**：问「推荐 300 元内的手机」应触发 ProductQueryTool，Feign 查到实时商品
- **鉴权**：未登录访问 `/ai/chat` 应被网关拦（401）
- **端到端**：前端聊天窗发问 → 收到合理答复

## 14. 风险与注意点

- Ollama 需常驻（作为 embedding 服务）；Redis Stack 容器需常驻——都属新增运维依赖。
- LangChain4j 版本与 Spring Boot 3.3.5 / JDK 21 的兼容需在引依赖时确认。
- DeepSeek function-calling 的工具描述（`@Tool` 注解的 description）写得好坏直接影响 agent 是否正确调用工具，需仔细打磨。
- 内存 ChatMemory 在多实例部署下不共享（第一期单实例，暂不涉及）。
