package com.tongluxing.team.vo;

/**
 * TeamApplicationResponse 响应数据对象。
 */
public record TeamApplicationResponse(
        String applicationId,
        String teamId,
        String tripId,
        String applicantUserId,
        String nickname,
        String avatarImageKey,
        String applicantVehicleId,
        Boolean wantsToDrive,
        String vehicleSummary,
        String conversationName,
        String applicationStatus,
        String applyMessage,
        String reviewMessage,
        String reviewedAt,
        String createdAt,
        Boolean following,
        Boolean followedByTarget,
        Boolean mutual
) {
}
