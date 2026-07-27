package com.tongluxing.admin.vo;

import java.time.LocalDateTime;

/** 后台轨迹风险与结算汇总。 */
public record AdminTripTrackReviewSummaryVO(
        Long tripId,
        String tripTitle,
        Long primaryUserId,
        String captainNickname,
        Integer rawDistanceMeters,
        Integer filteredDistanceMeters,
        Integer approvedDistanceMeters,
        Integer totalPointCount,
        Integer validPointCount,
        Integer invalidPointCount,
        Integer locationGapCount,
        Integer warningCount,
        Integer hardAnomalyCount,
        Integer riskScore,
        String riskLevel,
        String settlementStatus,
        String reviewReason,
        Long reviewerId,
        LocalDateTime reviewedAt,
        LocalDateTime updatedAt
) {
}
