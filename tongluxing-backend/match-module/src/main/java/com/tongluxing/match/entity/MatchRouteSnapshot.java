package com.tongluxing.match.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
/**
 * MatchRouteSnapshot 数据库实体。
 */
public class MatchRouteSnapshot {
    private Long id;
    private Long tripId;
    private Long userId;
    private Long vehicleId;
    private String startName;
    private String startAddress;
    private BigDecimal startLatitude;
    private BigDecimal startLongitude;
    private String endName;
    private String endAddress;
    private BigDecimal endLatitude;
    private BigDecimal endLongitude;
    private String routePointsJson;
    private Integer routeDistance;
    private Integer routeDuration;
    private LocalDateTime departureTime;
    private String travelDepth;
    private Integer maxVehicleCount;
    private Integer publicFlag;
    private String snapshotStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
