package com.tongluxing.order.integration;

import java.math.BigDecimal;

/**
 * 订单模块读取商品快照的端口。
 *
 * <p>端口由启动应用适配到商家商品模块，订单模块只关心下单时需要固化的商品信息。</p>
 */
public interface OrderMerchantProductPort {

    /**
     * 获取商品下单快照。
     *
     * @param productId 商品 ID
     * @return 商品快照
     */
    MerchantProductSnapshot getSnapshot(Long productId);

    /**
     * 商品下单快照。
     *
     * @param productId 商品 ID
     * @param merchantId 商家 ID
     * @param productName 商品名称
     * @param productType 商品类型
     * @param originalPrice 商品原价
     * @param groupPrice 拼团价
     * @param snapshotJson 商品完整快照 JSON
     */
    record MerchantProductSnapshot(Long productId, Long merchantId, String productName, String productType,
                                   BigDecimal originalPrice, BigDecimal groupPrice, String snapshotJson) {
    }
}
