package com.tongluxing.application.adapter;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.tongluxing.coupon.service.CouponService;
import com.tongluxing.order.integration.OrderCouponPort;
import com.tongluxing.user.dto.request.CouponLockRequest;
import com.tongluxing.user.dto.request.CouponOrderResultRequest;

import lombok.RequiredArgsConstructor;

/**
 * 订单模块调用优惠券模块的应用层适配器。
 *
 * <p>实现 order-module 定义的优惠券端口，将锁券、确认用券和释放用券转发给 coupon-module。</p>
 */
@Component
@RequiredArgsConstructor
public class OrderCouponAdapter implements OrderCouponPort {
    private final CouponService couponService;

    /**
     * 为订单锁定用户优惠券。
     *
     * @param userCouponId 用户优惠券 ID
     * @param orderId 订单 ID
     * @param amount 用券前订单金额
     */
    @Override
    public void lockCoupon(Long userCouponId, Long orderId, BigDecimal amount) {
        couponService.lock(userCouponId, new CouponLockRequest(orderId, amount));
    }

    /**
     * 订单支付成功后确认优惠券已使用。
     *
     * @param orderId 订单 ID
     */
    @Override
    public void confirmCoupon(Long orderId) {
        couponService.orderResult(new CouponOrderResultRequest(orderId, "SUCCESS"));
    }

    /**
     * 订单取消或超时后释放优惠券。
     *
     * @param orderId 订单 ID
     */
    @Override
    public void releaseCoupon(Long orderId) {
        couponService.orderResult(new CouponOrderResultRequest(orderId, "CANCELLED"));
    }
}
