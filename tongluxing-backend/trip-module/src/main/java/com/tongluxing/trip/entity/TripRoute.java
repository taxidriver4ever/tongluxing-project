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
    private String origin;
    private String destination;
    private String waypoints;
    private String polyline;
    private Integer planDistance;
    private Integer planDuration;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
