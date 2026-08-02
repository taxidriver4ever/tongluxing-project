package com.tongluxing.match.vo;

/**
 * 发现行程场景可公开展示的发起人摘要。
 *
 * @param userId 发起人用户 ID
 * @param nickname 昵称
 * @param avatarImageKey 头像资源 Key
 * @param levelCode 用户等级编码
 * @param certificationStatus 驾驶认证状态
 * @param rating 公开评分；暂无数据时为空
 * @param totalTripCount 累计行程数
 * @param totalDistanceMeters 累计里程（米）
 * @param lastActiveAt 最近活跃时间文本
 * @param badgeCount 徽章数
 * @param followed 当前登录用户是否关注发起人
 */
public record TripDiscoverOwnerResponse(
        String userId,
        String nickname,
        String avatarImageKey,
        String levelCode,
        String certificationStatus,
        Double rating,
        Integer totalTripCount,
        Long totalDistanceMeters,
        String lastActiveAt,
        Integer badgeCount,
        Boolean followed
) {
}
