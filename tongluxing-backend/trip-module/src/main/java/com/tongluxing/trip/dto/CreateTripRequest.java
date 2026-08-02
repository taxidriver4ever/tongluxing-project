package com.tongluxing.trip.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * CreateTripRequest 请求参数对象。
 */
public record CreateTripRequest(
        @NotNull Long vehicleId,
        @Size(max = 128) String title,
        @Size(max = 1000) String description,
        @Size(max = 512) String coverImageKey,
        @Min(1) @Max(50) Integer expectedPeople,
        @Valid @NotNull LocationRequest startLocation,
        @Valid @NotNull LocationRequest endLocation,
        @Size(max = 255) String routeSummary,
        @NotBlank String departureTime,
        @Min(1) @Max(365) Integer estimatedDays,
        @Min(0) Integer routeDistance,
        @Min(0) Integer routeDuration,
        String routePolyline,
        @NotNull @Min(1) @Max(50) Integer maxVehicleCount,
        @NotBlank @Size(max = 16) String travelDepth,
        @NotNull Boolean publicFlag,
        @Size(max = 8) List<@Size(max = 16) String> vehicleRequirements,
        @Size(max = 128) String budgetDescription,
        @Size(max = 255) String remark,
        @Valid List<WaypointLocationRequest> waypoints
) {
}
