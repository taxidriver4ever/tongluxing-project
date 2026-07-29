package com.tongluxing.merchant.vo;

/**
 * 商家中心概览对外返回视图。
 * 该模型只承载客户端需要的数据，避免直接暴露数据库实体及内部实现字段。
 */
public record MerchantCenterOverviewVO(Long merchantId, String merchantName, String merchantLevel, String partnerStatus,
                                       long storeCount, long couponCount, long pendingCouponCount,
                                       long onlineCouponCount, long totalStock, long soldCount) {}
