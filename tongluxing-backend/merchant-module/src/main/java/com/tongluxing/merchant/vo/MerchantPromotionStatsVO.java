package com.tongluxing.merchant.vo;

import java.math.BigDecimal;

/**
 * 商家推广活动聚合数据返回对象。
 *
 * <p>仅返回聚合指标，不返回用户手机号、用户 ID 等隐私明细。</p>
 */
public record MerchantPromotionStatsVO(
        Long promotionId,
        Long registerCount,
        Long couponClaimCount,
        Long couponVerifyCount,
        Long orderCount,
        BigDecimal tradeAmount
) {
}
