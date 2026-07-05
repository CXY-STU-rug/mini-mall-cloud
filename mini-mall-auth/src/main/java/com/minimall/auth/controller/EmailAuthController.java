package com.minimall.auth.controller;

import com.minimall.auth.client.UserFeignClient;
import com.minimall.auth.dto.AuthResponse;
import com.minimall.auth.dto.EmailLoginDTO;
import com.minimall.auth.dto.SendEmailCodeDTO;
import com.minimall.auth.model.User;
import com.minimall.common.core.domain.Result;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.security.util.JwtUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * EMAIL-1: 邮箱验证码登录
 *
 * 2 个端点 (都在网关白名单 /auth/** 下, 免登录可调):
 *   POST /auth/email/code   {email}        发 6 位验证码到邮箱
 *   POST /auth/email/login  {email, code}  验码登录, 未注册的邮箱【自动注册】
 *
 * 为什么"未注册自动注册"而不是报错"邮箱不存在":
 *   ① 少一步注册流程, 体验跟主流 App"验证码登录/注册二合一"一致
 *   ② 安全: 不暴露"这个邮箱有没有注册过"(否则可以拿接口探测用户)
 *   套路完全复用 OAuthController: 查不到 → 建号 → 签 token
 *
 * Redis 三个 key (都带 TTL, 不用手动清理):
 *   auth:email:code:{email}   验证码本体, 5 分钟
 *   auth:email:limit:{email}  60 秒重发限制 (存在=刚发过)
 *   auth:email:err:{email}    验错计数, 连错 5 次作废验证码 (防爆破: 6位数字只有100万种)
 */
@RestController
@RequestMapping("/auth/email")
public class EmailAuthController {

    private static final Logger log = LoggerFactory.getLogger(EmailAuthController.class);

    /** Redis key 前缀 (集中定义, 拼错一个字母就是"验证码永远不对"的隐性 bug) */
    private static final String CODE_PREFIX  = "auth:email:code:";
    private static final String LIMIT_PREFIX = "auth:email:limit:";
    private static final String ERR_PREFIX   = "auth:email:err:";

    /** 验证码有效期 5 分钟 / 重发间隔 60 秒 / 最多验错 5 次 */
    private static final long CODE_TTL_MINUTES = 5;
    private static final long RESEND_LIMIT_SECONDS = 60;
    private static final int MAX_ERR_COUNT = 5;

    /** SecureRandom 而不是 Random: 验证码是安全场景, Random 种子可被推测 */
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** Spring Boot 看到 spring.mail.* 配置就自动装配好这个 Bean */
    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private JwtUtil jwtUtil;

    /** QQ 邮箱强制要求: 信封上的发件人(from) 必须等于登录 SMTP 的账号, 所以直接注入配置 */
    @Value("${spring.mail.username}")
    private String fromEmail;

    // ═══════════════════════════════════════════════════════════
    // ① 发验证码
    // ═══════════════════════════════════════════════════════════

    /**
     * 流程:
     *   ① 60 秒重发限制 (setIfAbsent = Redis SETNX, 原子的"没有才放"; 已存在说明刚发过)
     *   ② 生成 6 位码, 先写 Redis 再发邮件 (顺序很重要, 见下)
     *   ③ SMTP 发送; 失败则回滚限制 key, 让用户能立刻重试
     *
     * 为什么【先存 Redis 再发邮件】:
     *   反过来的话 —— 邮件已发出去、存 Redis 失败, 用户拿着收到的码来登录却验不过, 更糟。
     */
    @PostMapping("/code")
    public Result<Void> sendCode(@Valid @RequestBody SendEmailCodeDTO dto) {
        String email = dto.getEmail();

        // ① 60 秒内只许发一次 (防止接口被刷 → 邮箱轰炸别人 + 耗光发信额度)
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(LIMIT_PREFIX + email, "1", RESEND_LIMIT_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(first)) {
            throw new BusinessException(429, "发送太频繁, 请 60 秒后再试");
        }

        // ② 生成 [100000, 999999] 的 6 位码, 存 Redis 5 分钟
        //    重发会直接覆盖旧码 → 天然"旧码作废", 顺手把错误计数也清零
        String code = String.valueOf(RANDOM.nextInt(900_000) + 100_000);
        stringRedisTemplate.opsForValue()
                .set(CODE_PREFIX + email, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
        stringRedisTemplate.delete(ERR_PREFIX + email);

        // ③ 发邮件 (纯文本用 SimpleMailMessage 就够, 要 HTML 排版才用 MimeMessage)
        //    ⚠️ SMTP 是同步网络调用, 1~2 秒很正常; 量大了可改 @Async 丢线程池
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("【mini-mall】登录验证码");
        message.setText("你的登录验证码是: " + code + " , " + CODE_TTL_MINUTES
                + " 分钟内有效。如非本人操作请忽略本邮件。");
        try {
            mailSender.send(message);
        } catch (Exception e) {
            // 发送失败要把 60 秒限制回滚, 否则用户白等 1 分钟才能重试
            stringRedisTemplate.delete(LIMIT_PREFIX + email);
            log.error("[邮箱验证码] 发送失败 email={}", email, e);
            throw new BusinessException("验证码发送失败, 请稍后重试");
        }

        log.info("[邮箱验证码] 已发送 email={}", email);
        return Result.success();
    }

    // ═══════════════════════════════════════════════════════════
    // ② 验证码登录 (未注册自动注册)
    // ═══════════════════════════════════════════════════════════

    /**
     * 流程:
     *   ① 取 Redis 里的码 (没有 = 没发过/已过期)
     *   ② 防爆破: 连错 5 次直接作废这个码
     *   ③ 比对成功 → 立刻删码 (一次性! 防止 5 分钟内重放登录)
     *   ④ Feign 按邮箱查用户, 没有就自动建号 (复用 OAuth 首登套路)
     *   ⑤ 禁用拦截 → 签 JWT → 返 AuthResponse (跟密码登录/OAuth 返回结构完全一致)
     */
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody EmailLoginDTO dto) {
        String email = dto.getEmail();
        String codeKey = CODE_PREFIX + email;

        // ① 取码
        String realCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (realCode == null) {
            throw new BusinessException("验证码已过期或未发送, 请重新获取");
        }

        // ② 验码不对: 错误计数 +1, 到 5 次把码删掉逼用户重新发
        //    (不然 6 位数字 100 万种组合, 5 分钟窗口内脚本能跑完)
        if (!realCode.equals(dto.getCode())) {
            Long errCount = stringRedisTemplate.opsForValue().increment(ERR_PREFIX + email);
            stringRedisTemplate.expire(ERR_PREFIX + email, CODE_TTL_MINUTES, TimeUnit.MINUTES);
            if (errCount != null && errCount >= MAX_ERR_COUNT) {
                stringRedisTemplate.delete(codeKey);
                throw new BusinessException("错误次数过多, 验证码已失效, 请重新获取");
            }
            throw new BusinessException("验证码错误");
        }

        // ③ 验证通过 → 码立刻作废 (一次性), 错误计数一并清掉
        stringRedisTemplate.delete(codeKey);
        stringRedisTemplate.delete(ERR_PREFIX + email);

        // ④ 按邮箱查用户
        Result<User> findResp = userFeignClient.getByEmail(email);
        if (findResp.getCode() != 200) {
            throw new BusinessException(findResp.getMessage());   // user 服务挂了 → 503 透传
        }
        User user = findResp.getData();

        // ④.5 查不到 → 自动注册 (跟 OAuth 首次登录一模一样的套路)
        if (user == null) {
            User newUser = new User();
            newUser.setEmail(email);
            // username 不能跟本地注册的撞车, 加 mail_ 前缀 + 4 位随机数
            // (password 留 null: 邮箱用户没有密码, 走不了密码登录, 跟 OAuth 用户一样)
            String prefix = email.substring(0, email.indexOf('@'));
            newUser.setUsername("mail_" + prefix + "_" + (RANDOM.nextInt(9000) + 1000));
            newUser.setNickname(prefix);
            newUser.setRole((byte) 0);      // 普通用户
            newUser.setStatus((byte) 1);    // 正常状态
            newUser.setCreateTime(LocalDateTime.now());
            newUser.setUpdateTime(LocalDateTime.now());

            Result<User> createResp = userFeignClient.createUser(newUser);
            if (createResp.getCode() != 200 || createResp.getData() == null) {
                throw new BusinessException("自动注册失败: " + createResp.getMessage());
            }
            user = createResp.getData();    // 含 DB 回填的 id
            log.info("[邮箱登录] 首次登录自动注册 email={} userId={}", email, user.getId());
        }

        // ⑤ 禁用账号拦截 (放在验码之后, 理由同密码登录: 不给探测"哪些账号被禁"的机会)
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用, 请联系管理员");
        }

        // ⑥ 签自家 JWT, 返回结构跟 /auth/login 完全一致, 前端不用区分登录方式
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        user.setPassword(null);   // 兜底: 密文绝不出网关
        return Result.success(new AuthResponse(token, user));
    }
}
