package com.minimall.search.service.impl;


import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.json.JsonData;
import com.minimall.common.core.domain.Result;
import com.minimall.search.client.ProductFeignClient;
import com.minimall.search.document.ProductDocument;
import com.minimall.search.dto.ProductSearchRequest;
import com.minimall.search.entity.ProductSource;
import com.minimall.search.repository.ProductDocumentRepository;
import com.minimall.search.service.IProductSearchService;
import com.minimall.search.vo.PageResultVO;
import com.minimall.search.vo.ProductSearchVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
/**
 * 商品搜索服务实现 (search 服务核心业务逻辑)
 * <p>
 * 职责分两类:
 *   - 写 ES (syncAll/syncById/deleteById): 把 product 服务的商品数据灌进 ES 索引
 *   - 读 ES (search): 给前端提供搜索能力
 * <p>
 * 关键依赖:
 *   - repository: Spring Data ES 的 DAO, 屏蔽底层 HTTP 调用
 *   - productFeignClient: 调 product 服务拉全量商品数据
 * <p>
 * 注意:
 *   - syncAll 用 saveAll, ES 的 save 是 upsert (id 已存在会覆盖, 不冲突)
 *   - search 暂未实现, G9.4.4 再填
 */
@Service
@Slf4j

public class ProductSearchServiceImpl implements IProductSearchService {

    @Resource
    private ProductDocumentRepository repository;  // ES 的 DAO (你刚写的)

    @Resource
    private ProductFeignClient productFeignClient; // 调 product 服务 (你刚写的)

    @Resource
    private ElasticsearchOperations elasticsearchOperations; // 复杂搜索 (Repository 不够用时用它)

    @Override
    public int syncAll() {
        Result<List<ProductSource>>result = productFeignClient.listAllForSync();
        if (result.getCode() == null || result.getCode() != 200) {
            log.error("[search-sync] 拉商品失败, message={}", result.getMessage());
            return 0;
        }
        List<ProductSource> sources = result.getData();
        List<ProductDocument> documents = sources.stream()
                .map(ProductDocument::from)
                .toList();

        // 4. 分批灌进 ES (saveAll 是 upsert: 有则覆盖, 无则插入)
        //    ⚠ 为什么要分批: saveAll 会把整个 List 拼成一个 bulk 请求一次发出。
        //    10 万商品 ≈ 55MB, 超过 ES 的写入压力保护阈值(默认=堆内存 10%, 512M 堆 → 53.6MB),
        //    ES 直接回 429 es_rejected_execution_exception 拒收 —— 这是压测灌数时真实炸出来的坑。
        //    每批 5000 条 ≈ 3MB, 远低于阈值, 且批次间给 ES 留出消化时间。
        final int batchSize = 5000;
        for (int from = 0; from < documents.size(); from += batchSize) {
            int to = Math.min(from + batchSize, documents.size());
            repository.saveAll(documents.subList(from, to));   // subList 是视图不复制数据
            log.info("[search-sync] 已写入 {}/{} 条", to, documents.size());
        }

        log.info("[search-sync] 全量同步完成, 共 {} 条", documents.size());
        return documents.size();
    }
    /**
     * SEC-2: 单商品增量同步 (终于实现, 之前一直是空壳)。
     * 语义: "让 ES 里这个商品的状态跟数据库对齐" ——
     *   商品在库且已上架(status=1) → upsert 进索引 (新增/编辑/重新上架 都靠这条)
     *   商品不存在 / 已下架       → 从索引删掉 (下架商品不该被搜到)
     * 调用方: product 服务在 上/下架、编辑、删除 后 Feign 调 POST /search/sync/{id}。
     */
    @Override
    public void syncById(Long productId) {
        // ① Feign 单查商品 (走 product 详情接口, 详情不过滤下架, 所以能拿到 status=0 的商品)
        Result<ProductSource> resp = productFeignClient.getById(productId);

        // ② product 服务不可用(fallback 503) → 跳过本次, 【不能删索引】——
        //    否则 product 一抖动, 商品就从搜索里消失了, 宁可暂时陈旧也不误删
        if (resp == null || resp.getCode() == null) {
            log.warn("[search-sync] syncById 响应异常, 跳过 productId={}", productId);
            return;
        }
        if (resp.getCode() == 503) {
            log.warn("[search-sync] product 服务不可用, 跳过 productId={}", productId);
            return;
        }

        ProductSource src = resp.getData();

        // ③ 商品不存在(404/业务异常) 或 已下架(status!=1) → 从 ES 删掉
        //    deleteById 对"索引里本来就没有"的 id 不报错, 天然幂等
        if (resp.getCode() != 200 || src == null
                || src.getStatus() == null || src.getStatus() != 1) {
            repository.deleteById(productId);
            log.info("[search-sync] 商品不在售, 已从 ES 移除 productId={}", productId);
            return;
        }

        // ④ 在售商品 → 转文档 upsert (save 有则覆盖无则插入, 跟 syncAll 的 saveAll 同语义)
        repository.save(ProductDocument.from(src));
        log.info("[search-sync] 增量同步完成 productId={}", productId);
    }

    @Override
    public void deleteById(Long productId) {  repository.deleteById(productId);
        log.info("已从 ES 删除商品 productId={}", productId);
    }

    /**
     * 搜索商品 — ES 核心方法.
     * <p>
     * 5 步流程:
     *   1. 处理 page/size 默认值 (防 null/0)
     *   2. 构造 BoolQuery (拼 keyword/categoryId/price 三类条件)
     *   3. 包装 NativeQuery (加分页 + 排序)
     *   4. ElasticsearchOperations.search() 真正执行
     *   5. SearchHits → PageResultVO 转换返回
     */
    @Override
    public PageResultVO<ProductSearchVO> search(ProductSearchRequest request) {
        // ─── 1. 参数默认值 ────────────────────────────────────────
        int page = request.getPage() == null || request.getPage() < 1 ? 1 : request.getPage();
        int size = request.getSize() == null || request.getSize() < 1 ? 10 : request.getSize();

        // ─── 2. 构造 BoolQuery ───────────────────────────────────
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        // 2a. keyword: 模糊匹配 name + description + detail (要打分, 用 must)
        if (StringUtils.hasText(request.getKeyword())) {
            Query keywordQuery = MultiMatchQuery.of(m -> m
                    .query(request.getKeyword())
                    .fields("name", "description", "detail")
            )._toQuery();
            boolBuilder.must(keywordQuery);
        }

        // 2b. categoryId: 精确过滤 (不打分, 用 filter, 有缓存更快)
        if (request.getCategoryId() != null) {
            Query catQuery = TermQuery.of(t -> t
                    .field("categoryId")
                    .value(request.getCategoryId())
            )._toQuery();
            boolBuilder.filter(catQuery);
        }

        // 2c. price 区间: gte/lte 都用 filter (精确范围过滤, 不打分)
        if (request.getMinPrice() != null || request.getMaxPrice() != null) {
            Query priceQuery = RangeQuery.of(r -> {
                r.field("price");
                if (request.getMinPrice() != null) {
                    r.gte(JsonData.of(request.getMinPrice()));
                }
                if (request.getMaxPrice() != null) {
                    r.lte(JsonData.of(request.getMaxPrice()));
                }
                return r;
            })._toQuery();
            boolBuilder.filter(priceQuery);
        }

        // ─── 3. 包装成 NativeQuery (含分页 + 排序) ──────────────
        // PageRequest 的 page 从 0 开始, 所以传 page - 1
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(boolBuilder.build()._toQuery())
                .withPageable(PageRequest.of(page - 1, size, parseSort(request.getSort())))
                .build();

        // ─── 4. 执行查询 ────────────────────────────────────────
        SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);

        // ─── 5. SearchHits → PageResultVO<ProductSearchVO> ─────
        List<ProductSearchVO> records = hits.getSearchHits().stream()
                .map(SearchHit::getContent)             // SearchHit → ProductDocument
                .map(ProductSearchVO::from)             // ProductDocument → ProductSearchVO (你要写的)
                .toList();

        long total = hits.getTotalHits();               // 总命中数
        long pages = (total + size - 1) / size;         // 总页数 = 向上取整(total/size)

        log.info("[search] keyword={}, total={}, page={}/{}",
                request.getKeyword(), total, page, pages);

        return new PageResultVO<>(total, pages, page, size, records);
    }

    /**
     * 解析前端传的 sort 字符串 → Spring Sort 对象.
     * <p>
     * 支持 5 种:
     *   price_asc    — 价格升序
     *   price_desc   — 价格降序
     *   sales_desc   — 销量降序
     *   rating_desc  — 评分降序
     *   newest       — 最新上架 (createTime 降序)
     *   (其他/null)  — 默认按 ES 相关度评分排序 (Sort.unsorted)
     */
    private Sort parseSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return Sort.unsorted();  // 默认按 ES 评分排序
        }
        return switch (sort) {
            case "price_asc"   -> Sort.by(Sort.Order.asc("price"));
            case "price_desc"  -> Sort.by(Sort.Order.desc("price"));
            case "sales_desc"  -> Sort.by(Sort.Order.desc("sales"));
            case "rating_desc" -> Sort.by(Sort.Order.desc("avgRating"));
            case "newest"      -> Sort.by(Sort.Order.desc("createTime"));
            default            -> Sort.unsorted();
        };
    }

}
