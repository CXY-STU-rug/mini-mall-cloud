package com.minimall.auth.security;

import com.minimall.auth.client.UserFeignClient;
import com.minimall.auth.model.User;
import com.minimall.common.core.domain.Result;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security 认证的"数据来源"。
 *
 * 认证流程里的分工：
 *   AuthenticationManager → DaoAuthenticationProvider → 【本类】按用户名把用户查出来
 *                                                    → 再用 PasswordEncoder 比对密码
 *
 * 我们的用户数据在 user 服务（auth 不直连 DB），所以这里走 Feign 查。
 * 相当于把原来 PasswordLoginStrategy 里"Feign 查用户"那一步，交给框架的标准扩展点来做。
 */
@Service
public class FeignUserDetailsService implements UserDetailsService {

    private final UserFeignClient userFeignClient;

    public FeignUserDetailsService(UserFeignClient userFeignClient) {
        this.userFeignClient = userFeignClient;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Feign 调 user 服务 internal 接口，按用户名查（返回带 BCrypt 密文的 User）
        Result<User> resp = userFeignClient.getByUsername(username);
        if (resp.getCode() != 200) {
            // user 服务挂了（fallback 返 503）→ 抛认证异常
            throw new UsernameNotFoundException("用户服务暂不可用");
        }

        User user = resp.getData();
        if (user == null) {
            // 查不到用户。DaoAuthenticationProvider 默认 hideUserNotFoundExceptions=true，
            // 会把这个异常悄悄转成 BadCredentialsException，跟"密码错"统一，天然防用户名枚举。
            throw new UsernameNotFoundException("用户名或密码错误");
        }

        // 包成 UserDetails 交回框架；密码比对、禁用检查都由框架接着做
        return new LoginUser(user);
    }
}
