package com.minimall.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minimall.user.entity.SysRole;
import com.minimall.user.entity.SysRolePermission;
import com.minimall.user.entity.SysUserRole;
import com.minimall.user.mapper.SysRoleMapper;
import com.minimall.user.mapper.SysRolePermissionMapper;
import com.minimall.user.mapper.SysUserRoleMapper;
import com.minimall.user.service.IRoleService;
import com.minimall.user.service.RbacCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色 Service 实现（G9 RBAC）
 * <p>
 * extends ServiceImpl 拿到角色表本身的 CRUD；跨表操作注入另外两张桥表的 Mapper。
 * 配权限 / 配角色成功后，必须调 RbacCacheService 刷新 Redis，否则网关还读旧数据。
 */
@Service
public class RoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements IRoleService {

    @Autowired
    private SysRolePermissionMapper rolePermissionMapper;
    @Autowired
    private SysUserRoleMapper userRoleMapper;
    @Autowired
    private RbacCacheService rbacCacheService;

    // ─── 角色↔权限 ───────────────────────────────
    @Override
    public List<Long> listPermissionIdsByRole(Long roleId) {
        // 查这个角色在关系表里的所有行，取出 permissionId
        return rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<SysRolePermission>()
                                .eq(SysRolePermission::getRoleId, roleId))
                .stream()
                .map(SysRolePermission::getPermissionId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        // 全量覆盖：先删这个角色的所有权限关系
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
        // 再插新的
        if (permissionIds != null) {
            for (Long pid : permissionIds) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionId(pid);
                rolePermissionMapper.insert(rp);
            }
        }
        // ⭐ 权限变了，刷新门锁清单，网关即时生效
        rbacCacheService.loadUrlRoles();
    }

    // ─── 用户↔角色 ───────────────────────────────
    @Override
    public List<Long> listRoleIdsByUser(Long userId) {
        return userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>()
                                .eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRolesToUser(Long userId, List<Long> roleIds) {
        // 全量覆盖：先删这个用户的所有角色关系
        userRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds != null) {
            for (Long rid : roleIds) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(rid);
                userRoleMapper.insert(ur);
            }
        }
        // ⭐ 该用户角色变了，刷新他的钥匙串
        rbacCacheService.loadUserRoles(userId);
    }
}
