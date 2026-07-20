package com.minimall.auth.controller;

import com.minimall.auth.constant.EmailAuthConstants;
import com.minimall.auth.dto.AuthResponse;
import com.minimall.auth.dto.EmailLoginDTO;
import com.minimall.auth.dto.LoginRequest;
import com.minimall.auth.dto.SendEmailCodeDTO;
import com.minimall.auth.enums.LoginType;
import com.minimall.auth.service.EmailService;
import com.minimall.auth.service.LoginService;
import com.minimall.common.core.domain.Result;
import com.minimall.common.core.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * EMAIL-1: 邮箱验证码登录
 *
 * 2 个端点 (都在网关白名单 /auth/** 下, 免登录可调):
 *   POST /auth/email/code   {email}        发 6 位验证码到邮箱
 *   POST /auth/email/login  {email, code}  验码登录, 未注册的邮箱【自动注册】
 *
 * 验码 + 建号逻辑已抽到 EmailCodeLoginStrategy, 本控制器的 /login 只做委托;
 * 发码逻辑仍留在这里 (它不属于"登录", 是登录的前置步骤)。
 */
@RestController
@RequestMapping("/auth/email")
public class EmailAuthController {

    /** SecureRandom 而不是 Random: 验证码是安全场景, Random 种子可被推测 */
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 异步邮件发送服务 (走 mailExecutor 线程池, 不阻塞本请求线程) */
    @Autowired
    private EmailService emailService;

    /** 登录统一收口 (策略+工厂) */
    @Autowired
    private LoginService loginService;

    // ═══════════════════════════════════════════════════════════
    // ① 发验证码
    // ═══════════════════════════════════════════════════════════

    /**
     * 流程:
     *   ① 60 秒重发限制 (setIfAbsent = Redis SETNX, 原子的"没有才放"; 已存在说明刚发过)
     *   ② 生成 6 位码, 先写 Redis 再发邮件 (顺序很重要, 见下)
     *   ③ 异步发送 (丢 mailExecutor 线程池), 主线程立即返回
     *
     * 为什么【先存 Redis 再发邮件】:
     *   反过来的话 —— 邮件已发出去、存 Redis 失败, 用户拿着收到的码来登录却验不过, 更糟。
     *
     * 同步 → 异步的取舍:
     *   SMTP 发送 1~2 秒, 原本会阻塞整个 HTTP 请求。改异步后接口毫秒级返回、吞吐更高,
     *   代价是主线程无法感知发送结果 —— 所以"发送失败回滚重发限制"的补偿逻辑
     *   已移入 EmailService 的异步方法内部处理。
     */
    @PostMapping("/code")
    public Result<Void> sendCode(@Valid @RequestBody SendEmailCodeDTO dto) {
        String email = dto.getEmail();

        // ① 60 秒内只许发一次 (防止接口被刷 → 邮箱轰炸别人 + 耗光发信额度)
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(EmailAuthConstants.LIMIT_PREFIX + email, "1",
                        EmailAuthConstants.RESEND_LIMIT_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(first)) {
            throw new BusinessException(429, "发送太频繁, 请 60 秒后再试");
        }

        // ② 生成 [100000, 999999] 的 6 位码, 存 Redis 5 分钟
        //    重发会直接覆盖旧码 → 天然"旧码作废", 顺手把错误计数也清零
        String code = String.valueOf(RANDOM.nextInt(900_000) + 100_000);
        stringRedisTemplate.opsForValue()
                .set(EmailAuthConstants.CODE_PREFIX + email, code,
                        EmailAuthConstants.CODE_TTL_MINUTES, TimeUnit.MINUTES);
        stringRedisTemplate.delete(EmailAuthConstants.ERR_PREFIX + email);

        // ③ 异步发邮件: 提交给线程池后立即返回, 不等 SMTP。
        //    失败补偿(回滚 LIMIT_PREFIX 限制 key)在 EmailService 内部完成。
        emailService.sendCodeMail(email, code, EmailAuthConstants.CODE_TTL_MINUTES,
                EmailAuthConstants.LIMIT_PREFIX + email);

        return Result.success();
    }

    // ═══════════════════════════════════════════════════════════
    // ② 验证码登录 (未注册自动注册) —— 委托给策略
    // ═══════════════════════════════════════════════════════════

    /**
     * 组装 EMAIL_CODE 登录请求, 交 LoginService 统一处理。
     * 具体验码 + 建号逻辑见 EmailCodeLoginStrategy, 禁用拦截/签 token 见 LoginService。
     */
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody EmailLoginDTO dto) {
        LoginRequest request = new LoginRequest();
        request.setLoginType(LoginType.EMAIL_CODE);
        request.setEmail(dto.getEmail());
        request.setCode(dto.getCode());
        return Result.success(loginService.login(request));
    }
}
