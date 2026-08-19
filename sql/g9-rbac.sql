-- ============================================================
-- G9 —— 动态 RBAC 权限模型（角色-权限，可后台动态配置）
-- 数据库：mini_mall（与 schema.sql 同库，归 user 服务管理）
-- 使用方法：USE mini_mall; SOURCE .../g9-rbac.sql;
--
-- 设计目标：把原来写死在网关 needAdmin() 里的 "role==1 才是管理员"，
--   升级为 "用户→角色→权限(URL)" 三层可配置模型，权限关系加载进 Redis，
--   网关读 Redis 动态判权，后台改权限即时生效、无需重启、无需改代码。
--
-- 与现有 user 表的关系：
--   - user 表结构【不改】，其 role 字段（0普通/1管理员）保留做兜底与数据迁移依据
--   - 新增 4 张 sys_* 表承载 RBAC，user.id 作为 sys_user_role 的逻辑外键
-- ============================================================

-- ------------------------------------------------------------
-- 1. sys_role —— 角色表
--   role_code 是给程序用的稳定标识（如 ROLE_ADMIN），role_name 是给人看的中文名
--   约定 role_code 以 ROLE_ 开头，与 Spring Security 习惯一致，便于日后接入
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
  `role_code`   VARCHAR(50)  NOT NULL                        COMMENT '角色码（程序用，唯一，如 ROLE_ADMIN）',
  `role_name`   VARCHAR(50)  NOT NULL                        COMMENT '角色名（展示用，如 超级管理员）',
  `description` VARCHAR(200) DEFAULT NULL                    COMMENT '角色说明',
  `status`      TINYINT      NOT NULL DEFAULT 1              COMMENT '状态：0=禁用 1=启用',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0              COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)                    -- 角色码唯一，程序按它认角色
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- ------------------------------------------------------------
-- 2. sys_permission —— 权限表
--   一条权限 = 一个可被授权的资源。本项目按"接口(URL+method)"粒度授权，
--   因为网关就是按 URL 判权，存 url_pattern+method 可直接匹配。
--   perm_type 预留菜单/按钮维度（前端控制显隐用），V1 主要用 type=3 接口权限。
--   url_pattern 支持 Ant 风格通配（/admin/product/**），method='*' 表示不限方法。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
  `perm_code`   VARCHAR(80)  NOT NULL                        COMMENT '权限码（唯一，如 product:manage）',
  `perm_name`   VARCHAR(80)  NOT NULL                        COMMENT '权限名（展示用，如 商品管理）',
  `perm_type`   TINYINT      NOT NULL DEFAULT 3              COMMENT '类型：1=菜单 2=按钮 3=接口',
  `url_pattern` VARCHAR(200) DEFAULT NULL                    COMMENT 'URL 模式（Ant 风格，如 /admin/product/**）',
  `method`      VARCHAR(10)  NOT NULL DEFAULT '*'            COMMENT 'HTTP 方法：GET/POST/PUT/DELETE/*(不限)',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0              COMMENT '父权限ID（做权限树，0=顶级）',
  `sort`        INT          NOT NULL DEFAULT 0              COMMENT '排序值',
  `status`      TINYINT      NOT NULL DEFAULT 1              COMMENT '状态：0=禁用 1=启用',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0              COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`)                    -- 权限码唯一
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- ------------------------------------------------------------
-- 3. sys_role_permission —— 角色↔权限关系表（多对多）
--   一行 = 给某角色授某权限。后台"勾选权限"就是增删这张表的行。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
  `id`            BIGINT   NOT NULL AUTO_INCREMENT           COMMENT '主键ID',
  `role_id`       BIGINT   NOT NULL                           COMMENT '角色ID（指向 sys_role.id）',
  `permission_id` BIGINT   NOT NULL                           COMMENT '权限ID（指向 sys_permission.id）',
  `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`),    -- 同一角色同一权限只授一次（幂等）
  KEY `idx_role_id` (`role_id`)                               -- 按角色查它的所有权限
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关系表';

-- ------------------------------------------------------------
-- 4. sys_user_role —— 用户↔角色关系表（多对多）
--   一行 = 给某用户挂某角色。后台"给用户配角色"就是增删这张表的行。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT             COMMENT '主键ID',
  `user_id`     BIGINT   NOT NULL                             COMMENT '用户ID（指向 user.id）',
  `role_id`     BIGINT   NOT NULL                             COMMENT '角色ID（指向 sys_role.id）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),          -- 同一用户同一角色只挂一次（幂等）
  KEY `idx_user_id` (`user_id`)                               -- 按用户查它的所有角色（网关灌 Redis 用）
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关系表';


-- ============================================================
-- 初始化数据（内置角色 + 权限 + 关系 + 迁移现有管理员）
--   幂等：用 INSERT ... 前先按唯一键判断，重复执行不会报错/不会重复插
-- ============================================================

-- ---- 4.1 内置 3 个角色 ----
--   ROLE_ADMIN    超级管理员：拥有一切权限（含退款审批这种敏感操作）
--   ROLE_OPERATOR 运营：能管商品/分类/优惠券，但不能碰退款审批、用户管理
--   ROLE_SERVICE  客服：能审批退款、看用户，但不能改商品
INSERT INTO `sys_role` (`role_code`, `role_name`, `description`) VALUES
  ('ROLE_ADMIN',    '超级管理员', '拥有全部后台权限'),
  ('ROLE_OPERATOR', '运营',       '商品/分类/优惠券管理'),
  ('ROLE_SERVICE',  '客服',       '退款审批、用户查看');

-- ---- 4.2 内置权限（按后台接口划分，url_pattern 对应网关拦截的写接口）----
--   这里覆盖当前项目所有 /admin/** 与管理写操作。日后加接口，后台加一行权限即可。
INSERT INTO `sys_permission` (`perm_code`, `perm_name`, `perm_type`, `url_pattern`, `method`, `sort`) VALUES
  ('product:manage',   '商品管理',   3, '/admin/product/**',  '*', 10),
  ('category:manage',  '分类管理',   3, '/admin/category/**', '*', 20),
  ('coupon:manage',    '优惠券管理', 3, '/admin/coupon/**',   '*', 30),
  ('seckill:manage',   '秒杀活动管理',3, '/admin/seckill/**',  '*', 40),
  ('order:manage',     '订单管理',   3, '/admin/order/**',    '*', 50),
  ('refund:approve',   '退款审批',   3, '/admin/refund/**',   '*', 60),
  ('user:manage',      '用户管理',   3, '/admin/user/**',     '*', 70),
  ('rbac:manage',      '角色权限管理',3, '/admin/role/**',     '*', 80),
  ('rbac:manage2',     '权限查看',   3, '/admin/permission/**','*', 81);

-- ---- 4.3 角色↔权限：给每个角色分配权限 ----
--   ROLE_ADMIN = 全部权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.role_code = 'ROLE_ADMIN';

--   ROLE_OPERATOR = 商品 + 分类 + 优惠券 + 秒杀
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `sys_role` r
JOIN `sys_permission` p ON p.perm_code IN ('product:manage','category:manage','coupon:manage','seckill:manage')
WHERE r.role_code = 'ROLE_OPERATOR';

--   ROLE_SERVICE = 退款审批 + 用户查看 + 订单管理
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `sys_role` r
JOIN `sys_permission` p ON p.perm_code IN ('refund:approve','user:manage','order:manage')
WHERE r.role_code = 'ROLE_SERVICE';

-- ---- 4.4 迁移现有管理员：user 表里 role=1 的用户，统一挂 ROLE_ADMIN ----
--   这样上线 RBAC 后，原来的管理员权限无缝延续，不会一夜之间谁都进不去后台。
INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.id, r.id
FROM `user` u
JOIN `sys_role` r ON r.role_code = 'ROLE_ADMIN'
WHERE u.role = 1 AND u.is_deleted = 0;

-- ============================================================
-- 📐 关系速查
-- ============================================================
--   user ──(sys_user_role)── sys_role ──(sys_role_permission)── sys_permission
--   网关判权时只需两个 Redis 集合求交集：
--     用户拥有的角色  ∩  接口要求的角色  ≠ ∅  → 放行
-- ============================================================
