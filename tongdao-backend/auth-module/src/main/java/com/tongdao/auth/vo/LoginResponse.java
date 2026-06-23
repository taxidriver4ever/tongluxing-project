package com.tongdao.auth.vo;

/**
 * 登录成功响应。
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
        /** access token 有效期，单位秒。 */
        Integer expireSeconds
) {
}
