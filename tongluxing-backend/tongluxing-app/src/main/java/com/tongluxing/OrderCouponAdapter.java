package com.tongluxing;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.tongluxing.coupon.service.CouponService;
import com.tongluxing.order.integration.OrderCouponPort;
import com.tongluxing.user.model.UserModels.CouponLockRequest;
import com.tongluxing.user.model.UserModels.CouponOrderResultRequest;

import lombok.RequiredArgsConstructor;

/**
 * 订单模块调用优惠券模块的应用层适配器。
 */
@Component
@RequiredArgsConstructor
public class OrderCouponAdapter implements OrderCouponPort {
    private final CouponService couponService;

    @Override
    public void lockCoupon(Long userCouponId, Long orderId, BigDecimal amount) {
        couponService.lock(userCouponId, new CouponLockRequest(orderId, amount));
    }

    @Override
    public void confirmCoupon(Long orderId) {
        couponService.orderResult(new CouponOrderResultRequest(orderId, "SUCCESS"));
    }

    @Override
    public void releaseCoupon(Long orderId) {
        couponService.orderResult(new CouponOrderResultRequest(orderId, "CANCELLED"));
    }
}
