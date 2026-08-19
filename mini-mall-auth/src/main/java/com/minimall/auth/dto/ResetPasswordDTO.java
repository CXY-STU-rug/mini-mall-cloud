package com.minimall.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 找回密码入参 DTO (POST /auth/reset-password)
 *
 * 流程: 邮箱 → 发验证码 → 填码 + 新密码 → 重置
 * email + code 必须与 Redis 里的 CODE_PREFIX:{email} 匹配, 否则 400
 */
@Data
public class ResetPasswordDTO {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度 6-20 位")
    private String newPassword;
}
