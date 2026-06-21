package com.tongdao.team.vo;

public record TeamApplicationResponse(
        String applicationId,
        String teamId,
        String tripId,
        String applicantUserId,
        String applicantVehicleId,
        String applicationStatus,
        String applyMessage,
        String reviewMessage,
        String reviewedAt,
        String createdAt
) {
}
