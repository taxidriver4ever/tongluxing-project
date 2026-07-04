package com.tongluxing.verification.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * VerificationRecordVO 视图响应对象。
 */

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
