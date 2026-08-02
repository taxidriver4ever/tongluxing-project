package com.tongluxing.user.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户优惠券详情返回对象。
 *
 * <p>lockedOrderId 表示优惠券正被未完成订单占用，usedOrderId 表示最终核销订单，
 * 两者用于区分临时锁定与已使用状态。</p>
 *
 * @param id 用户优惠券主键
 * @param templateId 模板主键
 * @param couponName 展示名称
 * @param couponType 类型
 * @param issuerId 发放方 ID
 * @param thresholdAmount 使用门槛
 * @param discountAmount 抵扣金额
 * @param scopeJson 适用范围 JSON
 * @param couponStatus 当前状态
 * @param validStartAt 生效时间
 * @param validEndAt 失效时间
 * @param lockedOrderId 当前锁定订单
 * @param usedOrderId 最终核销订单
 */
public record UserCouponDetailVO(
        Long id, Long templateId, String couponName, String couponType, Long issuerId,
        BigDecimal thresholdAmount, BigDecimal discountAmount, String scopeJson, String couponStatus,
        LocalDateTime validStartAt, LocalDateTime validEndAt, Long lockedOrderId, Long usedOrderId
) {
}

