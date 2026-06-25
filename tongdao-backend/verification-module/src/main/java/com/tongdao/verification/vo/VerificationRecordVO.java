package com.tongdao.verification.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VerificationRecordVO(
        Long verificationId,
        Long verificationCodeId,
        String verificationCode,
        String bizType,
        Long bizId,
        Long orderId,
        Long userCouponId,
        Long merchantId,
        Long userId,
        Long operatorId,
        BigDecimal amount,
        String verificationStatus,
        String reversalStatus,
        String locationName,
        BigDecimal longitude,
        BigDecimal latitude,
        LocalDateTime verifiedAt
) {
}
