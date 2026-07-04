package com.minimall.ai.client.fallback;

import com.minimall.ai.client.ProductFeignClient;
import com.minimall.ai.client.dto.ProductPage;
import com.minimall.common.core.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;

/**
 * product 服务不可用时的降级: 返回空商品列表, 别让整个 AI 对话崩掉。
 */
@Slf4j
@Component
public class ProductFeignClientFallback implements ProductFeignClient {

    @Override
    public Result<ProductPage> search(String keyword, BigDecimal maxPrice, Integer size) {
        log.warn("product 服务不可用, 商品查询降级返回空. keyword={}", keyword);
        ProductPage empty = new ProductPage();
        empty.setRecords(Collections.emptyList());
        return Result.success(empty);
    }
}
