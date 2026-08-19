package com.minimall.product.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.minimall.common.core.domain.Result;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.product.entity.Product;
import com.minimall.product.service.IProductService;
import io.swagger.v3.oas.annotations.Operation;   // 描述"单个接口"的用途
import io.swagger.v3.oas.annotations.tags.Tag;    // 给"一组接口(整个Controller)"起名归类
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品 Controller (G3.9 补齐: 单体 6 端点 + flaky 演示)
 *
 * 端点清单:
 *   GET    /product?page=&size=&categoryId=&keyword=&minPrice=&maxPrice=  分页搜索
 *   GET    /product/{id}                  详情(带 Redis 缓存)
 *   POST   /product                       新建
 *   PUT    /product/{id}                  改 + 删缓存
 *   DELETE /product/{id}                  删 + 删缓存
 *   GET    /product/hot-search            热搜 Top 10
 *   GET    /product/flaky                 Sentinel 熔断演示(F2 保留)
 */
@Tag(name = "商品", description = "商品浏览：分页搜索、详情、热搜（C 端公开读；写操作走 /admin/product）")
@RestController
@RequestMapping("/product")
public class ProductController {

    @Autowired
    private IProductService productService;

    /** ① 分页搜索 + 多条件筛选 */
    @Operation(summary = "商品分页搜索", description = "按分类/关键词/价格区间分页查询上架商品，公开接口")
    @GetMapping
    public Result<IPage<Product>> list(
            @RequestParam(defaultValue = "1")  Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice
    ) {
        return Result.success(
                productService.searchProducts(page, size, categoryId, keyword, minPrice, maxPrice)
        );
    }

    /** ② 详情(走 Redis 缓存) */
    @Operation(summary = "商品详情", description = "按 id 查商品详情（带 Redis 缓存），公开接口")
    @GetMapping("/{id}")
    public Result<Product> detail(@PathVariable("id") Long id) {
        Product product = productService.getProductDetail(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        return Result.success(product);
    }

    /** ③ 新建(白嫖 IService.save) */
    @PostMapping
    public Result<Product> create(@RequestBody Product product) {
        productService.save(product);
        return Result.success(product);
    }

    /** ④ 修改 + 自动失效缓存 */
    @PutMapping("/{id}")
    public Result<Product> update(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        productService.updateProduct(product);
        return Result.success(product);
    }

    /** ⑤ 删除(逻辑删) + 失效缓存 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return Result.success();
    }

    /** ⑥ 热搜 Top 10 */
    @GetMapping("/hot-search")
    public Result<List<Map<String, Object>>> hotSearch() {
        return Result.success(productService.getHotSearch(10));
    }

    /** ⑦ G3.10 扣库存 (内部端点, 给 order 服务 Feign 调) */
    @PutMapping("/{id}/stock/deduct")
    public Result<Integer> deductStock(@PathVariable Long id, @RequestParam Integer qty) {
        int rows = productService.deductStock(id, qty);
        if (rows == 0) {
            throw new BusinessException(400, "库存不足");
        }
        return Result.success(rows);
    }

    /** ⑧ G3.10 回库存 (内部端点) */
    @PutMapping("/{id}/stock/restore")
    public Result<Integer> restoreStock(@PathVariable Long id, @RequestParam Integer qty) {
        return Result.success(productService.restoreStock(id, qty));
    }

    /** ⑨ G7.7 重算评分 (内部端点, 给 review 服务 Feign 调) */
    @PutMapping("/{id}/internal/refresh-rating")
    public Result<Void> refreshRating(@PathVariable Long id) {
        productService.refreshRating(id);
        return Result.success(null);
    }

    // ════════════════════════════════════════════════════════════════
    // F2.6 Sentinel 熔断演示(保留)
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/flaky")
    @SentinelResource(value = "flakyResource", blockHandler = "flakyBlock")
    public Result<String> flaky() {
        if (ThreadLocalRandom.current().nextInt(100) < 30) {
            throw new BusinessException("flaky 接口随机失败 (模拟下游不稳定)");
        }
        return Result.success("flaky ok");
    }

    public Result<String> flakyBlock(BlockException ex) {
        return Result.error(503, "下游服务繁忙, 暂时降级 (触发规则: "
                + ex.getClass().getSimpleName() + ")");
    }
    @GetMapping("/internal/all")
    public Result<List<Product>> listAllForSync() {
        return Result.success(productService.listAllForSync());
    }

}
