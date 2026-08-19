package com.minimall.auth.security;

import com.minimall.auth.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 自定义 UserDetails：把项目里的 {@link User} 包一层，交给 Spring Security 使用。
 *
 * 为什么要自定义，而不用 Spring 自带的 org.springframework.security.core.userdetails.User：
 *   自带那个只有 username / password / authorities 三样，
 *   拿不到我们签 JWT 需要的 userId 和 role。所以自己包一层，把 domain User 一起带进来。
 *
 * 谁会用到它：
 *   ① FeignUserDetailsService.loadUserByUsername() 查到用户后 new 一个返回；
 *   ② DaoAuthenticationProvider 用 getPassword() 拿密文去比对；
 *   ③ 认证成功后 AuthController 从 principal 强转成 LoginUser，取 getUser() 去签 JWT。
 */
public class LoginUser implements UserDetails {

    /** 底层真正的业务用户对象（含 id / role / status / 密文） */
    private final User user;

    public LoginUser(User user) {
        this.user = user;
    }

    /** 认证成功后 Controller 用它拿 id/role 去签 JWT */
    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 把 role（0 普通用户 / 1 管理员）翻译成 Spring Security 的权限字符串。
        // ⚠️ 约定：@PreAuthorize("hasRole('ADMIN')") 实际匹配的是 "ROLE_ADMIN"，前缀 ROLE_ 不能省。
        String roleName = (user.getRole() != null && user.getRole() == 1) ? "ROLE_ADMIN" : "ROLE_USER";
        return List.of(new SimpleGrantedAuthority(roleName));
    }

    @Override
    public String getPassword() {
        // BCrypt 密文，交给 DaoAuthenticationProvider 用 PasswordEncoder 比对
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    // ── 下面 4 个是账号状态位，Spring Security 认证时会逐个检查 ──

    @Override
    public boolean isAccountNonExpired() {
        return true;   // 我们没有"账号过期"概念，恒为 true
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;   // 没有"锁定"概念
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;   // 没有"密码过期"概念
    }

    @Override
    public boolean isEnabled() {
        // status==0 表示被管理员禁用。返回 false 时，DaoAuthenticationProvider 会抛 DisabledException，
        // 我们在 AuthController 里捕获它并返回 403 —— 保留了原来"禁用账号拦截"的行为。
        return user.getStatus() == null || user.getStatus() != 0;
    }
}
