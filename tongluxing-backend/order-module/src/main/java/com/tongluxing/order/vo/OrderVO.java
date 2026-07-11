package com.tongluxing.order.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情响应视图。
 *
 * @param orderId 订单 ID
 * @param orderNo 对用户和商家展示的订单号
 * @param userId 下单用户 ID
 * @param merchantId 商家 ID
 * @param productId 商品 ID
 * @param activityId 拼团活动 ID；普通购买时为空
 * @param orderStatus 订单主状态，如 WAIT_PAY、PAID、VERIFIED、COMPLETED、CANCELLED、REFUNDED
 * @param paymentStatus 支付状态，如 WAIT_PAY、PAYING、SUCCESS、CLOSED
 * @param verificationStatus 核销状态，如 UNGENERATED、WAIT_VERIFY、VERIFIED
 * @param refundStatus 退款状态，如 NONE、REFUNDING、SUCCESS
 * @param profitSharingStatus 分账状态，如 NONE、WAIT_SHARING、SUCCESS
 * @param originalAmount 商品原始总额
 * @param groupbuyDiscountAmount 拼团优惠金额
 * @param couponDeductionAmount 优惠券抵扣金额
 * @param payableAmount 应付金额
 * @param paidAmount 实付金额
 * @param lockedCouponId 已锁定的用户优惠券 ID
 * @param expireAt 待支付订单过期时间
 * @param paidAt 支付成功时间
 * @param completedAt 订单完成时间
 * @param items 订单商品明细
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

