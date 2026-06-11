package com.tongdao.auth.vo;

public record RefreshTokenResponse(
        String token,
        Integer expireSeconds
) {
}
