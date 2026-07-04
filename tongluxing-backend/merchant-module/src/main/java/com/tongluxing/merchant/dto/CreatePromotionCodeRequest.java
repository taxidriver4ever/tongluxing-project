package com.tongluxing.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建商家推广码请求。
 *
 * <p>推广码只做商家渠道归因，用户邀请关系以 invite-module 记录为准。</p>
 */
public record CreatePromotionCodeRequest(
        @NotBlank @Size(max = 64) String channelName,
        @NotBlank @Size(max = 32) String scene,
        @Size(max = 255) String remark
) {
}
