package com.tongdao.chat.vo;

/**
 * 腾讯云 IM 登录票据响应。
 */
public record ImUserSigResponse(
        /** 腾讯云 IM SDKAppID。 */
        Long sdkAppId,
        /** 腾讯云 IM 用户 ID。 */
        String userId,
        /** IM 登录签名。 */
        String userSig,
        /** 过期时间戳，单位秒。 */
        Long expireTime
) {
}
