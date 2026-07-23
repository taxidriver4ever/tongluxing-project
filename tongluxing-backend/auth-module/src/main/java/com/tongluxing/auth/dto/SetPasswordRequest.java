package com.tongluxing.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 当前登录用户首次设置密码请求。 */
public record SetPasswordRequest(
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 32, message = "密码长度需为8-32位")
        String password
) {
}
