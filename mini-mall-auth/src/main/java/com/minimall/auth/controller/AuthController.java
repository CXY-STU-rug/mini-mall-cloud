package com.minimall.auth.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.minimall.auth.client.UserFeignClient;
import com.minimall.auth.constant.EmailAuthConstants;
import com.minimall.auth.vo.AuthResponse;
import com.minimall.auth.dto.LoginRequest;
import com.minimall.auth.dto.ResetPasswordDTO;
import com.minimall.auth.dto.UserLoginDTO;
import com.minimall.auth.dto.UserRegisterDTO;
import com.minimall.auth.enums.LoginType;
import com.minimall.auth.model.User;
import com.minimall.auth.service.LoginService;
import com.minimall.common.core.domain.Result;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.auth.security.LoginUser;
import com.minimall.common.security.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
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

    /**
     * Spring Security 认证入口。
     * 调它的 authenticate() 会自动走：FeignUserDetailsService 查用户 → PasswordEncoder 比对密码 → 禁用检查。
     */
    @Autowired
    private AuthenticationManager authenticationManager;

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
        // ① 交给 Spring Security 认证。authenticate() 内部依次做：
        //    FeignUserDetailsService 按用户名查用户 → PasswordEncoder(BCrypt) 比对密码 →
        //    LoginUser.isEnabled() 检查是否被禁用。任一步失败都会抛异常。
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword()));
        } catch (DisabledException e) {
            // status==0 被禁用：保留原来的 403 语义
            throw new BusinessException(403, "账号已被禁用, 请联系管理员");
        } catch (AuthenticationException e) {
            // 用户不存在 / 密码错：统一提示、不区分，防用户名枚举
            throw new BusinessException("用户名或密码错误");
        }

        // ② 认证通过：principal 就是我们的 LoginUser，取出业务 User 去签自家 JWT
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        User user = loginUser.getUser();
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        user.setPassword(null);   // 兜底：密文绝不出网关
        return Result.success(new AuthResponse(token, user));
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
    // ①.8 找回密码 (邮箱验证码重置)
    // ═══════════════════════════════════════════════════════════

    /**
     * 找回密码
     *
     * 流程:
     *   ① 从 Redis 取该邮箱的验证码, 比对 dto.code
     *   ② Feign 按邮箱查用户 (不存在 → 400)
     *   ③ BCrypt 加密新密码, Feign 调 internal 接口写库
     *   ④ 删除 Redis 验证码 (防重放)
     *
     * 注: 发验证码仍用现有 POST /auth/email/code (不新增接口, 复用逻辑)
     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        // ① 验证码校验
        String storedCode = stringRedisTemplate.opsForValue()
                .get(EmailAuthConstants.CODE_PREFIX + dto.getEmail());
        if (storedCode == null || !storedCode.equals(dto.getCode())) {
            throw new BusinessException("验证码无效或已过期");
        }

        // ② 查用户 (不存在说明该邮箱没有绑定账号)
        Result<User> userResult = userFeignClient.getByEmail(dto.getEmail());
        if (userResult.getCode() != 200) {
            throw new BusinessException("用户服务暂不可用，请稍后再试");
        }
        User user = userResult.getData();
        if (user == null) {
            throw new BusinessException("该邮箱未绑定任何账号，请先注册或绑定邮箱");
        }

        // ③ 加密新密码, 通过 internal 接口写库
        String encodedPwd = ENCODER.encode(dto.getNewPassword());
        Map<String, String> body = new HashMap<>();
        body.put("password", encodedPwd);
        Result<Void> updateResult = userFeignClient.updatePassword(user.getId(), body);
        if (updateResult.getCode() != 200) {
            throw new BusinessException("密码重置失败，请重试");
        }

        // ④ 删除验证码 (一次性, 防重放攻击)
        stringRedisTemplate.delete(EmailAuthConstants.CODE_PREFIX + dto.getEmail());
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
