package com.tongluxing.auth.vo;

/**
 * 当前登录用户响应。
 *
 * <p>只返回认证域需要的最小账号状态；昵称、头像等公开资料由 user-module 提供。</p>
 *
 * @param userId 当前认证主体的业务用户 ID
 * @param phone 脱敏手机号，不可用于还原完整登录凭据
 * @param loginStatus 登录状态；当前合法会话固定为 LOGIN
 * @param passwordSet 是否已有可用密码凭证，用于决定是否提示初始化密码
 * @param miniInviteOnboardingCompleted 是否已经展示过小程序邀请码引导
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
