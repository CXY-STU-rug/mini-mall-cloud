package com.minimall.user.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * "接口 → 能访问它的角色" 的平铺投影（G9 RBAC）
 * <p>
 * listUrlRoles() 查出来是多行：同一个 url 被 N 个角色授权就有 N 行。
 * Service 层再按 (method + urlPattern) 聚合成 rbac:url_roles 的一条：
 *   field = "GET:/admin/order/**"  value = "ROLE_ADMIN,ROLE_SERVICE"
 */
@Getter
@Setter
public class UrlRoleDTO {

    /** URL 模式，如 /admin/product/** */
    private String urlPattern;

    /** HTTP 方法：GET/POST/PUT/DELETE/*(不限) */
    private String method;

    /** 角色码，如 ROLE_ADMIN */
    private String roleCode;
}
