package com.tongluxing.merchant.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家优惠券权益对外返回视图。
 * 该模型只承载客户端需要的数据，避免直接暴露数据库实体及内部实现字段。
 */
public record MerchantCouponOfferVO(
        Long couponId, Long merchantId, String merchantName, Long storeId, String storeName,
        String storeAddress, BigDecimal longitude, BigDecimal latitude, String couponName,
        String coverImageKey, String description, String category, BigDecimal originalPrice,
        BigDecimal salePrice, Integer stock, Integer soldCount, Integer limitCount,
        boolean groupEnabled, Integer groupPeople, Integer groupTimeoutHours,
        LocalDateTime publishTime, LocalDateTime expireTime, LocalDateTime useStartTime,
        LocalDateTime useEndTime, boolean reservationRequired, boolean refundable,
        boolean holidayAvailable, boolean stackable, String useInstructions,
        String auditStatus, String rejectReason, String offerStatus
) {}
