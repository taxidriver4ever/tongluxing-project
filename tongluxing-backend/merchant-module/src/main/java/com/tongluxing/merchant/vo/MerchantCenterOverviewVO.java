package com.tongluxing.merchant.vo;

public record MerchantCenterOverviewVO(Long merchantId, String merchantName, String merchantLevel,
                                       long storeCount, long couponCount, long pendingCouponCount,
                                       long onlineCouponCount, long totalStock, long soldCount) {}
