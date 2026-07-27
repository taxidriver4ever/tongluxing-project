package com.tongluxing.invite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 当前登录用户绑定邀请码请求。邀请码格式由服务层在 trim/转大写后统一校验。 */
public record InviteBindRequest(
        @NotBlank(message = "请输入邀请码")
        String inviteCode,
        @Pattern(regexp = "^(MANUAL_CODE|QR_CODE|SHARE_LINK)$", message = "邀请来源不正确")
        String sourceType,
        @Size(max = 64, message = "requestId长度不能超过64")
        String requestId
) {
}
