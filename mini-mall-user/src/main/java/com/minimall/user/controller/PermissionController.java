package com.minimall.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.minimall.common.core.domain.Result;
import com.minimall.user.entity.SysPermission;
import com.minimall.user.service.IPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台权限查看 Controller（G9 RBAC）
 * <p>
 * 权限是内置的（跟着接口走），一般不在后台增删，所以只提供"列出全部"给配角色时选。
 */
@RestController
@RequestMapping("/admin/permission")
public class PermissionController {

    @Autowired
    private IPermissionService permissionService;

    /** 列出所有权限，按 sort 升序（前端配权限勾选用） */
    @GetMapping("/list")
    public Result<List<SysPermission>> list() {
        return Result.success(permissionService.list(
                new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getSort)));
    }
}
