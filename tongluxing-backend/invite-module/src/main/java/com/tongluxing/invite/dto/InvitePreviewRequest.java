package com.tongluxing.invite.dto;

import jakarta.validation.constraints.NotBlank;

/** 绑定前预览邀请人请求。邀请码格式由服务层在 trim/转大写后统一校验。 */
public record InvitePreviewRequest(
        /** 待预览的邀请码，服务端会统一转大写并校验。 */
        @NotBlank(message = "请输入邀请码")
        String inviteCode
) {
}
