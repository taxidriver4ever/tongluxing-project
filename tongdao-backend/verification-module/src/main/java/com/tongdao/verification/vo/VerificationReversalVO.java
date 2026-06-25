package com.tongdao.verification.vo;

import java.time.LocalDateTime;

public record VerificationReversalVO(
        Long reversalId,
        Long verificationId,
        Long merchantId,
        Long applicantId,
        String reason,
        String auditStatus,
        Long reviewerId,
        LocalDateTime reviewedAt,
        String rejectReason,
        LocalDateTime createdAt
) {
}
