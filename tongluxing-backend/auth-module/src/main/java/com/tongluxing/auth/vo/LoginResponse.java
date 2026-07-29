package com.tongluxing.auth.vo;

/**
 * 登录成功响应。
 *
 * <p>客户端应安全保存 access/refresh 两个令牌，并在刷新成功时同时替换。
 * {@code isNewUser} 与引导状态用于决定首次登录页面，不应被当作权限判断依据。</p>
 *
 * @param token 短期 access token，用于 Bearer 认证
 * @param refreshToken 长期刷新令牌，仅提交给刷新接口
 * @param userId 认证账号对应的全局业务用户 ID
 * @param isNewUser 本次请求是否刚创建账号
 * @param passwordSet 账号是否存在有效密码凭证
 * @param miniInviteOnboardingCompleted 是否已完成小程序邀请码引导
 * @param expireSeconds access token 有效期，单位秒
 */
public record LoginResponse(
        /** 访问令牌，后续请求放入 Authorization: Bearer 头。 */
        String token,
        /** 刷新令牌，用于 access token 过期后换新。 */
        String refreshToken,
        /** 登录用户 ID。 */
        Long userId,
        /** 是否为本次登录自动创建的新用户。 */
        Boolean isNewUser,
        /** 当前账号是否已经设置密码。 */
        Boolean passwordSet,
        /** 小程序邀请码引导是否已经完成。 */
        Boolean miniInviteOnboardingCompleted,
        /** access token 有效期，单位秒。 */
        Integer expireSeconds
) {
}
