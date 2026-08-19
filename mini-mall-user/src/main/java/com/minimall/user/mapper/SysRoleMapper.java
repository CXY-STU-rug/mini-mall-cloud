package com.minimall.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimall.user.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 Mapper（G9 RBAC）
 * <p>
 * 继承 BaseMapper 拿到通用 CRUD，后台角色管理直接用，无需自定义 SQL。
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}
