package com.tongluxing.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 当前登录用户首次设置密码请求。
 *
 * <p>该接口只允许补建第一份密码凭证，不提供覆盖式修改密码。
 * 若账号已有凭证，相同密码视为幂等成功，不同密码返回冲突，避免登录态被用来静默改密。</p>
 *
 * @param password 待设置的明文密码；服务层再次检查首尾空格和强度，最终只保存 BCrypt 哈希
 */
public record SetPasswordRequest(
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 32, message = "密码长度需为8-32位")
        String password
) {
}
