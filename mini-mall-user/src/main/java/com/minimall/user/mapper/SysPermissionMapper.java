package com.minimall.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimall.user.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限 Mapper（G9 RBAC）
 * <p>
 * 继承 BaseMapper 即可，后台"列权限"用 selectList 就够。
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {
}
