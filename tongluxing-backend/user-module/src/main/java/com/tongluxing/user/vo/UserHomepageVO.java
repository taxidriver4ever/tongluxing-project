package com.tongluxing.user.vo;

/**
 * 用户公开主页聚合返回对象。
 *
 * <p>聚合公开资料、成长值、徽章墙、主车辆、IP 属地和关注状态。上游聚合服务必须
 * 根据用户隐私设置决定等级与车辆是否填充，不能仅依赖客户端隐藏。</p>
 *
 * @param profile 已裁剪的公开资料
 * @param growth 允许展示时的成长概览
 * @param badges 徽章墙
 * @param mainVehicle 允许展示时的主车辆摘要
 * @param ipProvince IP 归属省份
 * @param follow 当前用户与主页用户的关注关系
 */
public record UserHomepageVO(PublicProfileVO profile, GrowthSummaryVO growth, BadgeWallVO badges,
                             PublicVehicleSummaryVO mainVehicle, String ipProvince, FollowStatusVO follow) {
}

