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
 * 权限实体（G9 RBAC）—— 对应表 sys_permission
 * 一行 = 一个可授权的资源（本项目按"接口 URL+method"粒度）
 */
@Getter
@Setter
@TableName("sys_permission")
public class SysPermission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 权限码：唯一，如 product:manage */
    private String permCode;

    /** 权限名：展示用，如 商品管理 */
    private String permName;

    /** 类型：1=菜单 2=按钮 3=接口 */
    private Byte permType;

    /** URL 模式：Ant 风格，如 /admin/product/** （网关按它匹配） */
    private String urlPattern;

    /** HTTP 方法：GET/POST/PUT/DELETE/*(不限) */
    private String method;

    /** 父权限ID：做权限树用，0=顶级 */
    private Long parentId;

    /** 排序值 */
    private Integer sort;

    /** 状态：0=禁用 1=启用 */
    private Byte status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Byte isDeleted;
}
