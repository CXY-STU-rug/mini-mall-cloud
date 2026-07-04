package com.minimall.ai.tool;

import com.minimall.ai.client.ProductFeignClient;
import com.minimall.ai.client.dto.ProductBrief;
import com.minimall.ai.client.dto.ProductPage;
import com.minimall.common.core.domain.Result;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品查询工具 (function-calling)。
 * @Tool 描述写清「什么时候该调用」, DeepSeek 会自动从用户的自然语言里抽出 keyword / maxPrice,
 * 自己决定要不要调这个方法, 再把返回的文本读给用户。
 */
@Slf4j
@Component
public class ProductQueryTool {

    private final ProductFeignClient productFeignClient;

    // 构造器注入 Feign 客户端
    public ProductQueryTool(ProductFeignClient productFeignClient) {
        this.productFeignClient = productFeignClient;
    }

    @Tool("根据关键词和最高价格查询在售商品。用户想找商品、要推荐、问有没有某类商品时调用。")
    public String queryProducts(String keyword, BigDecimal maxPrice) {
        log.info("AI 触发商品查询: keyword={}, maxPrice={}", keyword, maxPrice);

        // 1. 调 product 服务, 固定取前 5 条 (给大模型看, 太多没意义)
        Result<ProductPage> result = productFeignClient.search(keyword, maxPrice, 5);

        // 2. 从分页结果里取出真正的商品列表 (records 字段)
        List<ProductBrief> list = result.getData().getRecords();

        // 3. 空结果保护: 没查到就直接回一句人话, 别返回空串让 AI 瞎编
        if (list == null || list.isEmpty()) {
            return "没有找到符合条件的在售商品。";
        }

        // 4. 把每件商品 map 成一行展示文本, 再用换行拼成一整段返回给大模型
        return list.stream()
                .map(p -> p.getName() + " ￥" + p.getPrice() + " 库存" + p.getStock() + "件")
                .collect(Collectors.joining("\n"));
    }
}
