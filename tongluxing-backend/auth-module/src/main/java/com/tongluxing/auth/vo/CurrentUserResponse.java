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
        String loginStatus,
        /** 当前账号是否已经设置密码。 */
        Boolean passwordSet,
        /** 小程序邀请码引导是否已经完成。 */
        Boolean miniInviteOnboardingCompleted
) {
}
