package com.tongluxing.drivertrack.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 驾驶轨迹里程结算记录。
 */
@Data
public class DriverTrackDistanceRecord {
    private Long id;
    private Long tripId;
    private Long driverId;
    private Integer totalDistance;
    private Integer lastSettleDistance;
    private String settleType;
    private String settleKey;
    private LocalDateTime settleTime;
    private Integer eventPublished;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
