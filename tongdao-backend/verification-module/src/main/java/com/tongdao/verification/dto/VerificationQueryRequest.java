package com.tongdao.verification.dto;

import java.time.LocalDateTime;
/**
 * VerificationQueryRequest 请求对象。
 */

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
