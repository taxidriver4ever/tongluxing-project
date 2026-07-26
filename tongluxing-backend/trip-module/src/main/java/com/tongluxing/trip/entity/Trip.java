package com.tongluxing.trip.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
/**
 * Trip 数据库实体。
 */
public class Trip {
    private Long id;
    private Long userId;
    private Long vehicleId;
    private String title;
    private String description;
    private String coverImageKey;
    private Integer expectedPeople;
    private String startName;
    private BigDecimal startLat;
    private BigDecimal startLng;
    private String startLocationName;
    private String startLocationAddress;
    private BigDecimal startLatitude;
    private BigDecimal startLongitude;
    private String endName;
    private BigDecimal endLat;
    private BigDecimal endLng;
    private String endLocationName;
    private String endLocationAddress;
    private BigDecimal endLatitude;
    private BigDecimal endLongitude;
    private String routeSummary;
    private String routePolylineKey;
    private Integer routeDistance;
    private Integer routeDuration;
    private String routePolyline;
    private String waypointsJson;
    private LocalDateTime departureTime;
    private Integer estimatedDays;
    private Integer totalDistanceMeters;
    private Integer maxVehicleCount;
    private Integer joinedVehicleCount;
    private String vehicleRequirements;
    private String budgetDescription;
    private String travelDepth;
    private Integer publicFlag;
    private String status;
    private String remark;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
