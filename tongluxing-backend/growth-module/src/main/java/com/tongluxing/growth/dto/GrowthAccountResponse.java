package com.tongluxing.growth.dto;

/** 邀请增长联调使用的成长账户兼容返回。 */
public record GrowthAccountResponse(
        Long userId,
        Integer experience,
        String levelCode
) {
}
