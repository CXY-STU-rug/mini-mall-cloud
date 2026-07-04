package com.minimall.ai.client;

import com.minimall.ai.client.dto.ProductPage;
import com.minimall.ai.client.fallback.ProductFeignClientFallback;
import com.minimall.common.core.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * 调 product 服务查商品。
 * 靠 Nacos 服务名(mini-mall-product)直连, 不过网关。
 * fallback: product 挂了时的降级 (需 application.yml 开 feign.sentinel.enabled)。
 */
@FeignClient(name = "mini-mall-product", fallback = ProductFeignClientFallback.class)
public interface ProductFeignClient {

    /** 对应 product 的 GET /product?keyword=&maxPrice=&size= */
    @GetMapping("/product")
    Result<ProductPage> search(@RequestParam("keyword") String keyword,
                               @RequestParam("maxPrice") BigDecimal maxPrice,
                               @RequestParam(value = "size", defaultValue = "5") Integer size);
}
