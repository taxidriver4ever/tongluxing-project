package com.tongluxing.drivertrack.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/** 驾驶轨迹点。 */
@Data
public class DriverTrackRecord {
    private Long id;
    private Long tripId;
    private Long driverId;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal altitude;
    private BigDecimal speed;
    private BigDecimal direction;
    private BigDecimal accuracy;
    private Integer rawDistanceFromPrev;
    private Integer distanceFromPrev;
    private BigDecimal calculatedSpeedKmh;
    private String provider;
    private String appState;
    private Integer batteryLevel;
    private String deviceId;
    private Long sequenceNo;
    private Integer mockLocation;
    private String pointStatus;
    private Integer validPoint;
    private Integer riskScore;
    private String riskFlags;
    private String rejectReason;
    private LocalDateTime recordTime;
    private LocalDateTime clientSendTime;
    private LocalDateTime serverReceiveTime;
    private LocalDateTime createdAt;
    private Integer deleted;
}
