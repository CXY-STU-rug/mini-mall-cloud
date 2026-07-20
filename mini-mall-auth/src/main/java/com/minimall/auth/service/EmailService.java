package com.minimall.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 邮件异步发送服务。
 *
 * 单独抽成一个 Bean 的原因: @Async 依赖 Spring AOP 代理, 只在跨 Bean 调用时生效。
 * 若把发送逻辑留在 Controller 内自调用 (this.xxx()), 不走代理, @Async 失效仍会同步执行。
 * 因此由 Controller 注入本服务并调用, 邮件才会真正被丢进 mailExecutor 线程池异步执行。
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final StringRedisTemplate stringRedisTemplate;

    /** QQ 邮箱要求信封发件人(from)必须等于登录 SMTP 的账号 */
    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender, StringRedisTemplate stringRedisTemplate) {
        this.mailSender = mailSender;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 异步发送登录验证码邮件。
     *
     * 指定线程池 mailExecutor (见 AsyncConfig), 主调用线程提交任务后立即返回, 不阻塞 HTTP 请求。
     *
     * 注意: @Async 修饰的 void 方法, 内部抛出的异常会被线程池吞掉、无法传回主线程。
     * 所以发送结果必须在本方法内自行处理 —— 失败时回滚 60 秒重发限制 key, 让用户可立即重试。
     *
     * @param toEmail    收件邮箱
     * @param code       验证码
     * @param ttlMinutes 验证码有效期(分钟), 仅用于邮件正文提示
     * @param limitKey   60 秒重发限制的 Redis key, 发送失败时删除以解除限制
     */
    @Async("mailExecutor")
    public void sendCodeMail(String toEmail, String code, long ttlMinutes, String limitKey) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("【mini-mall】登录验证码");
        message.setText("你的登录验证码是: " + code + " , " + ttlMinutes
                + " 分钟内有效。如非本人操作请忽略本邮件。");
        try {
            mailSender.send(message);
            log.info("[邮箱验证码] 已发送 email={}", toEmail);
        } catch (Exception e) {
            // 异步线程内自行补偿: 回滚重发限制, 否则用户要白等满 60 秒才能重试
            stringRedisTemplate.delete(limitKey);
            log.error("[邮箱验证码] 发送失败(已回滚重发限制) email={}", toEmail, e);
        }
    }
}
