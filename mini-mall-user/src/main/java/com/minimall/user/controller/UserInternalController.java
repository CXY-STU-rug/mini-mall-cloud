package com.minimall.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimall.common.core.domain.Result;
import com.minimall.user.entity.User;
import com.minimall.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import java.time.LocalDateTime;

/**
 * Internal Controller (AUTH 阶段新增, 给 mini-mall-auth 服务用)
 *
 * 3 个端点:
 *   GET  /user/internal/by-username/{name}      按用户名查 (含 password 密文, 仅 auth 内部用)
 *   GET  /user/internal/by-oauth/{prov}/{id}    按 OAuth 主键查 (回调用)
 *   POST /user/internal                         创建用户 (注册 / OAuth 首次)
 *
 * ⚠️ 安全:
 *   - 网关 AuthGlobalFilter 黑名单已禁止外部访问 /user/internal/**
 *   - Feign 不走网关 (走 Nacos 直连 :9001), 所以 auth 服务能调
 *   - 暴露 password 密文给 auth 服务, 不能让前端直接拿到
 */
@RestController
@RequestMapping("/user/internal")
public class UserInternalController {

    @Autowired
    private UserMapper userMapper;

    // ─── ① 按 username 查 (本地登录用) ──────────────────────────
    @GetMapping("/by-username/{username}")
    public Result<User> getByUsername(@PathVariable("username") String username) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        // 查不到返 Result.success(null), 不抛异常 (让 auth 自己判 null 决定 "用户不存在" 还是建账号)
        return Result.success(userMapper.selectOne(wrapper));
    }

    // ─── ①.5 按 email 查 (EMAIL-1 邮箱验证码登录用) ──────────────
    @GetMapping("/by-email/{email}")
    public Result<User> getByEmail(@PathVariable("email") String email) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("email", email);
        // 同 by-username 约定: 查不到返 success(null), auth 拿 null 决定自动注册
        // last("LIMIT 1") 兜底: email 列没建唯一索引, 万一历史数据有重复不至于 selectOne 抛异常
        wrapper.last("LIMIT 1");
        return Result.success(userMapper.selectOne(wrapper));
    }

    // ─── ② 按 OAuth (provider, oauthId) 查 (OAuth 回调用) ────────
    @GetMapping("/by-oauth/{provider}/{oauthId}")
    public Result<User> getByOauth(@PathVariable("provider") String provider,
                                    @PathVariable("oauthId") String oauthId) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("oauth_provider", provider).eq("oauth_id", oauthId);
        return Result.success(userMapper.selectOne(wrapper));
    }

    // ─── ③.5 按 id 更新密码 (auth 找回密码时 Feign 调) ───────────
    /**
     * 更新指定用户的密码。
     * body: { "password": "BCrypt密文" } — 加密由 auth 服务完成, 这里只做写库
     * ⚠️ 仅内部接口, 网关禁止外部访问 /user/internal/**
     */
    @PutMapping("/{id}/password")
    public Result<Void> updatePassword(@PathVariable Long id,
                                       @RequestBody Map<String, String> body) {
        User update = new User();
        update.setId(id);
        update.setPassword(body.get("password"));
        userMapper.updateById(update);
        return Result.success();
    }

    // ─── ③ 创建用户 (本地注册 / OAuth 首次) ─────────────────────
    @PostMapping
    public Result<User> createUser(@RequestBody User user) {
        // 兜底设时间, auth 那边没设也不会 NPE
        LocalDateTime now = LocalDateTime.now();
        if (user.getCreateTime() == null) user.setCreateTime(now);
        if (user.getUpdateTime() == null) user.setUpdateTime(now);
        // 默认值
        if (user.getRole() == null) user.setRole((byte) 0);
        if (user.getStatus() == null) user.setStatus((byte) 1);

        userMapper.insert(user);   // MyBatis-Plus 自动回填自增 id 到 user.id
        return Result.success(user);
    }
}
