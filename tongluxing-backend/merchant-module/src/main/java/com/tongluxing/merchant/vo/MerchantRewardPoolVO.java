package com.tongluxing.merchant.vo;

import java.math.BigDecimal;

/**
 * 奖励合作商家池配置返回对象。
 */
public record MerchantRewardPoolVO(
        Long merchantId,
        Boolean enabled,
        String couponType,
        Integer monthlyStock,
        Integer usedStock,
        BigDecimal exposureWeightBonus
) {
}
