package com.tongluxing.user.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券列表项返回对象。
 *
 * <p>thresholdAmount 是最低使用门槛，discountAmount 是抵扣金额；有效期与状态
 * 共同决定客户端是否显示为可用。</p>
 *
 * @param id 用户优惠券主键
 * @param templateId 优惠券模板主键
 * @param couponName 展示名称
 * @param couponType 类型
 * @param thresholdAmount 使用门槛
 * @param discountAmount 抵扣金额
 * @param couponStatus 当前状态
 * @param validStartAt 生效时间
 * @param validEndAt 失效时间
 */
public record CouponSummaryVO(
        Long id, Long templateId, String couponName, String couponType, BigDecimal thresholdAmount,
        BigDecimal discountAmount, String couponStatus, LocalDateTime validStartAt, LocalDateTime validEndAt
) {
}

