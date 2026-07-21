package com.tongluxing.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 运营后台订单读模型。 */
public record AdminOrderVO(
        Long orderId, String orderNo, Long userId, String userName,
        Long merchantId, String merchantName, Long productId, String productName,
        BigDecimal originalAmount, BigDecimal discountAmount, BigDecimal payableAmount,
        BigDecimal paidAmount, String orderStatus, String paymentStatus,
        String verificationStatus, String refundStatus, LocalDateTime createdAt,
        LocalDateTime paidAt, LocalDateTime completedAt) {}
