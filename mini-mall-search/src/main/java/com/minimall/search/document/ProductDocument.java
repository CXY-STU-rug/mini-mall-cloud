package com.minimall.search.document;
import com.minimall.search.entity.ProductSource;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
@Document(indexName = "mini_mall_product")
public class ProductDocument {
    @Id                                        // 标记主键字段 (映射到 ES 的 _id)
    private Long id;

    @Field(type = FieldType.Text)              // 字段级别,声明 ES Field 类型
    private String name;

    @Field(type = FieldType.Long)
    private Long categoryId;

    @Field(type = FieldType.Text)
    private String description ;

    @Field(type = FieldType.Text)
    private String detail;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Integer)
    private Integer stock;

    @Field(type = FieldType.Integer)
    private Integer sales;

    @Field(type = FieldType.Double)
    private BigDecimal avgRating;

    @Field(type = FieldType.Integer)
    private Integer reviewCount;

    @Field(type = FieldType.Keyword)
    private String  coverImage;

    @Field(type = FieldType.Integer)
    private Byte status;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createTime;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime updateTime;

    // ProductDocument.java 里加这个静态方法
    public static ProductDocument from(ProductSource src) {
        ProductDocument doc = new ProductDocument();
        doc.setId(src.getId());
        doc.setName(src.getName());
       doc.setCategoryId(src.getCategoryId());
       doc.setDescription(src.getDescription());
       doc.setDetail(src.getDetail());
       doc.setPrice(src.getPrice());
       doc.setStock(src.getStock());
       doc.setSales(src.getSales());
       doc.setAvgRating(src.getAvgRating());
       doc.setReviewCount(src.getReviewCount());
       doc.setCoverImage(src.getCoverImage());
       doc.setStatus(src.getStatus());
       doc.setCreateTime(src.getCreateTime());
        doc.setUpdateTime(src.getUpdateTime());
        return doc;
    }


}