package com.minimall.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色-权限关系（G9 RBAC）—— 对应表 sys_role_permission
 * 一行 = 给某角色授某权限。纯关系表：没有逻辑删除，取消授权就是物理删这一行。
 */
@Getter
@Setter
@TableName("sys_role_permission")
public class SysRolePermission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 角色ID → sys_role.id */
    private Long roleId;

    /** 权限ID → sys_permission.id */
    private Long permissionId;

    private LocalDateTime createTime;
    // 注意：没有 updateTime、没有 isDeleted —— 关系只有"存在/删除"两态
}
