package com.tongluxing.verification.vo;

import java.time.LocalDateTime;
/**
 * VerificationReversalVO 视图响应对象。
 */

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
