-- 秒杀订单表修复脚本
--
-- 背景：
--   Java 实体 SeckillOrder 映射的是 seckill_order；
--   历史 schema.sql 中曾误写成 seckill_activity_id，导致按 schema 初始化后秒杀订单无法落库。
--
-- 使用：
--   先执行 sql/schema.sql，再执行本文件。

DROP TABLE IF EXISTS `seckill_order`;

CREATE TABLE IF NOT EXISTS `seckill_order` (
  `id`                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no`             VARCHAR(32)    NOT NULL                COMMENT '秒杀订单号',
  `user_id`              BIGINT         NOT NULL                COMMENT '用户ID',
  `seckill_activity_id`  BIGINT         NOT NULL                COMMENT '秒杀活动ID',
  `product_id`           BIGINT         NOT NULL                COMMENT '商品ID',
  `status`               TINYINT        NOT NULL DEFAULT 0      COMMENT '状态：0待支付 1已支付 2已发货 3已完成 4已取消',
  `seckill_price`        DECIMAL(10,2)  NOT NULL                COMMENT '秒杀成交价快照',
  `pay_time`             DATETIME       DEFAULT NULL            COMMENT '支付时间',
  `create_time`          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`           INT            NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_user_activity` (`user_id`, `seckill_activity_id`),
  KEY `idx_activity_id` (`seckill_activity_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀订单表';
