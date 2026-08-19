package com.minimall.auth.vo;   // 从 dto 迁到 vo：本类是"返回给前端"的视图对象，归 vo 更规范

import com.minimall.auth.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证响应视图对象（VO）—— 登录 / 注册 / OAuth 三个端点统一的返回体。
 *
 * 【演化来历】
 *   最早认证逻辑长在 user 服务里，返回是散的：
 *     /user/login   → 光秃秃一个 String token
 *     /user/oauth/* → 一个 Map { token, user }
 *   后来把"登录模块"从 user 服务拆出来、独立成 auth 微服务时，
 *   为了让前端一套解析吃遍所有认证端点，把上面两种散返回统一成了本类。
 *   （放 vo 包：它不落库、不对应任何表，只是"还给前端看"的视图对象。）
 *
 * 【字段说明】
 *   token —— mini-mall 自家签发的 JWT。前端拿到后存 localStorage，
 *            之后每次请求放进 Authorization: Bearer <token> 头，网关据此验身份。
 *   user  —— 当前登录用户的基本信息，供前端展示（头像/昵称等）。
 *            其 password 字段已加 @JsonIgnore，序列化时不会外泄给前端。
 */
@Data                 // Lombok：自动生成 getter/setter/toString/equals
@NoArgsConstructor    // 无参构造：Jackson 反序列化时需要
@AllArgsConstructor   // 全参构造：new AuthResponse(token, user) 一行建对象
public class AuthResponse {

    private String token;   // JWT 令牌，前端存 localStorage 并随请求带回
    private User user;       // 用户基本信息，password 已 @JsonIgnore 不外泄
}
