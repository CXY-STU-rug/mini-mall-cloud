package com.minimall.search.client;
import com.minimall.common.core.domain.Result;

import com.minimall.search.entity.ProductSource;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "mini-mall-product", fallback = ProductFeignClientFallback.class)
public interface ProductFeignClient {
    @GetMapping("/product/internal/all")
   Result<List<ProductSource>> listAllForSync();//跨服务查全部产品的接口方法，直接业务调用

    /**
     * SEC-2: 按 id 查单个商品 (syncById 增量同步用)。
     * 调 product 的 GET /product/{id} (详情, 带 Redis 缓存);
     * 返回体是 product 的 Product 实体, Jackson 按字段名反序列化进 ProductSource 副本
     * —— 跟 listAllForSync 同一个"不共享 jar, 各持副本"套路。
     * 商品不存在时 product 端抛 BusinessException → code!=200, 调用方按"查不到"处理。
     */
    @GetMapping("/product/{id}")
    Result<ProductSource> getById(@PathVariable("id") Long id);
}
