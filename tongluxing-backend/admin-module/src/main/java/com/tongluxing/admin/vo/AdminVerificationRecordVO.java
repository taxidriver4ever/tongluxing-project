package com.tongluxing.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 运营后台核销记录读模型。 */
public record AdminVerificationRecordVO(
        Long verificationId, String verificationCode, String bizType, Long bizId,
        Long orderId, Long userId, String userName, Long merchantId, String merchantName,
        BigDecimal amount, String verificationStatus, String locationName,
        LocalDateTime verifiedAt, String reversalStatus) {}
