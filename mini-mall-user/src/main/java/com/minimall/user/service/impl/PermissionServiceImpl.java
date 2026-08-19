package com.minimall.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minimall.user.entity.SysPermission;
import com.minimall.user.mapper.SysPermissionMapper;
import com.minimall.user.service.IPermissionService;
import org.aspectj.apache.bcel.classfile.Unknown;
import org.springframework.stereotype.Service;

/**
 * 权限 Service 实现（G9 RBAC）—— 纯继承，用 IService 自带方法即可。
 */
@Service
public class PermissionServiceImpl extends ServiceImpl<SysPermissionMapper, SysPermission>
        implements IPermissionService {
}
