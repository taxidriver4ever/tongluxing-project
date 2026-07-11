package com.tongluxing.order.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 订单主表实体，对应 order_trade 表。
 *
 * <p>记录订单金额、支付、核销、退款、分账和生命周期状态，是订单模块的事实主表。</p>
 */
@Data
public class OrderTrade {
    /** 订单主键。 */
    private Long id;
    /** 业务订单号，用于前端、商家后台和支付侧展示。 */
    private String orderNo;
    /** 下单用户 ID。 */
    private Long userId;
    /** 商家 ID。 */
    private Long merchantId;
    /** 商品 ID。 */
    private Long productId;
    /** 拼团活动 ID；普通订单为空。 */
    private Long activityId;
    /** 商品原始总额。 */
    private BigDecimal originalAmount;
    /** 拼团优惠金额。 */
    private BigDecimal groupbuyDiscountAmount;
    /** 优惠券抵扣金额。 */
    private BigDecimal couponDeductionAmount;
    /** 应付金额。 */
    private BigDecimal payableAmount;
    /** 实付金额。 */
    private BigDecimal paidAmount;
    /** 锁定的用户优惠券 ID。 */
    private Long userCouponId;
    /** 订单主状态。 */
    private String orderStatus;
    /** 支付状态。 */
    private String paymentStatus;
    /** 核销状态。 */
    private String verificationStatus;
    /** 退款状态。 */
    private String refundStatus;
    /** 分账状态。 */
    private String profitSharingStatus;
    /** 待支付订单过期时间。 */
    private LocalDateTime expireAt;
    /** 支付成功时间。 */
    private LocalDateTime paidAt;
    /** 订单完成时间。 */
    private LocalDateTime completedAt;
    /** 用户下单备注。 */
    private String remark;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记，0 表示有效。 */
    private Integer deleted;
}

