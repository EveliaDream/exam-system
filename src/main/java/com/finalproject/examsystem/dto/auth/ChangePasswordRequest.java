package com.finalproject.examsystem.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 修改密码请求。 */
public record ChangePasswordRequest(
        @NotBlank(message = "原密码不能为空")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "新密码长度需为 6~64 位")
        String newPassword) {
}
