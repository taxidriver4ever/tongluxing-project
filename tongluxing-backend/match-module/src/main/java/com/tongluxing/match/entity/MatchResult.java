package com.tongluxing.match.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
/**
 * MatchResult 数据库实体。
 */
public class MatchResult {
    private Long id;
    private Long sourceTripId;
    private Long targetTripId;
    private Long sourceUserId;
    private Long targetUserId;
    private Integer matchScore;
    private Integer overlapRate;
    private Integer distanceGapMeters;
    private Integer departureGapMinutes;
    private String scoreDetailJson;
    private String resultStatus;
    private LocalDateTime calculatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
