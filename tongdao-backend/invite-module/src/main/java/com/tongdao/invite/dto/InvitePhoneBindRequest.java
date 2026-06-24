package com.tongdao.invite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 7 天内弱兜底手机号绑定请求。
 *
 * @param inviterPhone 邀请人手机号
 */
public record InvitePhoneBindRequest(
        @NotBlank(message = "邀请人手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "邀请人手机号格式不正确")
        String inviterPhone
) {
}
