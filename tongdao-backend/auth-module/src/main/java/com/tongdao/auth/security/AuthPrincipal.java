package com.tongdao.auth.security;

public record AuthPrincipal(
        Long userId,
        String phone,
        String token,
        String deviceId
) {
}
