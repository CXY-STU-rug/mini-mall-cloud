package com.minimall.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.minimall.user.entity.SysPermission;

/**
 * 权限 Service（G9 RBAC）
 * <p>
 * 目前只用到 IService 自带的 list（后台配权限时列出所有可选权限），无自定义方法。
 */
public interface IPermissionService extends IService<SysPermission> {
}
