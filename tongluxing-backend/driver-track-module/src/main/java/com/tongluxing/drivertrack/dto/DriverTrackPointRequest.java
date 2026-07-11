package com.tongluxing.drivertrack.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

/**
 * App 驾驶端 GPS 点上传请求。
 */
public record DriverTrackPointRequest(
        @NotNull Long tripId,
        @NotNull BigDecimal longitude,
        @NotNull BigDecimal latitude,
        BigDecimal speed,
        BigDecimal direction,
        BigDecimal accuracy,
        @NotNull LocalDateTime recordTime
) {
}
