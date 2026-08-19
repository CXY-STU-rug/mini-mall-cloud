package com.minimall.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimall.user.dto.UrlRoleDTO;
import com.minimall.user.entity.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-权限关系 Mapper（G9 RBAC）
 * <p>
 * 继承 BaseMapper：给角色配权限 = insert/delete 这张关系表。
 * 额外 1 个自定义查询：查"每个接口 → 能访问它的角色码"，供网关灌 rbac:url_roles。
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    /**
     * 查全站"接口权限 → 角色码"平铺映射（三表 JOIN）。
     * <p>
     * 一个 url 被多个角色授权就有多行，Service 层再按 (method+url) 聚合。
     * 只取 perm_type=3（接口权限）、启用、且配了 url 的。
     */
    @Select("""
            SELECT p.url_pattern AS urlPattern, p.method AS method, r.role_code AS roleCode
            FROM sys_role_permission rp
            JOIN sys_permission p ON rp.permission_id = p.id
            JOIN sys_role       r ON rp.role_id       = r.id
            WHERE p.perm_type = 3
              AND p.status = 1 AND r.status = 1
              AND p.url_pattern IS NOT NULL
            """)
    List<UrlRoleDTO> listUrlRoles();
}
