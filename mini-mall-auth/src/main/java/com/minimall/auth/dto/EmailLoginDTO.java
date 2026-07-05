package com.minimall.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * EMAIL-1: 邮箱验证码登录的请求体
 */
@Data
public class EmailLoginDTO {

    /** 邮箱 (要跟发码时的邮箱一致, Redis 里的验证码就是按邮箱存的) */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 6 位数字验证码; 格式不对直接 400, 不用去 Redis 比对浪费一次查询 */
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "\\d{6}", message = "验证码为 6 位数字")
    private String code;
}
