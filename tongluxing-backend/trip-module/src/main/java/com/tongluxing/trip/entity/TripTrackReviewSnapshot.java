package com.tongluxing.trip.entity;

import java.time.LocalDateTime;

import lombok.Data;

/** 轨迹风险汇总与人工审核快照。 */
@Data
public class TripTrackReviewSnapshot {
    private Long tripId;
    private Long primaryUserId;
    private Integer rawDistanceMeters;
    private Integer filteredDistanceMeters;
    private Integer approvedDistanceMeters;
    private Integer totalPointCount;
    private Integer validPointCount;
    private Integer invalidPointCount;
    private Integer locationGapCount;
    private Integer warningCount;
    private Integer hardAnomalyCount;
    private Integer riskScore;
    private String riskLevel;
    private String settlementStatus;
    private String reviewReason;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
}
