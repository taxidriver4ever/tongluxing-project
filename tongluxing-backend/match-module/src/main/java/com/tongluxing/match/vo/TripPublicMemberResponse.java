package com.tongluxing.match.vo;

/** 公开行程成员摘要，严禁包含手机号和完整车牌。 */
public record TripPublicMemberResponse(
        String userId, String nickname, String avatarImageKey, String role,
        String certificationStatus, Integer totalTripCount, Long totalDistanceMeters
) {
}
