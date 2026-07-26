package com.tongluxing.drivertrack.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * App 驾驶端 GPS 点上传请求。
 */
public record DriverTrackPointRequest(
        @NotNull Long tripId,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @DecimalMin("0") BigDecimal speed,
        @DecimalMin("0") @DecimalMax("360") BigDecimal direction,
        @DecimalMin("0") BigDecimal accuracy,
        @NotNull LocalDateTime recordTime,
        String deviceId,
        Long sequenceNo,
        Boolean mockLocation
) {
}
