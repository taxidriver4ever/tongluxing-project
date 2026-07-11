package com.tongluxing.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建商家推广码请求。
 *
 * <p>推广码只做商家注册来源归因，最终写入 merchant_user_relation；用户邀请关系仍以 invite-module 记录为准。</p>
 */
public record CreatePromotionCodeRequest(
        @NotBlank @Size(max = 64) String channelName,
        @NotBlank @Size(max = 32) String scene,
        @Size(max = 255) String remark
) {
}
