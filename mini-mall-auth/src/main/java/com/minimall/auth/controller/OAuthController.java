package com.minimall.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.auth.client.UserFeignClient;
import com.minimall.auth.dto.AuthResponse;
import com.minimall.auth.model.User;
import com.minimall.auth.properties.GithubOAuthProperties;
import com.minimall.common.core.domain.Result;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.security.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * OAuth2 第三方登录 Controller (从 user 服务搬到 auth, 路径前缀改 /auth/oauth)
 *
 * 端点 (网关代理 /auth/oauth/** → auth 服务):
 *   GET /auth/oauth/github/login    → 返 GitHub 授权页 URL
 *   GET /auth/oauth/github/callback → GitHub 跳回这里, 拿 code 换 mini-mall JWT
 *
 * 跟原 user 服务的差别:
 *   - UserMapper 调用全改成 UserFeignClient (跨服务调 user)
 *   - 返回类型由 Map 改成 AuthResponse, 跟 /auth/login 对齐
 */
@RestController
@RequestMapping("/auth/oauth")
public class OAuthController {

    @Autowired
    private GithubOAuthProperties githubOAuthProperties;

    @Autowired
    private RestTemplate restTemplate;          // 调 GitHub HTTP

    @Autowired
    private UserFeignClient userFeignClient;    // 调 user 服务 internal 接口 (取代原 UserMapper)

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    // ═══════════════════════════════════════════════════════════
    // ① 拿 GitHub 授权页 URL
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/github/login")
    public Result<Map<String, String>> githubLogin() {
        // redirect_uri 必须 URL 编码
        String encodedCallback = URLEncoder.encode(
                githubOAuthProperties.getCallbackUrl(),
                StandardCharsets.UTF_8
        );

        String authorizeUrl = String.format(
                "%s?client_id=%s&redirect_uri=%s&scope=read:user user:email",
                githubOAuthProperties.getAuthorizeUrl(),
                githubOAuthProperties.getClientId(),
                encodedCallback
        );

        Map<String, String> data = new HashMap<>();
        data.put("url", authorizeUrl);
        return Result.success(data);
    }

    // ═══════════════════════════════════════════════════════════
    // ② GitHub 跳回来 → 4 步换 mini-mall JWT
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/github/callback")
    public Result<AuthResponse> githubCallback(@RequestParam String code) {
        try {
            // ─── 步骤 1: code → access_token (POST github.com) ─────────
            String tokenUrl = String.format(
                    "%s?client_id=%s&client_secret=%s&code=%s",
                    githubOAuthProperties.getTokenUrl(),
                    githubOAuthProperties.getClientId(),
                    githubOAuthProperties.getClientSecret(),
                    code
            );
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(null, headers);
            String tokenJson = restTemplate.postForObject(tokenUrl, entity, String.class);
            System.out.println("[OAuth] GitHub /access_token response = " + tokenJson);

            JsonNode tokenNode = objectMapper.readTree(tokenJson);
            String accessToken = tokenNode.path("access_token").asText();
            if (accessToken == null || accessToken.isEmpty()) {
                return Result.error("GitHub access_token 换取失败: " + tokenJson);
            }

            // ─── 步骤 2: access_token → 用户信息 (GET api.github.com) ──
            HttpHeaders userHeaders = new HttpHeaders();
            userHeaders.set("Accept", "application/json");
            userHeaders.setBearerAuth(accessToken);
            HttpEntity<Void> userEntity = new HttpEntity<>(userHeaders);

            ResponseEntity<String> userResp = restTemplate.exchange(
                    githubOAuthProperties.getUserInfoUrl(),
                    HttpMethod.GET,
                    userEntity,
                    String.class
            );
            String userJson = userResp.getBody();
            System.out.println("[OAuth] GitHub /user response = " + userJson);

            JsonNode userNode = objectMapper.readTree(userJson);
            String githubUserId = userNode.path("id").asText();
            String githubLogin  = userNode.path("login").asText();
            String githubEmail  = userNode.path("email").asText("");
            String githubAvatar = userNode.path("avatar_url").asText("");

            // ─── 步骤 3: 经 Feign 查/插 user (替代原 UserMapper) ─────────
            // ⭐ 关键差别: 原来直接 userMapper.selectOne(QueryWrapper), 现在改 Feign
            Result<User> findResp = userFeignClient.getByOauth("github", githubUserId);
            if (findResp.getCode() != 200) {
                // user 服务挂了
                throw new BusinessException(findResp.getMessage());
            }
            User user = findResp.getData();

            if (user == null) {
                // 首次 OAuth 登录, 建账号 (走 Feign POST /user/internal)
                User newUser = new User();
                newUser.setOauthProvider("github");
                newUser.setOauthId(githubUserId);
                newUser.setUsername("gh_" + githubLogin);
                newUser.setNickname(githubLogin);
                newUser.setEmail(githubEmail);
                newUser.setAvatar(githubAvatar);
                newUser.setRole((byte) 0);
                newUser.setStatus((byte) 1);
                newUser.setCreateTime(LocalDateTime.now());
                newUser.setUpdateTime(LocalDateTime.now());

                Result<User> createResp = userFeignClient.createUser(newUser);
                if (createResp.getCode() != 200 || createResp.getData() == null) {
                    throw new BusinessException("OAuth 用户创建失败: " + createResp.getMessage());
                }
                user = createResp.getData();   // user 服务回填的 id
            }

            // ─── 步骤 3.5: ⭐ SEC-2 禁用账号拦截 (跟 AuthController.login 同一条规则) ──
            //    老账号 OAuth 登录也要查 status: 被禁用的人换 GitHub 登录绕过 = 白禁了
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new BusinessException(403, "账号已被禁用, 请联系管理员");
            }

            // ─── 步骤 4: 签 mini-mall 自家 JWT (ADMIN 阶段: role 一起塞) ──
            String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
            user.setPassword(null);    // 兜底 (OAuth 用户密码本来就 null, 但保险起见)
            return Result.success(new AuthResponse(token, user));

        } catch (Exception e) {
            return Result.error("GitHub 登录失败: " + e.getMessage());
        }
    }
}
