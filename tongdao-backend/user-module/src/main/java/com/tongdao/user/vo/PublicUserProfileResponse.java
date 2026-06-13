package com.tongdao.user.vo;

public record PublicUserProfileResponse(
        Long userId,
        String nickname,
        String avatarUrl,
        String cityName,
        String realNameStatus,
        Boolean vehicleCertified
) {
}
