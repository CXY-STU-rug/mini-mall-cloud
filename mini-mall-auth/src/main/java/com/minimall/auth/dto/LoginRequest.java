package com.minimall.auth.dto;

import com.minimall.auth.enums.LoginType;

/**
 * 统一登录入参。
 *
 * 承载所有登录方式需要的字段, 由具体策略各取所需:
 *   PASSWORD   读 username / password
 *   EMAIL_CODE 读 email / code
 * 新增登录方式时在此补充对应字段即可。
 */
public class LoginRequest {

    /** 登录方式 (决定用哪个策略) */
    private LoginType loginType;

    /** 密码登录: 用户名 */
    private String username;

    /** 密码登录: 密码明文 */
    private String password;

    /** 邮箱验证码登录: 邮箱 */
    private String email;

    /** 邮箱验证码登录: 验证码 */
    private String code;

    public LoginRequest() {
    }

    public LoginType getLoginType() {
        return loginType;
    }

    public void setLoginType(LoginType loginType) {
        this.loginType = loginType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
