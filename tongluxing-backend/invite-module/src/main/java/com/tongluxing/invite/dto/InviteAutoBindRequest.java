package com.tongluxing.invite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 分享链接/二维码自动绑定请求。
 *
 * @param inviteCode 分享链接/二维码中携带的系统邀请参数，不是用户手动填写的邀请码
 * @param sourceType 来源类型：LINK 表示分享链接，QR_CODE 表示邀请二维码
 * @param sourceScene 分享场景，用于统计和排查，可为空
 */
public record InviteAutoBindRequest(
        @NotBlank(message = "邀请码不能为空")
        String inviteCode,

        @NotBlank(message = "来源类型不能为空")
        @Pattern(regexp = "LINK|QR_CODE", message = "来源类型只能是 LINK 或 QR_CODE")
        String sourceType,

        String sourceScene
) {
}
