package com.tongluxing.user.vo;

import java.time.LocalDateTime;

/**
 * 粉丝/关注列表中的公开用户摘要。
 *
 * <p>followedAt 的方向由列表语境决定；following、followedByTarget 和 mutual
 * 始终以当前登录用户为观察者，前端无需再次推导关系。</p>
 *
 * @param userId 列表用户 ID
 * @param nickname 昵称
 * @param avatarImageKey 头像资源 Key
 * @param certificationStatus 最新认证状态
 * @param totalTripCount 累计行程数
 * @param totalDistanceMeters 累计行驶距离（米）
 * @param followedAt 关注关系建立时间
 * @param following 当前用户是否关注列表用户
 * @param followedByTarget 列表用户是否关注当前用户
 * @param mutual 是否互相关注
 */
public record FollowUserVO(
        Long userId, String nickname, String avatarImageKey, String certificationStatus,
        Integer totalTripCount, Long totalDistanceMeters, LocalDateTime followedAt,
        Boolean following, Boolean followedByTarget, Boolean mutual
) {
}

