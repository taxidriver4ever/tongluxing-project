package com.tongdao.payment.integration;

import java.time.LocalDateTime;

public interface PaymentGroupbuyPort {

    void addPaidParticipant(Long activityId, Long orderId, Long userId, LocalDateTime paidAt, String requestId);
}
