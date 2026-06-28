package com.tongdao.order.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
/**
 * OrderVO 视图响应对象。
 */

public record OrderVO(
        Long orderId,
        String orderNo,
        Long userId,
        Long merchantId,
        Long productId,
        Long activityId,
        String orderStatus,
        String paymentStatus,
        String verificationStatus,
        String refundStatus,
        String profitSharingStatus,
        BigDecimal originalAmount,
        BigDecimal groupbuyDiscountAmount,
        BigDecimal couponDeductionAmount,
        BigDecimal payableAmount,
        BigDecimal paidAmount,
        Long lockedCouponId,
        LocalDateTime expireAt,
        LocalDateTime paidAt,
        LocalDateTime completedAt,
        List<OrderItemVO> items
) {
}

