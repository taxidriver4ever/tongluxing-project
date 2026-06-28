package com.tongdao.payment.integration;

import java.time.LocalDateTime;

/**
 * 支付模块访问拼团模块的跨模块端口。
 */
public interface PaymentGroupbuyPort {

    /**
     * 支付成功后同步拼团参与人支付状态。
     */
    void addPaidParticipant(Long activityId, Long orderId, Long userId, LocalDateTime paidAt, String requestId);
}
