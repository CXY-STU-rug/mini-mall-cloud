package com.minimall.search.client;

import com.minimall.common.core.domain.Result;
import com.minimall.search.entity.ProductSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ProductFeignClientFallback implements ProductFeignClient {
    /**
     * 全量拉商品的降级方法.
     * product 服务不通时, 这里返 Result.error, 上层 service 看到 success=false 就跳过同步.
     */
    @Override
    public Result<List<ProductSource>> listAllForSync() {
        log.warn("服务不可用");   // 用 warn 不用 error: 降级是"预期内的失败", 不是 bug, 没必要触发告警
        return Result.error(404,"服务不可用");
    }

    /** 单查商品的降级: product 不通时返 error, syncById 看到 code!=200 会跳过本次同步(不误删索引)。 */
    @Override
    public Result<ProductSource> getById(Long id) {
        log.warn("[fallback] product 服务不可用, getById 降级 id={}", id);
        return Result.error(503, "product 服务不可用");
    }
}