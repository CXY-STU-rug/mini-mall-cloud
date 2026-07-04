package com.minimall.product.client;

import com.minimall.common.core.domain.Result;
import com.minimall.product.client.fallback.SearchFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Search 服务 Feign 客户端 (product 侧, SEC-2 新增)
 * <p>
 * 用途: 商品 上/下架、编辑、删除 后, 通知 search 服务让 ES 索引跟数据库对齐。
 *   syncById   → search 会回查最新商品: 在售就 upsert, 下架/不存在就从索引删
 *   deleteById → 直接从索引删 (商品逻辑删时用)
 * <p>
 * ⭐ 循环调用说明 (跟 ReviewFeignClient 同一情况):
 *   search 服务 Feign 调 product (拉商品数据), product 又 Feign 调 search (通知同步)
 *   —— 是【调用图】循环不是【依赖图】循环: 不共享 jar、启动无先后要求、时序不交叉。
 * <p>
 * ⭐ 设计原则: 搜索同步是"锦上添花", 失败绝不能拖垮商品管理主流程,
 *   所以调用方全部 try-catch + fallback 静默, 最坏情况靠 POST /search/sync 全量兜底。
 */
@FeignClient(
        name = "mini-mall-search",
        fallback = SearchFeignClientFallback.class
)
public interface SearchFeignClient {

    /** 增量同步: search 端回查商品最新状态, 自行决定 upsert 还是删除 */
    @PostMapping("/search/sync/{productId}")
    Result<Void> syncById(@PathVariable("productId") Long productId);

    /** 从索引删除 (商品被删时) */
    @DeleteMapping("/search/{productId}")
    Result<Void> deleteById(@PathVariable("productId") Long productId);
}
