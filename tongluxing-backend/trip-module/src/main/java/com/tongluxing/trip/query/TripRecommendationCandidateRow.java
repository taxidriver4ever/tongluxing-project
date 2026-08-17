package com.tongluxing.trip.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/** 推荐第一阶段专用投影，只包含过滤、评分和排序字段。 */
@Data
public class TripRecommendationCandidateRow {
    private Long tripId;
    private Long userId;
    private BigDecimal startLatitude;
    private BigDecimal startLongitude;
    private BigDecimal endLatitude;
    private BigDecimal endLongitude;
    private LocalDateTime departureTime;
    private Integer estimatedDays;
    private Integer routeDistance;
    private String waypointsJson;
    private String travelDepth;
    private Integer maxVehicleCount;
    private Integer joinedVehicleCount;
    private String status;
}

