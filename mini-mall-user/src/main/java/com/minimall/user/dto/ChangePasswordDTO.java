package com.minimall.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码入参 DTO (PUT /user/me/password)
 *
 * 两个字段都必填:
 *   oldPassword — 用来 BCrypt 比对当前密码, 防止他人趁会话未过期改密
 *   newPassword — BCrypt 加密后写入数据库
 */
@Data
public class ChangePasswordDTO {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度 6-20 位")
    private String newPassword;
}
