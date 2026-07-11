package com.tongluxing.order.integration;

import java.math.BigDecimal;

/**
 * 订单模块访问优惠券模块的端口。
 *
 * <p>order-module 只依赖端口，不直接依赖 coupon-module，避免模块间形成强耦合。</p>
 */
public interface OrderCouponPort {

    /**
     * 为订单锁定指定用户券。
     *
     * @param userCouponId 用户优惠券 ID
     * @param orderId 订单 ID
     * @param amount 优惠券作用前的订单金额
     */
    void lockCoupon(Long userCouponId, Long orderId, BigDecimal amount);

    /**
     * 订单支付成功后确认用券。
     *
     * @param orderId 订单 ID
     */
    void confirmCoupon(Long orderId);

    /**
     * 订单取消、超时或支付失败后释放用券。
     *
     * @param orderId 订单 ID
     */
    void releaseCoupon(Long orderId);
}
