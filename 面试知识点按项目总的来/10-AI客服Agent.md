# 10 AI 客服 Agent（LangChain4j，差异化亮点）

> 这是简历上和别人拉开差距的模块。面试官大概率不深挖技术细节，但会考察你**是否真理解 RAG / Function Calling / Agent 这几个词**。

## 整体架构一张图

```
用户: "有没有3000以内的手机推荐?"
  │  (网关验JWT → 注入 X-User-Id → /ai/chat)
  ▼
ShoppingAssistant.chat(userId, message)     ← 接口由 LangChain4j 动态实现
  │
  ├─① RAG: 问题向量化(本地Ollama bge-m3) → Redis向量库检索知识片段(top3, 相似度≥0.6)
  ├─② Memory: 按 userId 取最近10条对话历史
  ├─③ 组装 prompt = 系统提示 + 知识片段 + 历史 + 用户问题 → 发给 DeepSeek
  │
  ▼
DeepSeek 决定: 需要查商品! → 返回 tool_call(queryProducts, keyword=手机, maxPrice=3000)
  │
  ▼
ProductQueryTool.queryProducts() → Feign 调 product 服务 → 结果文本回传 DeepSeek
  │
  ▼
DeepSeek 综合知识+商品数据生成最终回答 → 返回用户
```

---

## 知识点 1：AiServices —— 声明接口，框架生成实现

**【面试怎么问】** 你的 AI 客服怎么搭的？

**【项目代码】** `mini-mall-ai/.../agent/ShoppingAssistant.java` + `config/LangChainConfig.java`：

```java
// 只声明接口, LangChain4j 运行时动态生成实现 (跟 Feign/MyBatis Mapper 同一思想!)
public interface ShoppingAssistant {
    String chat(@MemoryId String userId, @UserMessage String message);
}

@Bean
public ShoppingAssistant shoppingAssistant(ChatLanguageModel chatModel,
                                           ContentRetriever contentRetriever,
                                           ProductQueryTool productQueryTool) {
    return AiServices.builder(ShoppingAssistant.class)
            .chatLanguageModel(chatModel)                 // 生成模型: DeepSeek(OpenAI兼容协议)
            .contentRetriever(contentRetriever)           // RAG检索器: 问答前自动检索知识
            .chatMemoryProvider(memoryId ->               // 每个userId一份记忆, 最近10条
                    MessageWindowChatMemory.withMaxMessages(10))
            .tools(productQueryTool)                      // function-calling 工具
            .build();
}
```

**【讲解】**
- 面试的好类比：**AiServices 之于 LLM，相当于 Feign 之于 HTTP、MyBatis 之于 JDBC**——声明式接口 + 动态代理，把编排细节（prompt 组装、记忆管理、工具调用循环）藏进框架。
- `@MemoryId` 用网关注入的 userId：对话记忆按用户隔离，A 用户看不到 B 的上下文。这也是 `/ai` 不放白名单、必须 JWT 的原因之一（另一个原因：防匿名刷 DeepSeek 额度）。

**【一分钟回答】** 用 LangChain4j 的 AiServices：声明一个带 @MemoryId/@UserMessage 注解的接口，builder 里装配生成模型（DeepSeek）、RAG 检索器、按 userId 隔离的对话记忆和商品查询工具，框架动态生成实现。类似 Feign 的声明式思想。

---

## 知识点 2：RAG（检索增强生成）

**【面试怎么问】** 什么是 RAG？为什么不直接问大模型？

**【项目代码】** `LangChainConfig.java` 三个基础设施 Bean：

```java
// ② 向量化模型: 本地 Ollama 跑 bge-m3 (文本→向量, 不花钱不出网)
@Bean
public EmbeddingModel embeddingModel(AiProperties p) {
    return OllamaEmbeddingModel.builder()
            .baseUrl(p.getOllama().getBaseUrl())          // localhost:11434
            .modelName(p.getOllama().getEmbeddingModel()).build();
}

// ③ 向量库: Redis Stack (复用已有中间件, 不新增组件)
@Bean
public EmbeddingStore<TextSegment> embeddingStore(AiProperties p) {
    return RedisEmbeddingStore.builder().host(...).port(...).dimension(...).build();
}

// ④ 检索器: maxResults(3)最多召回3个片段; minScore(0.6)低于0.6的丢弃, 防不相关内容干扰
@Bean
public ContentRetriever contentRetriever(...) {
    return EmbeddingStoreContentRetriever.builder()
            .embeddingStore(store).embeddingModel(embeddingModel)
            .maxResults(3).minScore(0.6).build();
}
```

知识导入：`KnowledgeImportService` 把客服知识（退款政策、物流说明等）切段→向量化→存入 Redis。

**【讲解】**
- RAG 解决 LLM 两大缺陷：**不知道私有知识**（我们的退款政策）+ **爱编造（幻觉）**。流程：知识入库时切段向量化；提问时把问题也向量化，按**余弦相似度**检索最相关片段，塞进 prompt 让模型"照着答"。
- 两个调参数值要能解释：maxResults=3（塞太多浪费 token 还稀释注意力）、minScore=0.6（相关度门槛，低分片段宁可不给，防止误导）。
- 技术选型的成本意识：向量化用**本地 Ollama**（调用频繁、不花钱），生成用 **DeepSeek API**（质量要求高）；向量库用 **Redis Stack**（项目已有 Redis，不为了 RAG 引入 Milvus 之类新组件）。

**【一分钟回答】** RAG=检索增强生成：私有知识切段后用 embedding 模型转向量存 Redis；用户提问先向量化检索 top3 相似片段（相似度≥0.6 才要），拼进 prompt 让 DeepSeek 基于事实回答，解决私有知识和幻觉问题。向量化跑本地 Ollama 省成本，生成走 DeepSeek 保质量。

---

## 知识点 3：Function Calling（让 AI 调用你的接口）

**【面试怎么问】** AI 怎么查到实时商品数据的？（知识库是静态的，价格库存是动态的）

**【项目代码】** `mini-mall-ai/.../tool/ProductQueryTool.java`：

```java
@Tool("根据关键词和最高价格查询在售商品。用户想找商品、要推荐、问有没有某类商品时调用。")
public String queryProducts(String keyword, BigDecimal maxPrice) {
    // DeepSeek 从"3000以内的手机"自动抽出 keyword=手机, maxPrice=3000, 决定调这个方法
    Result<ProductPage> result = productFeignClient.search(keyword, maxPrice, 5);
    List<ProductBrief> list = result.getData().getRecords();
    if (list == null || list.isEmpty()) {
        return "没有找到符合条件的在售商品。";   // ⭐ 空结果给人话, 别返回空串让AI瞎编
    }
    return list.stream()
            .map(p -> p.getName() + " ￥" + p.getPrice() + " 库存" + p.getStock() + "件")
            .collect(Collectors.joining("\n"));
}
```

**【讲解】**
- 机制：`@Tool` 的描述文本和方法签名被转成工具 schema 随 prompt 发给模型；模型判断需要时返回"调用请求 + 从自然语言里抽好的参数"；框架反射执行方法，把返回文本再喂回模型生成最终回答。**模型不执行代码，只做决策，执行在我们 JVM 里**。
- `@Tool` 描述是写给模型看的 prompt：要写清"什么时候该调用"，写不好模型就不调或乱调。
- 工程细节：固定取前 5 条（给模型看太多没意义还费 token）；空结果返回明确的自然语言（返回空串模型会开始编造商品）。
- RAG 和 Tool 的分工一句话：**RAG 管静态知识（政策文档），Tool 管动态数据（实时库存价格）**。

**【一分钟回答】** 给方法加 @Tool 注解和"何时调用"的描述，LangChain4j 把它注册给 DeepSeek；模型从用户话里自动抽参数决定调用，框架执行方法（内部 Feign 查商品）把结果回喂给模型组织回答。静态知识走 RAG，实时数据走 Function Calling，两者互补。

---

## 知识点 4：对话记忆与多轮上下文

**【项目代码】**

```java
.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
```

**【讲解】**
- LLM 本身无状态，"记忆"就是把历史消息随每次请求重发。`MessageWindowChatMemory` 滑动窗口只留最近 10 条——多轮体验和 token 成本的折中。
- memoryId = 网关注入的 userId：记忆按用户隔离，这就是为什么 `/ai/chat` 必须走 JWT 而不能匿名。

**【一分钟回答】** LLM 无状态，记忆靠每次把历史随请求带上。用滑动窗口保留每用户最近 10 条，memoryId 绑定网关验出的 userId 实现用户级隔离，同时控制 token 成本。
