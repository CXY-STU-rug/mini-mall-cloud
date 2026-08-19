package com.minimall.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.minimall.common.core.domain.Result;
import com.minimall.user.entity.SysRole;
import com.minimall.user.service.IRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台角色管理 Controller（G9 RBAC）
 * <p>
 * 路由前缀 /admin/role → 网关 AuthGlobalFilter 已鉴权，这里信任 X-User-Id，不再校验。
 * 一个 Controller = 一种资源（role），所以角色相关接口都归这里。
 */
@RestController
@RequestMapping("/admin/role")
public class RoleController {

    @Autowired
    private IRoleService roleService;

    /** ① 分页 + 关键词（角色名/角色码模糊） */
    @GetMapping("/page")
    public Result<IPage<SysRole>> page(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "10") long size,
                                       @RequestParam(required = false) String keyword) {
        Page<SysRole> p = Page.of(page, size);
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(SysRole::getRoleName, keyword)
                              .or().like(SysRole::getRoleCode, keyword));
        }
        wrapper.orderByDesc(SysRole::getId);
        return Result.success(roleService.page(p, wrapper));
    }

    /** ② 新增角色 */
    @PostMapping
    public Result<Long> create(@RequestBody SysRole role) {
        roleService.save(role);          // IService 自带，插入后回填自增 id
        return Result.success(role.getId());
    }

    /** ③ 修改角色 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysRole role) {
        role.setId(id);                  // 以路径 id 为准，防篡改 body
        roleService.updateById(role);
        return Result.success();
    }

    /** ④ 删除角色（@TableLogic 逻辑删） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.removeById(id);
        return Result.success();
    }

    /** ⑤ 查角色已配的权限 id 列表（回显勾选框） */
    @GetMapping("/{id}/permissions")
    public Result<List<Long>> permissions(@PathVariable Long id) {
        return Result.success(roleService.listPermissionIdsByRole(id));
    }

    /** ⑥ 给角色配权限（全量覆盖，内部会刷新 Redis 门锁清单） */
    @PutMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                          @RequestBody List<Long> permissionIds) {
        roleService.assignPermissions(id, permissionIds);
        return Result.success();
    }

    /** ⑦ 查某用户已挂的角色 id 列表（回显勾选框） */
    @GetMapping("/user/{userId}")
    public Result<List<Long>> userRoles(@PathVariable Long userId) {
        return Result.success(roleService.listRoleIdsByUser(userId));
    }

    /** ⑧ 给用户配角色（全量覆盖，内部会刷新该用户的 Redis 钥匙串） */
    @PutMapping("/user/{userId}")
    public Result<Void> assignUserRoles(@PathVariable Long userId,
                                        @RequestBody List<Long> roleIds) {
        roleService.assignRolesToUser(userId, roleIds);
        return Result.success();
    }
}
