package com.tongluxing.merchant.vo;

import java.time.LocalDateTime;

/**
 * 商家推广码返回对象。
 */
public record MerchantPromotionCodeVO(
        Long promotionId,
        Long merchantId,
        String promotionCode,
        String channelName,
        String scene,
        String qrImageKey,
        String status,
        String remark,
        LocalDateTime createdAt
) {
}
