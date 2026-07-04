package com.minimall.ai.client.dto;

import lombok.Data;

import java.util.List;

/**
 * 接 product 分页接口的返回。
 * product 的 IPage 序列化成 JSON 是 {records:[...], total, size, current...},
 * 这里只声明 records —— Jackson 反序列化时自动忽略其他没声明的字段。
 */
@Data
public class ProductPage {
    private List<ProductBrief> records;
}
