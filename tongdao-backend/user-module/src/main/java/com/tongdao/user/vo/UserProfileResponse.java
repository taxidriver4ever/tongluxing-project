package com.tongdao.user.vo;

import java.time.LocalDate;

public record UserProfileResponse(
        Long userId,
        String nickname,
        String avatarUrl,
        Integer gender,
        LocalDate birthday,
        String cityCode,
        String cityName,
        String bio,
        Integer profileCompletion,
        String realNameStatus
) {
}
