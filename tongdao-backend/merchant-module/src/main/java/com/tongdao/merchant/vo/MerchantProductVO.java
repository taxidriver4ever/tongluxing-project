package com.tongdao.merchant.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商家拼团商品返回对象。
 */
public record MerchantProductVO(
        Long productId,
        Long merchantId,
        String productName,
        String productType,
        BigDecimal originalPrice,
        BigDecimal groupPrice,
        String ladderPriceJson,
        Integer targetPeople,
        Integer stock,
        Integer validHours,
        BigDecimal minSettlementPrice,
        List<String> imageKeys,
        String description,
        String productStatus,
        LocalDateTime updatedAt
) {
}
