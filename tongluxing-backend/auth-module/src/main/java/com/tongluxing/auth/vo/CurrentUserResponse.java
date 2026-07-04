package com.tongluxing.auth.vo;

/**
 * 当前登录用户响应。
 */
public record CurrentUserResponse(
        /** 当前登录用户 ID。 */
        Long userId,
        /** 脱敏后的手机号。 */
        String phone,
        /** 登录状态标识，当前固定为 LOGIN。 */
        String loginStatus
) {
}
