package com.tongluxing.verification.vo;

import java.time.LocalDateTime;
/**
 * VerificationCodeVO 视图响应对象。
 */

public record VerificationCodeVO(
        Long verificationCodeId,
        String verificationCode,
        String qrContent,
        String bizType,
        Long bizId,
        String status,
        LocalDateTime expireAt
) {
}
