package com.tongluxing.merchant.vo;

import java.math.BigDecimal;

/**
 * 商家门店对外返回视图。
 * 该模型只承载客户端需要的数据，避免直接暴露数据库实体及内部实现字段。
 */
public record MerchantStoreVO(Long storeId, Long merchantId, String storeName, String address,
                              BigDecimal longitude, BigDecimal latitude, String contactPhoneMask,
                              String businessHours, String parkingInfo, String storeStatus) {}
