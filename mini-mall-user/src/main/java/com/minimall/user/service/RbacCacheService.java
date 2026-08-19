package com.minimall.user.service;

import com.minimall.user.dto.UrlRoleDTO;
import com.minimall.user.mapper.SysRolePermissionMapper;
import com.minimall.user.mapper.SysUserRoleMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RBAC 缓存加载器（G9 核心）
 * <p>
 * 把 DB 里的"角色-权限"关系加载进 Redis，网关读 Redis 动态判权：
 *   ① rbac:url_roles        —— Hash：field="method:urlPattern" value="ROLE_A,ROLE_B"（门锁清单，全局一份）
 *   ② rbac:user_roles:{id}  —— Set ：某用户拥有的角色码（钥匙串，每人一份）
 * 网关判权 = 用户角色 ∩ 接口要求角色 ≠ 空 → 放行。
 */
@Service
public class RbacCacheService {

    private static final Logger log = LoggerFactory.getLogger(RbacCacheService.class);

    /** 门锁清单 key（全局 Hash）。网关必须用同一个 key 读，改动要同步。 */
    public static final String KEY_URL_ROLES = "rbac:url_roles";

    /** 用户钥匙串 key 前缀（每人一个 Set） */
    public static final String KEY_USER_ROLES_PREFIX = "rbac:user_roles:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private SysRolePermissionMapper sysRolePermissionMapper;
    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    /**
     * 服务启动时全量灌一次门锁清单。
     * try-catch 兜底：sys_* 表还没建 / DB 没就绪时，不阻断服务启动（网关会走降级）。
     */
    @PostConstruct
    public void init() {
        try {
            loadUrlRoles();
        } catch (Exception e) {
            log.warn("[RBAC] 启动加载 url_roles 失败（表可能未建），跳过，走网关降级。原因: {}", e.getMessage());
        }
    }

    /**
     * ① 全量加载"接口 → 角色"门锁清单到 Redis Hash。
     * 服务启动调、后台改角色↔权限后调。
     */
    public void loadUrlRoles() {
        // 1. 查平铺多行：同一 url 被 N 个角色授权就有 N 行
        List<UrlRoleDTO> list = sysRolePermissionMapper.listUrlRoles();

        // 2. 聚合：按 "method:urlPattern" 分组，把同组的 roleCode 拼成逗号串
        //    groupingBy(分组key, mapping(取roleCode, joining(",")))
        Map<String, String> map = list.stream().collect(Collectors.groupingBy(
                d -> d.getMethod() + ":" + d.getUrlPattern(),                 // 分组 key
                Collectors.mapping(UrlRoleDTO::getRoleCode, Collectors.joining(",")) // 下游：拼角色码
        ));

        // 3. 先清后写（防旧数据残留）。putAll 传空 map 会报错，必须判空。
        stringRedisTemplate.delete(KEY_URL_ROLES);
        if (!map.isEmpty()) {
            stringRedisTemplate.opsForHash().putAll(KEY_URL_ROLES, map);
        }
        log.info("[RBAC] url_roles 已加载 {} 条接口权限到 Redis", map.size());
    }

    /**
     * ② 加载某用户的角色钥匙串到 Redis Set。
     * 用户登录后 / 网关首次判权回源时调；给用户改角色后也要重新调刷新。
     */
    public void loadUserRoles(Long userId) {
        List<String> codes = sysUserRoleMapper.selectRoleCodesByUserId(userId);
        String key = KEY_USER_ROLES_PREFIX + userId;

        stringRedisTemplate.delete(key);
        if (!codes.isEmpty()) {
            // opsForSet().add 是可变参数，List 要转成 String[]
            stringRedisTemplate.opsForSet().add(key, codes.toArray(new String[0]));
        }
        log.info("[RBAC] user_roles:{} 已加载 {} 个角色", userId, codes.size());
    }
}
