package com.fishmall.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "当前密码不能为空")
        @Size(min = 8, max = 64, message = "当前密码长度必须为8到64个字符")
        String currentPassword,
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 64, message = "新密码长度必须为8到64个字符")
        String newPassword,
        @NotBlank(message = "确认密码不能为空")
        @Size(min = 8, max = 64, message = "确认密码长度必须为8到64个字符")
        String confirmNewPassword
) {
}
