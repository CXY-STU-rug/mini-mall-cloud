# mini-mall-ai 智能客服 Agent（第一期）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 mini-mall-cloud 新增 `mini-mall-ai` 微服务，第一期实现 RAG 客服问答 + 商品查询 function-calling 工具。

**Architecture:** LangChain4j 的 `AiServices` 做 agent 编排：DeepSeek 生成、bge-m3@Ollama 做 embedding、Redis Stack 存向量。静态政策走 RAG 向量检索，动态商品走 `@Tool` function-calling 实时查库。

**Tech Stack:** Java 17 · Spring Boot 3.3.5 · Spring Cloud Alibaba（Nacos/Sentinel）· LangChain4j · DeepSeek API · Ollama(bge-m3) · Redis Stack(RediSearch)

## Global Constraints

- 编译 `java.version=17`（父 pom 统一），运行用 D 盘 JDK 21：`D:\jdk-21.0.11\bin\java.exe -jar`
- 新微服务端口 **9009**，Sentinel client 端口 **8726**（错开现有 8719~8725）
- 包名 `com.minimall.ai`；启动类 `@EnableFeignClients` + `@ComponentScan("com.minimall")`（复用 common-core）
- embedding：Ollama `http://localhost:11434`，模型 `bge-m3`，**向量维度 1024**（Redis 索引 DIM 必须 1024）
- 向量库：Redis Stack `localhost:6380`（**不是**业务 Redis 6379）
- DeepSeek key 外置：真实 `application.yml` 进 `.gitignore`，配 `application.yml.example`，生产用环境变量 `DEEPSEEK_API_KEY`
- LangChain4j 依赖版本：引依赖时统一用一个属性 `${langchain4j.version}`，取当前最新稳定 1.x（引入后 `mvn -q dependency:tree` 确认 4 个子包版本一致、能解析）
- 日志目录用 ASCII：`C:\mini-mall-logs\`（中文路径会 silent fail）
- 每个 Task 结束必须能独立验证 + 一次 commit

---

### Task 1: 微服务骨架（能起来 + 注册 Nacos）

**Files:**
- Modify: `pom.xml`（父 pom `<modules>` 加 `mini-mall-ai`）
- Create: `mini-mall-ai/pom.xml`
- Create: `mini-mall-ai/src/main/java/com/minimall/ai/MiniMallAiApplication.java`
- Create: `mini-mall-ai/src/main/resources/bootstrap.yml`
- Create: `mini-mall-ai/src/main/resources/application.yml`（真实，含 key，本地）
- Create: `mini-mall-ai/src/main/resources/application.yml.example`（占位模板，进 git）
- Modify: `.gitignore`（加 `mini-mall-ai/src/main/resources/application.yml`）

**Interfaces:**
- Produces: 一个可启动的 Spring Boot 服务，注册到 Nacos 服务名 `mini-mall-ai`，端口 9009

- [ ] **Step 1: 父 pom 加 module**

在 `pom.xml` 的 `<modules>` 里加一行（参考 payment 那行位置）：
```xml
<module>mini-mall-ai</module>
```

- [ ] **Step 2: 写 mini-mall-ai/pom.xml**

parent 指向父 pom；依赖：`common-core`（复用 Result/异常）、`spring-boot-starter-web`、`spring-cloud-starter-alibaba-nacos-discovery`、`spring-cloud-starter-alibaba-sentinel`、`spring-cloud-starter-openfeign`、LangChain4j 四件套（`langchain4j`、`langchain4j-open-ai`、`langchain4j-ollama`、`langchain4j-redis`，版本用 `${langchain4j.version}`）、`lombok`。加 `spring-boot-maven-plugin`（打 fat jar，注意 payment 那期的坑：缺插件不打可执行 jar）。

- [ ] **Step 3: 写启动类**

```java
package com.minimall.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableFeignClients
@ComponentScan("com.minimall")   // 扫到 common-core 的组件
public class MiniMallAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MiniMallAiApplication.class, args);
    }
}
```

- [ ] **Step 4: 写 bootstrap.yml + application.yml**

`bootstrap.yml`：`spring.application.name: mini-mall-ai`（抄 payment 的 bootstrap）。
`application.yml`：port 9009、Nacos discovery 127.0.0.1:8848、Sentinel dashboard 127.0.0.1:8858 port 8726 eager。先不加 LangChain4j 配置（Task 2 再加）。

- [ ] **Step 5: 写 application.yml.example + 改 .gitignore**

`.example` 把 `application.yml` 复制一份（第一期还没 key，先占位一致）。`.gitignore` 加 `mini-mall-ai/src/main/resources/application.yml`。

- [ ] **Step 6: 编译**

Run: `mvn -q -pl mini-mall-ai -am clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: 启动验证注册 Nacos**

启动（需 Nacos 8848 在线）：`D:\jdk-21.0.11\bin\java.exe -jar mini-mall-ai/target/mini-mall-ai-*.jar`
验证：Nacos 控制台服务列表出现 `mini-mall-ai`，或 `netstat -an | findstr "9009"` 看到 LISTENING。

- [ ] **Step 8: Commit**

```bash
git add pom.xml .gitignore mini-mall-ai/pom.xml mini-mall-ai/src/main/java mini-mall-ai/src/main/resources/bootstrap.yml mini-mall-ai/src/main/resources/application.yml.example
git commit -m "feat(ai): mini-mall-ai 微服务骨架 + Nacos 注册"
```

---

### Task 2: 三个基础设施 Bean + 连通性验证

**Files:**
- Create: `mini-mall-ai/src/main/java/com/minimall/ai/config/AiProperties.java`
- Create: `mini-mall-ai/src/main/java/com/minimall/ai/config/LangChainConfig.java`
- Modify: `mini-mall-ai/src/main/resources/application.yml`（+ ai 配置段）
- Test: `mini-mall-ai/src/test/java/com/minimall/ai/InfraConnectivityTest.java`

**Interfaces:**
- Produces: Spring 容器里三个 Bean —— `ChatLanguageModel`（DeepSeek）、`EmbeddingModel`（bge-m3）、`EmbeddingStore<TextSegment>`（Redis，1024 维）

- [ ] **Step 1: application.yml 加配置段**

```yaml
ai:
  deepseek:
    base-url: https://api.deepseek.com
    api-key: 你的DeepSeekKey       # example 里写 YOUR_DEEPSEEK_API_KEY
    model: deepseek-chat
  ollama:
    base-url: http://localhost:11434
    embedding-model: bge-m3
  redis:
    host: localhost
    port: 6380
    dimension: 1024
```

- [ ] **Step 2: 写 AiProperties（@ConfigurationProperties prefix=ai）**

三个嵌套静态类 DeepSeek/Ollama/Redis，字段对应上面 yaml。加 `@Component`。

- [ ] **Step 3: 写 LangChainConfig 三个 @Bean**

```java
@Bean
ChatLanguageModel chatModel(AiProperties p) {
    return OpenAiChatModel.builder()
        .baseUrl(p.getDeepseek().getBaseUrl())
        .apiKey(p.getDeepseek().getApiKey())
        .modelName(p.getDeepseek().getModel())
        .build();
}

@Bean
EmbeddingModel embeddingModel(AiProperties p) {
    return OllamaEmbeddingModel.builder()
        .baseUrl(p.getOllama().getBaseUrl())
        .modelName(p.getOllama().getEmbeddingModel())
        .build();
}

@Bean
EmbeddingStore<TextSegment> embeddingStore(AiProperties p) {
    return RedisEmbeddingStore.builder()
        .host(p.getRedis().getHost())
        .port(p.getRedis().getPort())
        .dimension(p.getRedis().getDimension())
        .build();
}
```

- [ ] **Step 4: 写连通性测试（真连外部服务，集成测试）**

```java
@SpringBootTest
class InfraConnectivityTest {
    @Autowired EmbeddingModel embeddingModel;
    @Autowired EmbeddingStore<TextSegment> store;

    @Test
    void embed_returns_1024_dim() {
        Embedding e = embeddingModel.embed("怎么退货").content();
        assertThat(e.dimension()).isEqualTo(1024);
    }

    @Test
    void store_add_and_search() {
        TextSegment seg = TextSegment.from("退货需在签收后7天内申请");
        Embedding e = embeddingModel.embed(seg).content();
        store.add(e, seg);
        Embedding q = embeddingModel.embed("如何申请退货").content();
        var matches = store.search(EmbeddingSearchRequest.builder()
            .queryEmbedding(q).maxResults(1).build()).matches();
        assertThat(matches).isNotEmpty();
        assertThat(matches.get(0).embedded().text()).contains("退货");
    }
}
```

- [ ] **Step 5: 跑测试**

Run: `mvn -q -pl mini-mall-ai test -Dtest=InfraConnectivityTest`（需 Ollama + Redis Stack 在线）
Expected: 2 passed。embed 出 1024 维、存进 Redis 再检索能召回。

- [ ] **Step 6: Commit**

```bash
git add mini-mall-ai/src/main/java/com/minimall/ai/config mini-mall-ai/src/test mini-mall-ai/src/main/resources/application.yml.example
git commit -m "feat(ai): DeepSeek/bge-m3/Redis 三基础设施 Bean + 连通性测试"
```

---

### Task 3: 知识库文档 + 启动灌入

**Files:**
- Create: `mini-mall-ai/src/main/resources/knowledge/*.md`（退货/运费/支付/优惠券/秒杀 政策）
- Create: `mini-mall-ai/src/main/java/com/minimall/ai/rag/KnowledgeImportService.java`

**Interfaces:**
- Consumes: `EmbeddingModel`、`EmbeddingStore<TextSegment>`（Task 2）
- Produces: 启动后知识片段已灌入 Redis 向量库

- [ ] **Step 1: 写几篇政策 md**

`knowledge/退货政策.md`、`运费说明.md`、`支付方式.md`、`优惠券规则.md`、`秒杀规则.md`。每篇几段真实规则文字（内容自定，覆盖常见问题）。

- [ ] **Step 2: 写 KnowledgeImportService**

`@Component` 实现 `ApplicationRunner`（启动跑一次）：用 LangChain4j `DocumentSplitters.recursive(300, 30)` 把每篇 md 切片 → `embeddingModel.embed` → `embeddingStore.addAll`。加一个 Redis flag key 或每次清空重灌（第一期简单：每次启动先 `removeAll` 再灌，避免重复堆积）。

```java
@Override
public void run(ApplicationArguments args) {
    var resources = new PathMatchingResourcePatternResolver()
        .getResources("classpath:knowledge/*.md");
    List<TextSegment> segments = new ArrayList<>();
    for (Resource r : resources) {
        Document doc = Document.from(new String(r.getInputStream().readAllBytes(), UTF_8));
        segments.addAll(DocumentSplitters.recursive(300, 30).split(doc));
    }
    var embeddings = embeddingModel.embedAll(segments).content();
    embeddingStore.addAll(embeddings, segments);
    log.info("知识库灌入完成: {} 片段", segments.size());
}
```

- [ ] **Step 3: 启动验证**

启动服务，日志出现「知识库灌入完成: N 片段」；RedisInsight（localhost:8001）能看到向量数据。

- [ ] **Step 4: Commit**

```bash
git add mini-mall-ai/src/main/resources/knowledge mini-mall-ai/src/main/java/com/minimall/ai/rag/KnowledgeImportService.java
git commit -m "feat(ai): 政策知识库 + 启动灌入向量库"
```

---

### Task 4: RAG 检索器 + 纯 RAG 问答

**Files:**
- Create: `mini-mall-ai/src/main/java/com/minimall/ai/agent/ShoppingAssistant.java`（接口，先只 RAG）
- Modify: `mini-mall-ai/src/main/java/com/minimall/ai/config/LangChainConfig.java`（+ ContentRetriever + AiService Bean）
- Test: `mini-mall-ai/src/test/java/com/minimall/ai/RagQaTest.java`

**Interfaces:**
- Consumes: `ChatLanguageModel`、`EmbeddingModel`、`EmbeddingStore`（Task 2）
- Produces: `ShoppingAssistant.chat(String userId, String message)` 返回 String

- [ ] **Step 1: 写 ShoppingAssistant 接口**

```java
public interface ShoppingAssistant {
    String chat(@MemoryId String userId, @UserMessage String message);
}
```

- [ ] **Step 2: LangChainConfig 加 ContentRetriever + AiService**

```java
@Bean
ContentRetriever contentRetriever(EmbeddingStore<TextSegment> store, EmbeddingModel model) {
    return EmbeddingStoreContentRetriever.builder()
        .embeddingStore(store).embeddingModel(model)
        .maxResults(3).minScore(0.6).build();
}

@Bean
ShoppingAssistant shoppingAssistant(ChatLanguageModel chat, ContentRetriever retriever) {
    return AiServices.builder(ShoppingAssistant.class)
        .chatLanguageModel(chat)
        .contentRetriever(retriever)
        .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(10))
        .build();
}
```

- [ ] **Step 3: 写 RAG 问答测试**

```java
@SpringBootTest
class RagQaTest {
    @Autowired ShoppingAssistant assistant;
    @Test
    void ask_return_policy() {
        String ans = assistant.chat("test-user", "我买的东西怎么退货？");
        assertThat(ans).isNotBlank();
        // 断言命中知识库内容(按你退货政策.md里的关键词，如"7天")
        assertThat(ans).contains("7");
    }
}
```

- [ ] **Step 4: 跑测试**

Run: `mvn -q -pl mini-mall-ai test -Dtest=RagQaTest`（需 Ollama+Redis+DeepSeek key）
Expected: PASS，回答里含退货政策要点。

- [ ] **Step 5: Commit**

```bash
git add mini-mall-ai/src/main/java/com/minimall/ai/agent mini-mall-ai/src/main/java/com/minimall/ai/config/LangChainConfig.java mini-mall-ai/src/test/java/com/minimall/ai/RagQaTest.java
git commit -m "feat(ai): RAG 检索器 + 纯知识问答链路"
```

---

### Task 5: 商品查询 function-calling 工具

**Files:**
- Create: `mini-mall-ai/src/main/java/com/minimall/ai/client/ProductFeignClient.java`（+ fallback）
- Create: `mini-mall-ai/src/main/java/com/minimall/ai/client/dto/ProductBrief.java`（副本 DTO）
- Create: `mini-mall-ai/src/main/java/com/minimall/ai/tool/ProductQueryTool.java`

**Interfaces:**
- Consumes: product 服务的搜索接口（确认现有路径，如 `GET /product?keyword=&maxPrice=` 或 `/search/product`）
- Produces: `ProductQueryTool` 被 AiService `.tools()` 注册；`@Tool` 方法 `queryProducts(String keyword, Integer maxPrice)` 返回 String

- [ ] **Step 1: 确认 product 搜索接口**

先看 product/search 现有可用的查询接口签名（关键词 + 价格上限），确定 Feign 要调哪个。ProductBrief 只留 agent 要用的字段（name/price/stock）。

- [ ] **Step 2: 写 ProductFeignClient + fallback**

`@FeignClient(name="mini-mall-product", fallback=...)`，方法对应 Step1 确认的接口。fallback 返回空列表并 log。

- [ ] **Step 3: 写 ProductQueryTool**

```java
@Component
public class ProductQueryTool {
    private final ProductFeignClient client;
    public ProductQueryTool(ProductFeignClient client) { this.client = client; }

    @Tool("根据关键词和最高价格查询在售商品。用户想找/推荐商品、问有没有某类商品时调用。")
    public String queryProducts(String keyword, Integer maxPrice) {
        List<ProductBrief> list = client.search(keyword, maxPrice);
        if (list.isEmpty()) return "没有找到符合条件的在售商品。";
        return list.stream().limit(5)
            .map(p -> String.format("%s ￥%s 库存%d", p.getName(), p.getPrice(), p.getStock()))
            .collect(Collectors.joining("\n"));
    }
}
```

- [ ] **Step 4: 手动验证工具（先单独 test 调用）**

写一个 `@SpringBootTest` 直接调 `tool.queryProducts("手机", 300)`（需 product 服务在线），断言返回非空文本。

- [ ] **Step 5: Commit**

```bash
git add mini-mall-ai/src/main/java/com/minimall/ai/client mini-mall-ai/src/main/java/com/minimall/ai/tool
git commit -m "feat(ai): 商品查询 function-calling 工具 + product Feign"
```

---

### Task 6: 组装完整 Agent + Chat 接口

**Files:**
- Modify: `LangChainConfig.java`（AiService `.tools(productQueryTool)`）
- Create: `mini-mall-ai/src/main/java/com/minimall/ai/controller/AiChatController.java`
- Create: `mini-mall-ai/src/main/java/com/minimall/ai/dto/ChatRequest.java`
- Test: `mini-mall-ai/src/test/java/com/minimall/ai/ToolCallingTest.java`

**Interfaces:**
- Consumes: `ShoppingAssistant`、`ProductQueryTool`
- Produces: `POST /ai/chat` body `{message}`，header `X-User-Id`，返回 `Result<String>`

- [ ] **Step 1: AiService 注入工具**

`shoppingAssistant` Bean 的 builder 加 `.tools(productQueryTool)`（构造参数注入 ProductQueryTool）。

- [ ] **Step 2: 写 AiChatController**

```java
@RestController
@RequestMapping("/ai")
public class AiChatController {
    private final ShoppingAssistant assistant;
    public AiChatController(ShoppingAssistant a) { this.assistant = a; }

    @PostMapping("/chat")
    public Result<String> chat(@RequestHeader("X-User-Id") String userId,
                               @RequestBody ChatRequest req) {
        return Result.success(assistant.chat(userId, req.getMessage()));
    }
}
```

- [ ] **Step 3: 工具调用测试**

```java
@Test
void ask_recommend_triggers_tool() {
    String ans = assistant.chat("u1", "推荐300元以内的手机");
    assertThat(ans).isNotBlank();   // 应触发 ProductQueryTool 走实时查库
}
```

- [ ] **Step 4: 跑测试 + 手动打接口**

`mvn test -Dtest=ToolCallingTest`（需 product 在线）。再起服务用 PowerShell 打 `POST /ai/chat`（带 X-User-Id）验证问答两类问题（退货=RAG、推荐=工具）都合理。

- [ ] **Step 5: Commit**

```bash
git add mini-mall-ai/src/main/java/com/minimall/ai/controller mini-mall-ai/src/main/java/com/minimall/ai/dto mini-mall-ai/src/main/java/com/minimall/ai/config/LangChainConfig.java mini-mall-ai/src/test
git commit -m "feat(ai): 组装 RAG+工具 agent + POST /ai/chat 接口"
```

---

### Task 7: 网关路由 + 鉴权

**Files:**
- Modify: `mini-mall-gateway/src/main/java/com/minimall/gateway/filter/AuthGlobalFilter.java`（isCEndWrite 加 /ai）
- Modify: `mini-mall-gateway/src/main/resources/application.yml`（+ ai-route）

**Interfaces:**
- Produces: `/ai/**` 经网关路由到 `lb://mini-mall-ai`，要求登录（塞 X-User-Id）

- [ ] **Step 1: 网关加路由**

gateway application.yml routes 加：
```yaml
- id: ai-route
  uri: lb://mini-mall-ai
  predicates:
    - Path=/ai/**
```

- [ ] **Step 2: isCEndWrite 加 /ai**

`AuthGlobalFilter.isCEndWrite()` 里把 `/ai` 加入 C 端写白名单（要登录、不要 admin）。确认 `/ai/**` 不落入 needAdmin，且校验通过后会塞 `X-User-Id`（controller 靠它做 memoryId）。

- [ ] **Step 3: 验证鉴权**

未带 token 打 `POST /ai/chat` → 401；带普通用户 token → 200 且能对话。

- [ ] **Step 4: Commit**

```bash
git add mini-mall-gateway/src/main/java/com/minimall/gateway/filter/AuthGlobalFilter.java mini-mall-gateway/src/main/resources/application.yml.example
git commit -m "feat(ai): 网关 /ai 路由 + 登录鉴权"
```

---

### Task 8: 前端悬浮聊天窗

**Files:**
- Create: `mini-mall-cloud-web/src/api/ai.ts`
- Create: `mini-mall-cloud-web/src/components/AiChat.vue`
- Modify: `mini-mall-cloud-web/src/layouts/MainLayout.vue`（挂载 AiChat）

**Interfaces:**
- Consumes: `POST /ai/chat`（走网关 /api 代理，带 web-token）
- Produces: 右下角悬浮聊天窗，能发问收答

- [ ] **Step 1: api/ai.ts**

```ts
import { http } from '@/utils/http'
export function chatWithAi(message: string) {
  return http.post<string>('/ai/chat', { message })
}
```

- [ ] **Step 2: AiChat.vue**

右下角悬浮按钮，点开一个聊天面板：消息列表（user/assistant 气泡）+ 输入框 + 发送。发送时 push 用户消息、调 `chatWithAi`、把返回 push 成 assistant 消息、loading 态。非流式（一次显示完整回复）。

- [ ] **Step 3: 挂载到 MainLayout**

MainLayout 末尾加 `<AiChat />`，让所有 C 端页面右下角都有。

- [ ] **Step 4: 前端验证**

`npm run build` 零 vue-tsc 错误；起前端 + 后端全链路，点开聊天窗，问「怎么退货」（RAG）、「推荐300内手机」（工具），都能收到合理回复。

- [ ] **Step 5: Commit**（前端独立仓库）

```bash
git add src/api/ai.ts src/components/AiChat.vue src/layouts/MainLayout.vue
git commit -m "feat(ai): C端悬浮 AI 客服聊天窗"
```

---

## Self-Review

**Spec coverage：**
- §2 技术选型 → Task 1(骨架)/Task 2(三 Bean) ✓
- §5 两条数据流 → Task 4(RAG链路)/Task 5+6(Tool链路) ✓
- §6 知识库 → Task 3 ✓
- §7 前端 → Task 8 ✓
- §8 网关鉴权 → Task 7 ✓
- §10 key 外置 → Task 1 Step5 + Task 2 example ✓
- §11 YAGNI(不做流式/订单/持久化) → 计划内无这些任务 ✓

**Placeholder scan：** 核心 Bean/接口/工具/controller 均给出真实代码；政策 md 内容、product 搜索接口签名标注「实现时按现有确认」——这两处是真实待定项（依赖现有接口和业务文案），非代码占位。

**Type consistency：** `ShoppingAssistant.chat(String userId, String message)` 全程一致；`ProductQueryTool.queryProducts(String, Integer)` 在 Task5 定义、Task6 注册一致；`EmbeddingStore<TextSegment>` 泛型贯穿 Task2/3/4。

**待实现时确认的外部事实（非占位，是要现场核对的）：**
1. LangChain4j 最新稳定 1.x 版本号 + 4 个子包坐标（尤其 `langchain4j-redis` 是否已并入 community）
2. product 服务现有「关键词+价格」搜索接口的确切路径与出参
3. `RedisEmbeddingStore` builder 是否需额外 indexName/prefix 参数
