package com.tongluxing.merchant.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
