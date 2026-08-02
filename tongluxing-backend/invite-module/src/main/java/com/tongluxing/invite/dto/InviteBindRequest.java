package com.tongluxing.invite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 当前登录用户绑定邀请码请求。
 *
 * <p>邀请码格式由服务层在 trim/转大写后统一校验；
 * requestId 用于网络超时重试时返回同一绑定结果。</p>
 */
public record InviteBindRequest(
        /** 用户输入或二维码解析出的邀请码。 */
        @NotBlank(message = "请输入邀请码")
        String inviteCode,
        /** 绑定来源：手动输入、扫码或分享链接。 */
        @Pattern(regexp = "^(MANUAL_CODE|QR_CODE|SHARE_LINK)$", message = "邀请来源不正确")
        String sourceType,
        /** 客户端生成的绑定请求幂等号；旧客户端可不传。 */
        @Size(max = 64, message = "requestId长度不能超过64")
        String requestId
) {
}
