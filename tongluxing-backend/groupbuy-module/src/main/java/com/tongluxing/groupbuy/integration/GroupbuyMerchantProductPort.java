package com.tongluxing.groupbuy.integration;

import java.math.BigDecimal;

/**
 * 拼团模块读取商家商品快照的端口。
 *
 * <p>端口放在 groupbuy-module 内，实际适配在启动应用中完成，避免团购模块直接依赖商家模块实现。</p>
 */
public interface GroupbuyMerchantProductPort {

    /**
     * 获取商品当前用于开团的价格和规则快照。
     *
     * @param productId 商品 ID
     * @return 商品快照
     */
    MerchantProductSnapshot getSnapshot(Long productId);

    /**
     * 扣减商品库存。
     *
     * @param productId 商品 ID
     * @param quantity 扣减数量
     * @param requestId 幂等请求号
     */
    void decreaseStock(Long productId, Integer quantity, String requestId);

    /**
     * 商家商品的开团快照。
     *
     * @param productId 商品 ID
     * @param merchantId 商家 ID
     * @param productName 商品名称
     * @param productType 商品类型
     * @param originalPrice 原价
     * @param groupPrice 拼团价格
     * @param ladderPriceJson 阶梯价格规则 JSON
     * @param targetPeople 默认成团人数
     * @param stock 当前库存
     * @param validHours 默认活动有效小时数
     */
    record MerchantProductSnapshot(Long productId, Long merchantId, String merchantName, String productName, String productType,
                                   BigDecimal originalPrice, BigDecimal groupPrice, String ladderPriceJson,
                                   Integer targetPeople, Integer stock, Integer validHours,
                                   Long storeId, String storeName, String storeAddress, boolean couponOffer, boolean available) {
    }
}
