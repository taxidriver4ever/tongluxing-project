package com.tongluxing.invite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 当前登录用户绑定邀请码请求。 */
public record InviteBindRequest(
        @NotBlank(message = "邀请码不能为空")
        @Size(max = 16, message = "邀请码长度不能超过16")
        String inviteCode
) {
}
