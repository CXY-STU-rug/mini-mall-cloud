package com.minimall.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * EMAIL-1: 发送邮箱验证码的请求体
 *
 * 只有一个字段也单独建 DTO, 不用 @RequestParam:
 *   ① 跟 login/register 统一都是 JSON body
 *   ② @Email 校验直接挂在字段上, 进 Controller 前就拦掉格式错误
 */
@Data
public class SendEmailCodeDTO {

    /** 收验证码的邮箱; @Email 校验格式 (xx@xx.xx), 格式错直接 400 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}
