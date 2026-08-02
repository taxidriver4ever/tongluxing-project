package com.tongluxing.user.vo;

/**
 * 同路人搜索结果，包含公开资料摘要和当前用户的关注关系。
 *
 * <p>该模型将可搜索的公开名片、行程活跃度、关注计数和按钮状态合并，供搜索页
 * 直接渲染；不会包含生日、隐私开关或认证证件信息。</p>
 *
 * @param userId 用户 ID
 * @param tongluxingId 同路行号
 * @param nickname 昵称
 * @param avatarImageKey 头像资源 Key
 * @param cityName 公开城市
 * @param bio 公开简介
 * @param certificationStatus 最新认证状态
 * @param totalTripCount 累计行程数
 * @param totalDistanceMeters 累计距离（米）
 * @param followerCount 粉丝数
 * @param followingCount 关注数
 * @param following 当前用户是否关注该用户
 * @param followedByTarget 该用户是否关注当前用户
 * @param mutual 是否互相关注
 */
public record UserSearchVO(
        Long userId, String tongluxingId, String nickname, String avatarImageKey, String cityName, String bio,
        String certificationStatus, Integer totalTripCount, Long totalDistanceMeters,
        Long followerCount, Long followingCount,
        Boolean following, Boolean followedByTarget, Boolean mutual
) {
}

