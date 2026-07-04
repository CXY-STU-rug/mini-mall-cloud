-- ═══════════════════════════════════════════════════════════
-- mini-mall-payment 支付服务建表脚本 (独立库)
-- 执行: mysql -uroot -p123456 < payment_schema.sql
-- ═══════════════════════════════════════════════════════════
CREATE DATABASE IF NOT EXISTS mini_mall_payment
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE mini_mall_payment;

-- 表1: 支付单 (一次支付尝试一行)
CREATE TABLE IF NOT EXISTS payment (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  payment_no   VARCHAR(64)  NOT NULL COMMENT '我方支付单号(传给支付宝当 out_trade_no)',
  order_id     BIGINT       NOT NULL COMMENT '关联订单id',
  order_no     VARCHAR(64)  NOT NULL COMMENT '关联订单号',
  user_id      BIGINT       NOT NULL COMMENT '冗余,便于查我的支付',
  amount       DECIMAL(10,2) NOT NULL COMMENT '应付金额(下单时从order冻结)',
  channel      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=支付宝(预留微信=2)',
  trade_no     VARCHAR(64)  DEFAULT NULL COMMENT '支付宝交易号(回调时存,退款要用)',
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已关闭 3退款中 4已退款',
  notify_time  DATETIME     DEFAULT NULL COMMENT '回调到账时间',
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payment_no (payment_no),
  KEY idx_order_id (order_id),
  KEY idx_user_id (user_id),
  KEY idx_trade_no (trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单';

-- 表2: 退款单 (一次退款一行,支持部分退)
CREATE TABLE IF NOT EXISTS refund (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  refund_no       VARCHAR(64)  NOT NULL COMMENT '我方退款单号',
  payment_id      BIGINT       NOT NULL COMMENT '关联支付单id',
  payment_no      VARCHAR(64)  NOT NULL COMMENT '关联支付单号',
  order_id        BIGINT       NOT NULL,
  order_no        VARCHAR(64)  NOT NULL,
  user_id         BIGINT       NOT NULL,
  amount          DECIMAL(10,2) NOT NULL COMMENT '退款金额',
  reason          VARCHAR(255) DEFAULT NULL COMMENT '退款原因',
  status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0申请中 1处理中 2成功 3失败',
  refund_trade_no VARCHAR(64)  DEFAULT NULL COMMENT '支付宝退款流水号',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_refund_no (refund_no),
  KEY idx_payment_id (payment_id),
  KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单';

-- 表3: 回调流水/幂等黑匣子 (所有回调无论成败都落一条)
CREATE TABLE IF NOT EXISTS payment_notify_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  notify_id     VARCHAR(128) NOT NULL COMMENT '支付宝通知ID,幂等唯一键',
  notify_type   TINYINT      NOT NULL DEFAULT 1 COMMENT '1支付回调 2退款回调',
  out_trade_no  VARCHAR(64)  DEFAULT NULL COMMENT '我方单号',
  trade_no      VARCHAR(64)  DEFAULT NULL COMMENT '支付宝交易号',
  trade_status  VARCHAR(32)  DEFAULT NULL COMMENT 'TRADE_SUCCESS 等',
  raw_body      TEXT         COMMENT '回调原始报文(复盘/对账用)',
  verify_result TINYINT      NOT NULL DEFAULT 0 COMMENT '验签 1成功 0失败',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notify_id (notify_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回调流水+幂等';
