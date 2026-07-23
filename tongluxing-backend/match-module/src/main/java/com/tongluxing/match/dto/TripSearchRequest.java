package com.tongluxing.match.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 发现同行的行程搜索条件。 */
public record TripSearchRequest(
        @NotBlank @Size(max = 128) String startName,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal startLatitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal startLongitude,
        @NotBlank @Size(max = 128) String endName,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal endLatitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal endLongitude,
        @Valid @Size(max = 5) List<LocationCondition> waypoints,
        @NotNull LocalDateTime departureStart,
        @NotNull LocalDateTime departureEnd,
        @Min(500) @Max(200000) Integer radiusMeters,
        @Min(0) @Max(1440) Integer timeToleranceMinutes,
        @Min(1) @Max(20) Integer minimumRemainingSeats,
        @Size(max = 32) String vehicleType,
        Boolean carpoolAllowed,
        Boolean driverVerified,
        @Size(max = 20) String sortBy,
        @Min(1) Integer page,
        @Min(1) @Max(50) Integer size
) {
    /** 搜索条件中的地点。 */
    public record LocationCondition(
            @NotBlank @Size(max = 128) String name,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude
    ) {
    }
}
