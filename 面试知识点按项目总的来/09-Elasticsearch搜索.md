# 09 Elasticsearch 商品搜索

## 知识点 1：为什么用 ES（vs MySQL LIKE）

**【面试怎么问】** 搜索为什么不用 MySQL 的 LIKE？

**【项目代码】** 项目里两代搜索并存，正好对比：

```java
// 老版: product 服务 MySQL 模糊查询 (G3阶段)
w.like("name", keyword);           // LIKE '%keyword%' 前置通配无法走索引, 全表扫

// 新版: search 服务 ES multi_match (G9阶段)
Query keywordQuery = MultiMatchQuery.of(m -> m
        .query(request.getKeyword())
        .fields("name", "description", "detail")   // 三字段同时搜, 自带相关度打分
)._toQuery();
```

**【讲解】**
- `LIKE '%x%'` 前置通配符让 B+ 树索引失效，数据量一上来全表扫描；且只能"包含匹配"，没有分词（搜"苹果手机"匹配不到"苹果 iPhone 手机"）、没有相关度排序。
- ES 倒排索引：文档先分词，建"词 → 文档列表"的映射，查询也分词后取交并集，天然支持多字段、打分排序、高亮。
- 架构上单独拆了 search 服务：搜索的资源消耗模式（CPU/内存密集）和业务服务不同，独立伸缩。

**【一分钟回答】** LIKE 前置通配不走索引且无分词无打分；ES 用倒排索引，分词后按词精确定位文档，支持多字段检索和相关度排序。我们把搜索拆成独立服务，商品数据通过 MQ 异步同步进 ES。

---

## 知识点 2：BoolQuery 的 must vs filter（打分与缓存）

**【面试怎么问】** ES 查询里 must 和 filter 有什么区别？

**【项目代码】** `ProductSearchServiceImpl.search()`：

```java
BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

// keyword: 要参与相关度打分 → must
boolBuilder.must(multiMatchQuery);

// categoryId 精确过滤: 不需要打分 → filter (结果可被ES缓存, 更快)
boolBuilder.filter(TermQuery.of(t -> t.field("categoryId").value(...)));

// price 区间: 同样 filter
boolBuilder.filter(RangeQuery.of(r -> r.field("price").gte(...).lte(...)));

NativeQuery nativeQuery = NativeQuery.builder()
        .withQuery(boolBuilder.build()._toQuery())
        .withPageable(PageRequest.of(page - 1, size, parseSort(request.getSort())))  // 页码从0起
        .build();
SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);
```

**【讲解】**
- 划分标准一句话：**影响"排序先后"的放 must（算分），只决定"进不进结果"的放 filter（不算分）**。filter 不算分所以结果可以被缓存复用，性能更好。
- 排序设计：默认按 ES 相关度分数；用户选了 price_asc/sales_desc 等则显式 Sort 覆盖（`parseSort` 的 switch）。
- 分页转换的小坑：前端 page 从 1 开始，ES/Spring Data 从 0 开始，`PageRequest.of(page - 1, ...)`。

**【一分钟回答】** keyword 走 must 参与打分决定排序；分类、价格区间这类"是否命中"的精确条件走 filter——不算分、可缓存、更快。组合成 BoolQuery 后套 NativeQuery 加分页排序执行。

---

## 知识点 3：MySQL → ES 的数据同步（MQ 事件驱动）

**【面试怎么问】** ES 里的数据怎么和数据库保持一致？

**【项目代码】** 见 [05-RabbitMQ消息队列.md](05-RabbitMQ消息队列.md) 知识点 4。核心回顾：

```java
// product 改库后发事件(只带 productId) → search 消费时回查最新数据
public void syncById(Long productId) {
    Result<ProductSource> resp = productFeignClient.getById(productId);
    if (resp.getCode() == 503) return;              // ⭐ product挂了→跳过, 绝不删索引
    if (src == null || src.getStatus() != 1) {
        repository.deleteById(productId);            // 不存在/已下架 → 移出索引(幂等)
        return;
    }
    repository.save(ProductDocument.from(src));      // 在售 → upsert
}
```

**【讲解】**
- 同步语义定义得很干净：**"让 ES 里这个商品的状态跟数据库对齐"**——新增/编辑/上架都是 upsert，下架/删除都是移除，一个方法覆盖所有事件。
- 全量兜底 `syncAll()`：Feign 拉全部在售商品 `saveAll` 灌入（save 是 upsert 语义不怕重复），用于初始化和数据修复。
- 时序细节：product 更新时**先删 Redis 缓存再发 MQ**，保证 search 回查 `/product/{id}` 拿到的是新数据。
- 其它方案对比着说：双写（强耦合、失败难处理）、canal 订阅 binlog（对业务零侵入，更工程化，但多一套组件）——本项目选 MQ 事件是折中。

**【一分钟回答】** 事件驱动最终一致：商品变更后发只含 id 的 MQ 消息，search 服务回查最新状态决定 upsert 或删除，幂等且不怕乱序；源服务抖动时跳过不误删。另有全量同步接口兜底。强一致不必要——搜索晚几百毫秒完全可接受。

---

## 知识点 4：文档映射（@Document / ProductDocument）

**【面试怎么问】** ES 的索引结构怎么定义？text 和 keyword 类型什么区别？

**【项目代码】** `mini-mall-search/.../document/ProductDocument.java`（结构要点）：

```java
@Document(indexName = "product")           // 对应 ES 索引名
public class ProductDocument {
    @Id
    private Long id;                        // 文档 _id = 商品id, save 天然 upsert
    @Field(type = FieldType.Text, analyzer = "ik_max_word")   // 分词字段: 参与全文检索
    private String name;
    @Field(type = FieldType.Keyword)        // 不分词: 精确匹配/聚合/排序用
    private Long categoryId;
    ...
    public static ProductDocument from(ProductSource src) { ... }   // MySQL实体→ES文档转换
}
```

**【讲解】**
- **text**：写入时分词，用于全文检索（name/description/detail）；**keyword**：整体一个词条，用于 term 精确过滤、排序、聚合（categoryId/status）。搜索字段用 text，过滤字段用 keyword，和上面 must/filter 的分工一一对应。
- 中文必须配 ik 分词器（ik_max_word 细粒度切词），默认 standard 分词器会把中文切成单字。
- 文档 `_id` 用商品 id：`repository.save` 有则覆盖无则插入（upsert），同步逻辑因此天然幂等。

**【一分钟回答】** @Document 映射索引，text 类型+ik 分词用于全文检索字段，keyword 类型用于精确过滤排序字段。文档 _id 复用商品主键，save 即 upsert，让同步操作幂等。
