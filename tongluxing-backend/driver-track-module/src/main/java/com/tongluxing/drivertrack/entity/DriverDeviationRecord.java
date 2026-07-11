package com.tongluxing.drivertrack.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 驾驶偏航记录。
 */
@Data
public class DriverDeviationRecord {
    private Long id;
    private Long tripId;
    private Long driverId;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Integer deviationDistance;
    private Integer deviationStatus;
    private LocalDateTime recordTime;
    private LocalDateTime createdAt;
    private Integer deleted;
}
