package com.tongdao.payment.integration;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface PaymentOrderPort {

    PaymentOrderDTO getOrder(Long orderId);

    PaymentOrderDTO markPaid(Long orderId, LocalDateTime paidAt);

    PaymentOrderDTO markRefunding(Long orderId);

    PaymentOrderDTO markRefunded(Long orderId);

    PaymentOrderDTO markVerified(Long orderId);

    PaymentOrderDTO markCompleted(Long orderId);

    record PaymentOrderDTO(
            Long orderId,
            String orderNo,
            Long userId,
            Long merchantId,
            Long activityId,
            String paymentStatus,
            String verificationStatus,
            String profitSharingStatus,
            BigDecimal payableAmount,
            BigDecimal paidAmount
    ) {
    }
}
