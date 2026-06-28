package com.tongdao.trip.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * TripResponse 响应数据对象。
 */
public record TripResponse(
        String tripId,
        String userId,
        String vehicleId,
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
        String travelDepth,
        Boolean publicFlag,
        String status,
        String remark,
        List<WaypointLocationResponse> waypoints,
        String createdAt,
        String updatedAt
) {
}
