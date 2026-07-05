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
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * GitHub OAuth 登录。
 *
 * 兼容两种方式：
 * 1. 直接请求 callback：返回 JSON AuthResponse；
 * 2. 从前端登录页发起：GitHub 回到后端 callback 后，再 302 回前端 callback 页写入登录态。
 */
@RestController
@RequestMapping("/auth/oauth")
public class OAuthController {

    @Autowired
    private GithubOAuthProperties githubOAuthProperties;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 前端 OAuth 回跳页。
     *
     * 不需要写进真实 application.yml；生产可用环境变量或 Nacos 覆盖：
     * oauth.frontend-callback-url=https://your-domain.com/oauth/github/callback
     */
    @Value("${oauth.frontend-callback-url:http://localhost:5174/oauth/github/callback}")
    private String frontendCallbackUrl;

    /**
     * 返回 GitHub 授权页 URL。
     *
     * redirect 是用户登录前想去的商城页面，只允许站内路径。
     */
    @GetMapping("/github/login")
    public Result<Map<String, String>> githubLogin(
            @RequestParam(required = false) String redirect
    ) {
        String encodedBackendCallback = urlEncode(githubOAuthProperties.getCallbackUrl());
        String encodedScope = urlEncode("read:user user:email");
        String encodedState = urlEncode(encodeState(normalizeRedirect(redirect)));

        String authorizeUrl = String.format(
                "%s?client_id=%s&redirect_uri=%s&scope=%s&state=%s",
                githubOAuthProperties.getAuthorizeUrl(),
                githubOAuthProperties.getClientId(),
                encodedBackendCallback,
                encodedScope,
                encodedState
        );

        Map<String, String> data = new HashMap<>();
        data.put("url", authorizeUrl);
        return Result.success(data);
    }

    /**
     * GitHub 回调。
     *
     * 有 state：说明来自前端登录页，回跳前端 callback 页面；
     * 无 state：保留原来的 JSON 返回，方便接口调试。
     */
    @GetMapping("/github/callback")
    public Result<AuthResponse> githubCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            HttpServletResponse servletResponse
    ) {
        try {
            AuthResponse authResponse = exchangeCodeForAuthResponse(code);

            if (state != null && !state.isBlank()) {
                redirectToFrontend(servletResponse, authResponse, decodeState(state));
                return null;
            }

            return Result.success(authResponse);

        } catch (Exception e) {
            return Result.error("GitHub 登录失败: " + e.getMessage());
        }
    }

    private AuthResponse exchangeCodeForAuthResponse(String code) throws Exception {
        String tokenUrl = String.format(
                "%s?client_id=%s&client_secret=%s&code=%s",
                githubOAuthProperties.getTokenUrl(),
                githubOAuthProperties.getClientId(),
                githubOAuthProperties.getClientSecret(),
                urlEncode(code)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        String tokenJson = restTemplate.postForObject(tokenUrl, entity, String.class);

        JsonNode tokenNode = objectMapper.readTree(tokenJson);
        String accessToken = tokenNode.path("access_token").asText();
        if (accessToken == null || accessToken.isEmpty()) {
            throw new BusinessException("GitHub access_token 换取失败");
        }

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

        JsonNode userNode = objectMapper.readTree(userResp.getBody());
        String githubUserId = userNode.path("id").asText();
        String githubLogin = userNode.path("login").asText();
        String githubEmail = userNode.path("email").asText("");
        String githubAvatar = userNode.path("avatar_url").asText("");

        Result<User> findResp = userFeignClient.getByOauth("github", githubUserId);
        if (findResp == null || findResp.getCode() != 200) {
            throw new BusinessException(findResp == null ? "用户服务暂不可用" : findResp.getMessage());
        }

        User user = findResp.getData();
        if (user == null) {
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
            if (createResp == null || createResp.getCode() != 200 || createResp.getData() == null) {
                throw new BusinessException("OAuth 用户创建失败");
            }
            user = createResp.getData();
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        user.setPassword(null);
        return new AuthResponse(token, user);
    }

    private void redirectToFrontend(HttpServletResponse response, AuthResponse authResponse, String redirect)
            throws IOException {
        String userJson = objectMapper.writeValueAsString(authResponse.getUser());
        String userPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(userJson.getBytes(StandardCharsets.UTF_8));

        String target = frontendCallbackUrl
                + "#token=" + urlEncode(authResponse.getToken())
                + "&user=" + urlEncode(userPayload)
                + "&redirect=" + urlEncode(normalizeRedirect(redirect));

        response.sendRedirect(target);
    }

    private String encodeState(String redirect) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(normalizeRedirect(redirect).getBytes(StandardCharsets.UTF_8));
    }

    private String decodeState(String state) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(state);
            return normalizeRedirect(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return "/";
        }
    }

    private String normalizeRedirect(String redirect) {
        if (redirect == null || redirect.isBlank() || !redirect.startsWith("/") || redirect.startsWith("//")) {
            return "/";
        }
        return redirect;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
