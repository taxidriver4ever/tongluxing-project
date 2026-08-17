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
    /** RDP 简化后的匹配专用路线，推荐算法只读取该字段。 */
    private String matchPolyline;
    private Integer planDistance;
    private Integer planDuration;
    private String providerType;
    private String routeStatus;
    /** 起点/终点/途经点顺序的 SHA-256 指纹，用于判断路线是否真的失效。 */
    private String routeSignature;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
