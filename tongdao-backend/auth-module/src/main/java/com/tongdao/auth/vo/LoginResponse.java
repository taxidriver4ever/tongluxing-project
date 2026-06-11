package com.tongdao.auth.vo;

public record LoginResponse(
        String token,
        String refreshToken,
        Long userId,
        Boolean isNewUser,
        Integer expireSeconds
) {
}
