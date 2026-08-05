package com.tongluxing.trip.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 编辑行程请求。乘客需求允许 {@code vehicleId} 为空；车主行程仍由服务层校验认证车辆。
 */
public record UpdateTripRequest(
        Long vehicleId,
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
        @Min(1) @Max(50) Integer maxVehicleCount,
        @NotBlank @Size(max = 16) String travelDepth,
        @NotNull Boolean publicFlag,
        @Size(max = 8) List<@Size(max = 16) String> vehicleRequirements,
        @Size(max = 128) String budgetDescription,
        @Size(max = 255) String remark,
        @Valid List<WaypointLocationRequest> waypoints,
        @Pattern(regexp = "DRIVER_TRIP|PASSENGER_DEMAND") String tripType,
        Boolean autoStartEnabled
) {
    /** 兼容项目内部旧构造调用。 */
    public UpdateTripRequest(
            Long vehicleId, String title, String description, String coverImageKey, Integer expectedPeople,
            LocationRequest startLocation, LocationRequest endLocation, String routeSummary, String departureTime,
            Integer estimatedDays, Integer routeDistance, Integer routeDuration, String routePolyline,
            Integer maxVehicleCount, String travelDepth, Boolean publicFlag, List<String> vehicleRequirements,
            String budgetDescription, String remark, List<WaypointLocationRequest> waypoints) {
        this(vehicleId, title, description, coverImageKey, expectedPeople, startLocation, endLocation,
                routeSummary, departureTime, estimatedDays, routeDistance, routeDuration, routePolyline,
                maxVehicleCount, travelDepth, publicFlag, vehicleRequirements, budgetDescription, remark,
                waypoints, null, null);
    }
}
