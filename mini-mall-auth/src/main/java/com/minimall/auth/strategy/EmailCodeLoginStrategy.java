package com.minimall.auth.strategy;

import com.minimall.auth.client.UserFeignClient;
import com.minimall.auth.constant.EmailAuthConstants;
import com.minimall.auth.dto.LoginRequest;
import com.minimall.auth.enums.LoginType;
import com.minimall.auth.model.User;
import com.minimall.common.core.domain.Result;
import com.minimall.common.core.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码登录策略。
 *
 * 逻辑来自原 EmailAuthController.login: 校验 Redis 验证码(含防爆破) → 按邮箱查用户,
 * 查不到则自动注册。发送验证码仍由 EmailAuthController 的 /code 端点负责。
 */
@Component
public class EmailCodeLoginStrategy implements LoginStrategy {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeLoginStrategy.class);

    /** SecureRandom 而不是 Random: 生成随机用户名后缀, 安全场景不用可预测的 Random */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate stringRedisTemplate;
    private final UserFeignClient userFeignClient;

    public EmailCodeLoginStrategy(StringRedisTemplate stringRedisTemplate,
                                  UserFeignClient userFeignClient) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userFeignClient = userFeignClient;
    }

    @Override
    public LoginType getType() {
        return LoginType.EMAIL_CODE;
    }

    @Override
    public User authenticate(LoginRequest request) {
        String email = request.getEmail();
        String codeKey = EmailAuthConstants.CODE_PREFIX + email;

        // ① 取码 (没有 = 没发过/已过期)
        String realCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (realCode == null) {
            throw new BusinessException("验证码已过期或未发送, 请重新获取");
        }

        // ② 验码不对: 错误计数 +1, 到上限把码删掉逼用户重发 (防 6 位数字被脚本爆破)
        if (!realCode.equals(request.getCode())) {
            Long errCount = stringRedisTemplate.opsForValue()
                    .increment(EmailAuthConstants.ERR_PREFIX + email);
            stringRedisTemplate.expire(EmailAuthConstants.ERR_PREFIX + email,
                    EmailAuthConstants.CODE_TTL_MINUTES, TimeUnit.MINUTES);
            if (errCount != null && errCount >= EmailAuthConstants.MAX_ERR_COUNT) {
                stringRedisTemplate.delete(codeKey);
                throw new BusinessException("错误次数过多, 验证码已失效, 请重新获取");
            }
            throw new BusinessException("验证码错误");
        }

        // ③ 验证通过 → 码立刻作废 (一次性), 错误计数一并清掉
        stringRedisTemplate.delete(codeKey);
        stringRedisTemplate.delete(EmailAuthConstants.ERR_PREFIX + email);

        // ④ 按邮箱查用户
        Result<User> findResp = userFeignClient.getByEmail(email);
        if (findResp.getCode() != 200) {
            throw new BusinessException(findResp.getMessage());   // user 服务挂了 → 503 透传
        }
        User user = findResp.getData();

        // ④.5 查不到 → 自动注册 (跟 OAuth 首次登录一样的套路)
        if (user == null) {
            User newUser = new User();
            newUser.setEmail(email);
            // username 不能跟本地注册的撞车, 加 mail_ 前缀 + 4 位随机数
            // (password 留 null: 邮箱用户没有密码, 走不了密码登录)
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
        return user;
    }
}
