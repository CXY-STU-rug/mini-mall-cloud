package com.minimall.auth.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.minimall.auth.client.UserFeignClient;
import com.minimall.auth.dto.AuthResponse;
import com.minimall.auth.dto.LoginRequest;
import com.minimall.auth.dto.UserLoginDTO;
import com.minimall.auth.dto.UserRegisterDTO;
import com.minimall.auth.enums.LoginType;
import com.minimall.auth.model.User;
import com.minimall.auth.service.LoginService;
import com.minimall.common.core.domain.Result;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.security.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 本地账号认证 Controller (从 user 服务 UserController 抽出来)
 *
 * 2 个端点:
 *   POST /auth/login    本地账号登录, 返 AuthResponse(token, user)
 *   POST /auth/register 注册新账号, 直接登录返 AuthResponse
 *
 * 与原 UserController 的差别:
 *   - 路径前缀 /user/login → /auth/login
 *   - 不直接调 UserMapper, 改 Feign 调 user 服务 internal 接口
 *   - 返 String token → 返 AuthResponse(token, user) 跟 OAuth 接口统一
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    /** BCrypt 加密器, 无状态线程安全, 全类共享一份 */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private JwtUtil jwtUtil;

    /** 登录统一收口 (策略+工厂) */
    @Autowired
    private LoginService loginService;

    /** 登出黑名单存这里, key 前缀跟网关校验时一致 */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 黑名单 key 前缀 (auth 写 / gateway 读, 必须一致) */
    public static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    // ═══════════════════════════════════════════════════════════
    // ① 本地登录
    // ═══════════════════════════════════════════════════════════

    /**
     * 本地登录
     *
     * 流程:
     *   ① Feign 调 user 服务 byUsername 查 User (含 BCrypt 密文)
     *   ② BCrypt.matches(明文, 密文) 比对
     *   ③ jwtUtil 签 token
     *   ④ 返 AuthResponse(token, 清掉 password 的 user)
     *
     * Sentinel 限流配置跟原 UserController.login 一样 (走 user 服务时是 loginResource).
     */
    @PostMapping("/login")
    @SentinelResource(
            value = "authLoginResource",
            blockHandler = "loginBlock",
            fallback = "loginFallback"
    )
    public Result<AuthResponse> login(@Valid @RequestBody UserLoginDTO dto) {
        // 组装 PASSWORD 登录请求, 交 LoginService 统一处理。
        // 密码校验见 PasswordLoginStrategy; 禁用拦截 + 签 token 见 LoginService。
        LoginRequest request = new LoginRequest();
        request.setLoginType(LoginType.PASSWORD);
        request.setUsername(dto.getUsername());
        request.setPassword(dto.getPassword());
        return Result.success(loginService.login(request));
    }

    /** Sentinel 限流降级 */
    public Result<AuthResponse> loginBlock(UserLoginDTO dto, BlockException ex) {
        return Result.error(429, "登录请求太频繁, 请稍后再试 (触发规则: "
                + ex.getClass().getSimpleName() + ")");
    }

    /** Sentinel 业务异常降级 (透传给 GlobalExceptionHandler 处理) */
    public Result<AuthResponse> loginFallback(UserLoginDTO dto, Throwable ex) {
        if (ex instanceof RuntimeException re) throw re;
        throw new RuntimeException(ex);
    }

    // ═══════════════════════════════════════════════════════════
    // ①.5 登出 (把当前 token 拉黑, 使其在过期前也立即失效)
    // ═══════════════════════════════════════════════════════════
    /**
     * 登出。
     * <p>
     * JWT 是无状态的, 平时"登出"只是前端删 token。但删掉的 token 在过期前(7天)仍有效,
     * 被截获就能重放。这里把它写进 Redis 黑名单, TTL=token 剩余有效期, 网关每次校验时查黑名单,
     * 命中就 401 → 实现"服务端强制失效"。token 自然过期后黑名单也自动清, 不占空间。
     * <p>
     * 说明: /auth/** 在网关白名单里免鉴权放行, 但会原样透传 Authorization 头, 所以这里能拿到 token。
     */
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // 没带 token 也算登出成功 (前端删本地即可, 不报错)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Result.success();
        }
        String token = authHeader.substring(7);
        try {
            // 解析拿过期时间, 算还剩多久 —— 黑名单只需保留到 token 本来的过期点
            Claims claims = jwtUtil.parseToken(token);
            long ttl = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (ttl > 0) {
                stringRedisTemplate.opsForValue()
                        .set(BLACKLIST_PREFIX + token, "1", ttl, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            // token 已过期/本就无效 → 无需拉黑, 直接当登出成功
        }
        return Result.success();
    }

    // ═══════════════════════════════════════════════════════════
    // ② 本地注册
    // ═══════════════════════════════════════════════════════════

    /**
     * 注册
     *
     * 流程:
     *   ① Feign 调 byUsername 查重 (查到说明用户名已存在 → 报错)
     *   ② BCrypt 加密密码
     *   ③ 组装 User, Feign 调 createUser 入库
     *   ④ 签 token, 注册即登录, 返 AuthResponse
     */
    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody UserRegisterDTO dto) {
        // ① 查重
        Result<User> existsResp = userFeignClient.getByUsername(dto.getUsername());
        if (existsResp.getCode() != 200) {
            throw new BusinessException(existsResp.getMessage());
        }
        if (existsResp.getData() != null) {
            throw new BusinessException("用户名已存在");
        }

        // ② 加密
        String encryptedPassword = ENCODER.encode(dto.getPassword());

        // ③ 组装并入库 (createTime/updateTime 让 user 服务 internal Controller 兜底, 这里也设一下保险)
        User newUser = new User();
        newUser.setUsername(dto.getUsername());
        newUser.setPassword(encryptedPassword);
        // C 端 WEB.2 注册表单可选字段; null 直接传给 user 服务, DB 字段允许 null
        newUser.setPhone(dto.getPhone());
        newUser.setNickname(dto.getNickname());
        newUser.setRole((byte) 0);
        newUser.setStatus((byte) 1);
        newUser.setCreateTime(LocalDateTime.now());
        newUser.setUpdateTime(LocalDateTime.now());

        Result<User> createResp = userFeignClient.createUser(newUser);
        if (createResp.getCode() != 200 || createResp.getData() == null) {
            throw new BusinessException("注册失败: " + createResp.getMessage());
        }
        User savedUser = createResp.getData();   // 含 user 服务回填的 id

        // ④ 签 token, 注册即登录 (新注册用户 role=0)
        String token = jwtUtil.generateToken(savedUser.getId(), savedUser.getUsername(), savedUser.getRole());
        savedUser.setPassword(null);             // 兜底
        return Result.success(new AuthResponse(token, savedUser));
    }
}
