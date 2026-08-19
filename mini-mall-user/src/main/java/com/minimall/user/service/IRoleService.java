package com.minimall.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.minimall.user.entity.SysRole;

import java.util.List;

/**
 * 角色 Service（G9 RBAC）
 * <p>
 * 角色本身的增删改查用 IService 自带方法（save/updateById/removeById/page）。
 * 这里只声明"跨关系表"的自定义方法：角色↔权限、用户↔角色。
 */
public interface IRoleService extends IService<SysRole> {

    /** 查某角色已配的权限 id 列表（回显给后台勾选框） */
    List<Long> listPermissionIdsByRole(Long roleId);

    /** 给角色重配权限（全量覆盖：删旧插新），并刷新 Redis 门锁清单 */
    void assignPermissions(Long roleId, List<Long> permissionIds);

    /** 查某用户已挂的角色 id 列表（回显） */
    List<Long> listRoleIdsByUser(Long userId);

    /** 给用户重配角色（全量覆盖），并刷新该用户的 Redis 钥匙串 */
    void assignRolesToUser(Long userId, List<Long> roleIds);
}
