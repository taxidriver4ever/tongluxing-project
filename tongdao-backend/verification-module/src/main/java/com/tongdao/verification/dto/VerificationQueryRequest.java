package com.tongdao.verification.dto;

import java.time.LocalDateTime;

public record VerificationQueryRequest(
        Long merchantId,
        String bizType,
        String verificationStatus,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int page,
        int size
) {
}
