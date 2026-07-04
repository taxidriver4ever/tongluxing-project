package com.tongluxing.coupon.integration;

import com.tongluxing.user.model.UserModels.CouponCountVO;

/**
 * 优惠券模块对其他模块开放的门面接口。
 *
 * <p>其他模块通过该接口完成发券和统计查询，不需要关心优惠券模块内部表结构和实现细节。</p>
 */
public interface CouponFacade {

    /**
     * 统计指定用户可用券数量和即将过期券数量。
     */
    CouponCountVO count(Long userId);

    /**
     * 向指定用户发放优惠券。
     *
     * @param userId 领券用户 ID
     * @param templateId 优惠券模板 ID
     * @param sourceType 发券来源类型
     * @param sourceBizId 发券来源业务 ID，用于幂等控制
     */
    CouponIssueResult issue(Long userId, Long templateId, String sourceType, String sourceBizId);

    /**
     * 发券结果。
     *
     * @param couponUserId 用户优惠券 ID
     * @param status 当前优惠券状态
     * @param duplicate 是否为重复发券请求
     */
    record CouponIssueResult(Long couponUserId, String status, boolean duplicate) {
    }
}
