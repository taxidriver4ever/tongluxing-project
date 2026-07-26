package com.tongluxing.trip.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * TripResponse 响应数据对象。
 */
public record TripResponse(
        String tripId,
        String userId,
        String vehicleId,
        String title,
        String description,
        String coverImageKey,
        Integer expectedPeople,
        String startName,
        BigDecimal startLat,
        BigDecimal startLng,
        LocationResponse startLocation,
        String endName,
        BigDecimal endLat,
        BigDecimal endLng,
        LocationResponse endLocation,
        String routeSummary,
        String routePolylineKey,
        Integer routeDistance,
        Integer routeDuration,
        String routePolyline,
        String departureTime,
        Integer estimatedDays,
        Integer totalDistanceMeters,
        Integer maxVehicleCount,
        Integer joinedVehicleCount,
        List<String> vehicleRequirements,
        String budgetDescription,
        String travelDepth,
        Boolean publicFlag,
        String status,
        String remark,
        String actualStartTime,
        String actualEndTime,
        List<WaypointLocationResponse> waypoints,
        String createdAt,
        String updatedAt
) {
}
