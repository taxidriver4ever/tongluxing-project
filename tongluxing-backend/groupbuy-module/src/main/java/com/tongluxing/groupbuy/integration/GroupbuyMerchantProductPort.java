package com.tongluxing.groupbuy.integration;

import java.math.BigDecimal;

/**
 * 拼团模块读取商家商品快照的端口。
 *
 * <p>端口放在 groupbuy-module 内，实际适配在启动应用中完成，避免模块间直接循环依赖。</p>
 */
public interface GroupbuyMerchantProductPort {

    MerchantProductSnapshot getSnapshot(Long productId);

    void decreaseStock(Long productId, Integer quantity, String requestId);

    record MerchantProductSnapshot(Long productId, Long merchantId, String productName, String productType,
                                   BigDecimal originalPrice, BigDecimal groupPrice, String ladderPriceJson,
                                   Integer targetPeople, Integer stock, Integer validHours) {
    }
}
