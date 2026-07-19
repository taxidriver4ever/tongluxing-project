package com.tongluxing.trip.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 行程规划路线快照。
 */
@Data
public class TripRoute {
    private Long id;
    private Long tripId;
    private Long draftId;
    private Long routePlanId;
    private String origin;
    private String destination;
    private String waypoints;
    private String polyline;
    private Integer planDistance;
    private Integer planDuration;
    private String providerType;
    private String routeStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
