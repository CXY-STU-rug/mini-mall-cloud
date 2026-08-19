package com.minimall.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minimall.user.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色关系 Mapper（G9 RBAC）
 * <p>
 * 继承 BaseMapper：给用户配角色 = insert/delete 这张关系表。
 * 额外 1 个自定义查询：查某用户拥有的所有角色码，供网关灌 rbac:user_roles:{userId}。
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 查某用户拥有的所有角色码（role_code）。
     * <p>
     * 桥表只有 role_id，role_code 在 sys_role，所以要 JOIN。
     * 一个用户可能有多个角色 → 返回 List。
     */
    @Select("""
            SELECT r.role_code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND r.status = 1
              AND r.is_deleted = 0
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
