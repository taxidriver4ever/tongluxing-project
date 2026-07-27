package com.tongluxing.drivertrack.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** App 驾驶端 GPS 点上传请求。 */
public record DriverTrackPointRequest(
        @NotNull Long tripId,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        BigDecimal altitude,
        @DecimalMin("0") BigDecimal speed,
        @DecimalMin("0") @DecimalMax("360") BigDecimal direction,
        @NotNull @DecimalMin("0") BigDecimal accuracy,
        @NotNull LocalDateTime recordTime,
        LocalDateTime clientSendTime,
        String deviceId,
        @NotNull @Positive Long sequenceNo,
        Boolean mockLocation,
        String provider,
        @Min(0) @Max(100) Integer batteryLevel,
        String appState
) {
}
