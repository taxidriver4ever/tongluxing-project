package com.tongdao.payment.integration;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付模块访问订单模块的跨模块端口。
 */
public interface PaymentOrderPort {

    /**
     * 查询支付所需的订单摘要。
     */
    PaymentOrderDTO getOrder(Long orderId);

    /**
     * 支付成功后标记订单已支付。
     */
    PaymentOrderDTO markPaid(Long orderId, LocalDateTime paidAt);

    /**
     * 退款申请创建后标记订单退款中。
     */
    PaymentOrderDTO markRefunding(Long orderId);

    /**
     * 退款成功后标记订单已退款。
     */
    PaymentOrderDTO markRefunded(Long orderId);

    /**
     * 核销成功后标记订单已核销。
     */
    PaymentOrderDTO markVerified(Long orderId);

    /**
     * 分账成功后标记订单已完成。
     */
    PaymentOrderDTO markCompleted(Long orderId);

    /**
     * 支付模块依赖的订单最小字段集合。
     */
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
