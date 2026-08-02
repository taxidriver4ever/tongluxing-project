package com.tongluxing.match.vo;

/**
 * 公开行程成员摘要，严禁包含手机号和完整车牌。
 *
 * @param userId 成员用户 ID
 * @param nickname 昵称
 * @param avatarImageKey 头像资源 Key
 * @param role 车队角色
 * @param certificationStatus 驾驶认证状态
 * @param totalTripCount 累计行程数
 * @param totalDistanceMeters 累计里程（米）
 */
public record TripPublicMemberResponse(
        String userId, String nickname, String avatarImageKey, String role,
        String certificationStatus, Integer totalTripCount, Long totalDistanceMeters
) {
}
