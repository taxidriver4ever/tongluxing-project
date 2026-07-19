package com.tongluxing.groupbuy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建拼团活动请求。
 *
 * @param productId 商品 ID
 * @param targetPeople 成团人数，必须大于 0
 * @param validHours 活动有效小时数，必须大于 0
 * @param requestId 客户端生成的幂等请求号
 */
public record CreateGroupbuyRequest(
        Long productId,
        Long couponId,
        @Min(2) Integer targetPeople,
        @Min(1) Integer validHours,
        @NotBlank String requestId
) {
}

