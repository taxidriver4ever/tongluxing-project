package com.tongluxing.user.dto.request;

import java.time.LocalDate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 修改用户资料请求。
 *
 * <p>字段均为可选，未传入表示保留原值。</p>
 *
 * @param nickname 昵称，最多 16 个字符
 * @param avatarImageKey 头像对象存储 Key
 * @param gender 性别编码：0 未知、1 男、2 女
 * @param birthday 生日
 * @param cityCode 标准城市编码
 * @param cityName 用于展示的城市名称
 * @param bio 个人简介
 */
public record UpdateUserProfileRequest(
        @Size(max = 16) String nickname,
        @Size(max = 512) String avatarImageKey,
        @Min(0) @Max(2) Integer gender,
        LocalDate birthday,
        @Size(max = 12) String cityCode,
        @Size(max = 32) String cityName,
        @Size(max = 100) String bio
) {
}

