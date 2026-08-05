package com.tongluxing.team.vo;

/** 入队/归队申请响应。 */
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
        Boolean mutual,
        String applicationType,
        String joinRole,
        String linkedOwnerUserId,
        String linkedVehicleId,
        String plateReference,
        String ownerConfirmStatus,
        String currentLatitude,
        String currentLongitude
) {
}
