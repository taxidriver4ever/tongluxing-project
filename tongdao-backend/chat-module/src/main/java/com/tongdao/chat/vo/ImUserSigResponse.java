package com.tongdao.chat.vo;

public record ImUserSigResponse(
        Long sdkAppId,
        String userId,
        String userSig,
        Long expireTime
) {
}
