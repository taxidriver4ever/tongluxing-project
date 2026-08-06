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
 * 创建行程请求。
 *
 * <p>P0 规则：车辆不是必填项。无已认证车辆的用户也可以发布
 * {@code PASSENGER_DEMAND}（乘客出行需求），但该发布者不会自动成为队长。</p>
 */
public record CreateTripRequest(
        Long vehicleId,
        @Size(max = 128) String title,
        @Size(max = 1000) String description,
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
        @Pattern(regexp = "AUTO|DRIVER_TRIP|PASSENGER_DEMAND") String tripType,
        Boolean autoStartEnabled
) {
    /** 兼容项目内部尚未显式传递 P0 新字段的旧构造调用。 */
    public CreateTripRequest(
            Long vehicleId, String title, String description, Integer expectedPeople,
            LocationRequest startLocation, LocationRequest endLocation, String routeSummary, String departureTime,
            Integer estimatedDays, Integer routeDistance, Integer routeDuration, String routePolyline,
            Integer maxVehicleCount, String travelDepth, Boolean publicFlag, List<String> vehicleRequirements,
            String budgetDescription, String remark, List<WaypointLocationRequest> waypoints) {
        this(vehicleId, title, description, expectedPeople, startLocation, endLocation,
                routeSummary, departureTime, estimatedDays, routeDistance, routeDuration, routePolyline,
                maxVehicleCount, travelDepth, publicFlag, vehicleRequirements, budgetDescription, remark,
                waypoints, "AUTO", Boolean.TRUE);
    }
}
