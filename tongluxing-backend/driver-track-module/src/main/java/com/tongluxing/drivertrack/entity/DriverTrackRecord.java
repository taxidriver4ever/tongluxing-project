package com.tongluxing.drivertrack.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 驾驶轨迹点。
 */
@Data
public class DriverTrackRecord {
    private Long id;
    private Long tripId;
    private Long driverId;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal speed;
    private BigDecimal direction;
    private BigDecimal accuracy;
    private Integer distanceFromPrev;
    private LocalDateTime recordTime;
    private LocalDateTime createdAt;
    private Integer deleted;
}
