package com.minimall.ai.client.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品简要信息 —— product 服务 Product 实体的【副本】。
 * 微服务不共享 entity jar, 所以建副本, 只留 agent 要用的字段。
 */
@Data
public class ProductBrief {
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer stock;
    private String description;
}
