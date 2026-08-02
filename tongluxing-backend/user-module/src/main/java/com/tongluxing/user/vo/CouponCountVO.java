package com.tongluxing.user.vo;

/**
 * 优惠券数量摘要。
 *
 * @param availableCount 当前可使用数量
 * @param expiringCount 即将过期数量
 */
public record CouponCountVO(Integer availableCount, Integer expiringCount) {
}

