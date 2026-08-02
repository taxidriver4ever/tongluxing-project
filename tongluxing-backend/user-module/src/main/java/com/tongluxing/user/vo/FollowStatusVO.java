package com.tongluxing.user.vo;

/**
 * 当前用户与目标用户的关注关系及公开计数。
 *
 * @param userId 目标用户 ID
 * @param following 当前用户是否关注目标
 * @param followedByTarget 目标是否反向关注当前用户
 * @param mutual 两个方向是否同时存在
 * @param followerCount 目标用户粉丝数
 * @param followingCount 目标用户关注数
 */
public record FollowStatusVO(
        Long userId, Boolean following, Boolean followedByTarget, Boolean mutual,
        Long followerCount, Long followingCount
) {
}

