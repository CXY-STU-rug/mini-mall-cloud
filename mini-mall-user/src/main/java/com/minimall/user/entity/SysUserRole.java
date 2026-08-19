package com.minimall.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户-角色关系（G9 RBAC）—— 对应表 sys_user_role
 * 一行 = 给某用户挂某角色。user_id 是【逻辑外键】指向 user.id，不建物理外键。
 */
@Getter
@Setter
@TableName("sys_user_role")
public class SysUserRole implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID → user.id（逻辑外键） */
    private Long userId;

    /** 角色ID → sys_role.id */
    private Long roleId;

    private LocalDateTime createTime;
}
