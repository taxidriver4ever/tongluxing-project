package com.tongdao.payment.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * ProfitSharingVO 视图响应对象。
 */

public record ProfitSharingVO(
        Long sharingId,
        Long orderId,
        Long merchantId,
        String sharingNo,
        BigDecimal totalAmount,
        BigDecimal platformCommissionAmount,
        BigDecimal merchantAmount,
        BigDecimal commissionRate,
        String sharingStatus,
        LocalDateTime sharedAt
) {
}

