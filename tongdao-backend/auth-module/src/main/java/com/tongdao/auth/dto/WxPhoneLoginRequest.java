package com.tongdao.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record WxPhoneLoginRequest(
        @NotBlank(message = "微信手机号授权 code 不能为空")
        String code,

        String deviceId,

        String inviteCode
) {
}
