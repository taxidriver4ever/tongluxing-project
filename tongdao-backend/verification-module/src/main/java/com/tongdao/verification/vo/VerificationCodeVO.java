package com.tongdao.verification.vo;

import java.time.LocalDateTime;

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
