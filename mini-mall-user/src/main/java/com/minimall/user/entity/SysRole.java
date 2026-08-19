package com.minimall.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色实体（G9 RBAC）—— 对应表 sys_role
 * 一行 = 一个角色，如 ROLE_ADMIN / ROLE_OPERATOR / ROLE_SERVICE
 */
@Getter
@Setter
@TableName("sys_role")                       // 绑定数据库表名
public class SysRole implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)  // 主键自增，跟 DB 的 AUTO_INCREMENT 对应
    private Long id;

    /** 角色码：程序用的稳定标识，唯一，如 ROLE_ADMIN */
    private String roleCode;                  // DB 是 role_code，驼峰自动映射

    /** 角色名：给人看的中文名，如 超级管理员 */
    private String roleName;

    /** 角色说明 */
    private String description;

    /** 状态：0=禁用 1=启用 */
    private Byte status;

    private LocalDateTime createTime;         // DB 默认 CURRENT_TIMESTAMP，插入不用手动填
    private LocalDateTime updateTime;         // DB ON UPDATE 自动刷新

    /** 逻辑删除：0未删 1已删 */
    @TableLogic                               // 查询自动加 AND is_deleted=0，删除变 UPDATE
    private Byte isDeleted;
}
