package com.tongluxing.trip.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 推荐/发现候选的轻量数据库投影。
 *
 * <p>刻意不包含完整路线 polyline。用户、成长、车辆公开摘要通过一次 SQL 聚合，
 * 避免候选池进入 Java 后再逐条查询造成 N+1。</p>
 */
@Data
public class TripMatchCandidateRow {
    private Long tripId;
    private String tripNumber;
    private Long userId;
    private Long vehicleId;
    private String tripType;
    private String publisherRole;
    private Long captainUserId;
    private String ownerNickname;
    private String ownerAvatarImageKey;
    private Boolean driverVerified;
    private String ownerLevelCode;
    private Integer ownerTotalTripCount;
    private Long ownerTotalDistanceMeters;
    private LocalDateTime ownerLastActiveAt;
    private Integer ownerBadgeCount;
    private String vehicleType;
    private String vehicleBrand;
    private String vehicleModel;
    private String vehicleRequirements;
    private String budgetDescription;
    private String title;
    private String description;
    private String startName;
    private String endName;
    private BigDecimal startLatitude;
    private BigDecimal startLongitude;
    private BigDecimal endLatitude;
    private BigDecimal endLongitude;
    private LocalDateTime departureTime;
    private Integer estimatedDays;
    private Integer routeDistance;
    private Integer routeDuration;
    private String waypointsJson;
    private String remark;
    private String travelDepth;
    private Integer expectedPeople;
    private Integer maxVehicleCount;
    private Integer joinedVehicleCount;
    private String status;
    private Integer publicFlag;
}
