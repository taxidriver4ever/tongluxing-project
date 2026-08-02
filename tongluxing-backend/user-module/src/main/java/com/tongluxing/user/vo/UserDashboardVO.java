package com.tongluxing.user.vo;

/**
 * 用户首页聚合数据返回对象。
 *
 * <p>把资料、成长值、优惠券、邀请进度和下一份草稿合并，减少首页多次网络请求。</p>
 *
 * @param profile 当前用户资料
 * @param growth 成长值概览
 * @param coupon 优惠券数量摘要
 * @param invitation 邀请进度摘要
 * @param nextTripDraft 下一份待处理行程草稿
 */
public record UserDashboardVO(
        UserProfileVO profile, GrowthSummaryVO growth, CouponCountVO coupon,
        InvitationSummaryVO invitation, TripDraftVO nextTripDraft
) {
}

