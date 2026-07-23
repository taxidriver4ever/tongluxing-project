package com.tongluxing.match.vo;

/** 发现行程场景可公开展示的发起人摘要。 */
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
