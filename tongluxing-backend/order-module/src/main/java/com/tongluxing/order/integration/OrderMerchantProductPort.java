package com.tongluxing.order.integration;

import java.math.BigDecimal;

/**
 * 订单模块读取商品快照的端口。
 */
public interface OrderMerchantProductPort {

    MerchantProductSnapshot getSnapshot(Long productId);

    record MerchantProductSnapshot(Long productId, Long merchantId, String productName, String productType,
                                   BigDecimal originalPrice, BigDecimal groupPrice, String snapshotJson) {
    }
}
