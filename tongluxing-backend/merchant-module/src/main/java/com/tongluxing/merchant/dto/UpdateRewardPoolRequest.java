package com.tongluxing.merchant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 奖励合作商家池配置请求。
 */
public record UpdateRewardPoolRequest(
        @NotNull Boolean enabled,
        @Min(0) Integer monthlyStock,
        @Size(max = 32) String couponType
) {
}
