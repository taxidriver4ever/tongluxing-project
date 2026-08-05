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
    private String tripNumber;
    private Long userId;
    private Long vehicleId;
    /** DRIVER_TRIP：车主行程；PASSENGER_DEMAND：乘客出行需求。 */
    private String tripType;
    /** 发布时的身份快照：DRIVER 或 PASSENGER。 */
    private String publisherRole;
    /** 实际队长；乘客需求在匹配到车主前为空。 */
    private Long captainUserId;
    /** 是否允许到点后由系统自动出发。 */
    private Integer autoStartEnabled;
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
    /** NONE / ARRIVED_PENDING / CONTINUING / FINISHED。 */
    private String arrivalStatus;
    private LocalDateTime arrivalEnteredAt;
    private LocalDateTime arrivalDecisionDeadline;
    private Integer continueCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
