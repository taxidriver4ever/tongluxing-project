package com.tongdao.team.vo;

/**
 * TeamApplicationResponse 响应数据对象。
 */
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
