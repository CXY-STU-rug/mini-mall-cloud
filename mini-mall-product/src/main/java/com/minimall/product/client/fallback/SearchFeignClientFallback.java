package com.minimall.product.client.fallback;

import com.minimall.common.core.domain.Result;
import com.minimall.product.client.SearchFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SearchFeignClient 降级 (SEC-2)。
 * search 服务挂了 → 只记 warn 日志, 返回 error, 不抛异常
 * —— 商品上下架/删除的主流程绝不能因为"搜索同步失败"而失败。
 * 后果只是 ES 索引暂时陈旧, 可用 POST /search/sync 全量同步补齐。
 */
@Component
@Slf4j
public class SearchFeignClientFallback implements SearchFeignClient {

    @Override
    public Result<Void> syncById(Long productId) {
        log.warn("[fallback] search 服务不可用, 增量同步跳过 productId={}", productId);
        return Result.error(503, "search 服务不可用");
    }

    @Override
    public Result<Void> deleteById(Long productId) {
        log.warn("[fallback] search 服务不可用, 索引删除跳过 productId={}", productId);
        return Result.error(503, "search 服务不可用");
    }
}
