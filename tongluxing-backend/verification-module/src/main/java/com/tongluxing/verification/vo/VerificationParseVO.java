package com.tongluxing.verification.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * VerificationParseVO 视图响应对象。
 */

public record VerificationParseVO(
        Long verificationCodeId,
        String bizType,
        Long bizId,
        Long userId,
        Long merchantId,
        BigDecimal amount,
        String codeStatus,
        LocalDateTime expireAt,
        Boolean canVerify,
        String blockReason
) {
}
