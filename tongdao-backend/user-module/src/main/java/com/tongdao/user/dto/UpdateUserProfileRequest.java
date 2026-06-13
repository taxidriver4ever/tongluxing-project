package com.tongdao.user.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @Size(max = 32, message = "昵称不能超过32个字符")
        String nickname,

        @Size(max = 512, message = "头像地址不能超过512个字符")
        String avatarUrl,

        @Min(value = 0, message = "性别取值不正确")
        @Max(value = 2, message = "性别取值不正确")
        Integer gender,

        LocalDate birthday,

        @Size(max = 32, message = "城市编码不能超过32个字符")
        String cityCode,

        @Size(max = 64, message = "城市名称不能超过64个字符")
        String cityName,

        @Size(max = 160, message = "个人简介不能超过160个字符")
        String bio
) {
}
