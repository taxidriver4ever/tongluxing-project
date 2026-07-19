package com.tongluxing.merchant.vo;

import java.math.BigDecimal;

public record MerchantStoreVO(Long storeId, Long merchantId, String storeName, String address,
                              BigDecimal longitude, BigDecimal latitude, String contactPhoneMask,
                              String businessHours, String parkingInfo, String storeStatus) {}
