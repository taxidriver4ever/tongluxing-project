package com.tongdao.coupon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 优惠券模块查询结果 DTO。
 *
 * <p>该对象用于承接 coupon_template、coupon_user 以及两表关联查询的结果。
 * 服务层会根据不同接口场景转换成列表、详情、可用券等 VO。</p>
 */
@Data
public class CouponQueryDTO {
    /** 用户优惠券 ID 或模板 ID，取决于具体查询场景。 */
    private Long id;

    /** 领券用户 ID。 */
    private Long userId;

    /** 优惠券模板 ID。 */
    private Long templateId;

    /** 优惠券名称。 */
    private String couponName;

    /** 优惠券类型。 */
    private String couponType;

    /** 发行方/商户 ID；为空表示平台通用。 */
    private Long issuerId;

    /** 使用门槛金额。 */
    private BigDecimal thresholdAmount;

    /** 固定抵扣金额。 */
    private BigDecimal discountAmount;

    /** 当前订单下实际可抵扣金额。 */
    private BigDecimal deductionAmount;

    /** 适用范围 JSON，例如订单类型、商户范围等。 */
    private String scopeJson;

    /** 用户优惠券状态，例如 AVAILABLE、LOCKED、USED。 */
    private String couponStatus;

    /** 用户优惠券有效期开始时间。 */
    private LocalDateTime validStartAt;

    /** 用户优惠券有效期结束时间。 */
    private LocalDateTime validEndAt;

    /** 当前锁定该券的订单 ID。 */
    private Long lockedOrderId;

    /** 最终使用该券的订单 ID。 */
    private Long usedOrderId;

    /** 模板有效期类型，例如固定时间段或领取后 N 天。 */
    private String validityType;

    /** 领取后 N 天有效时的天数配置。 */
    private Integer validDays;
}
