package com.tongluxing.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 邀请码绑定请求。
 *
 * @param inviteCode 待绑定的邀请码，去除首尾空白后最长 16 个字符
 */
public record InviteBindRequest(@NotBlank @Size(max = 16) String inviteCode) {
}

