package com.tongluxing.coupon.dto;

/**
 * 内部发券请求。
 *
 * @param userId 领券用户 ID
 * @param templateId 优惠券模板 ID
 * @param sourceType 发券来源类型，例如领取、活动、邀请奖励
 * @param sourceBizId 发券来源业务 ID，用于幂等控制
 */
public record CouponIssueRequest(Long userId, Long templateId, String sourceType, String sourceBizId) {
}
