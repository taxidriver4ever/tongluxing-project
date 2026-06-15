package com.tongdao.user.vo;

public record PublicUserProfileResponse(
        Long userId,
        String nickname,
        String avatarImageKey,
        String cityName,
        String realNameStatus,
        Boolean vehicleCertified
) {
}
