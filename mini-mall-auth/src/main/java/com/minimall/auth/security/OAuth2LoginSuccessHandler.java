package com.minimall.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.auth.client.UserFeignClient;
import com.minimall.auth.model.User;
import com.minimall.common.core.domain.Result;
import com.minimall.common.security.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * OAuth2 登录成功后的收尾处理器。
 *
 * ⭐ 关键：授权码流程（跳 GitHub → 用户点授权 → 回调换 access_token → 拉 GitHub 用户信息）
 *    这一整套【全部由 spring-boot-starter-oauth2-client 自动完成】，
 *    不再需要原来 OAuthController 里手写 RestTemplate 拼 URL / 换 token / 调用户接口那几十行。
 *
 * 走到本方法时，authentication.getPrincipal() 已经是一个装好 GitHub 用户属性的 OAuth2User。
 * 我们只需做"业务收尾"：拿 GitHub 身份 → 查/建本地用户 → 签自家 JWT → 带 token 跳回前端。
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserFeignClient userFeignClient;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    /** 前端 OAuth 回跳页；生产可用 Nacos / 环境变量覆盖 */
    @Value("${oauth.frontend-callback-url:http://localhost:5174/oauth/github/callback}")
    private String frontendCallbackUrl;

    public OAuth2LoginSuccessHandler(UserFeignClient userFeignClient,
                                     JwtUtil jwtUtil,
                                     ObjectMapper objectMapper) {
        this.userFeignClient = userFeignClient;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        // ① 框架已把 GitHub 用户信息拉回来，principal 就是它
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        // ② 取 GitHub 返回的字段（跟原来手写拿到的字段一模一样）
        String githubId = String.valueOf(oauthUser.<Object>getAttribute("id"));
        String login = oauthUser.getAttribute("login");
        String email = oauthUser.getAttribute("email");
        String avatar = oauthUser.getAttribute("avatar_url");

        // ③ 按 (provider, oauthId) 查本地是否已绑定该 GitHub 账号
        Result<User> findResp = userFeignClient.getByOauth("github", githubId);
        User user = (findResp != null && findResp.getCode() == 200) ? findResp.getData() : null;

        // ④ 第一次用 GitHub 登录 → 自动建号
        if (user == null) {
            User newUser = new User();
            newUser.setOauthProvider("github");
            newUser.setOauthId(githubId);
            newUser.setUsername("gh_" + login);
            newUser.setNickname(login);
            newUser.setEmail(email);
            newUser.setAvatar(avatar);
            newUser.setRole((byte) 0);
            newUser.setStatus((byte) 1);
            newUser.setCreateTime(LocalDateTime.now());
            newUser.setUpdateTime(LocalDateTime.now());
            Result<User> createResp = userFeignClient.createUser(newUser);
            user = createResp.getData();
        }

        // ⑤ 签自家 JWT（跟本地登录同一套 token，前端不用区分登录来源）
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        user.setPassword(null);

        // ⑥ 把 user 序列化成 Base64，跟 token 一起塞进跳转地址的 # 片段，302 跳回前端
        String userJson = objectMapper.writeValueAsString(user);
        String userPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(userJson.getBytes(StandardCharsets.UTF_8));
        String target = frontendCallbackUrl + "#token=" + token + "&user=" + userPayload;

        response.sendRedirect(target);   // 前端从 # 里取 token 写入登录态，OAuth 登录完成
    }
}
