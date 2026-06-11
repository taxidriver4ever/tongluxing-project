package com.tongdao.auth.vo;

public record CurrentUserResponse(
        Long userId,
        String phone,
        String loginStatus
) {
}
