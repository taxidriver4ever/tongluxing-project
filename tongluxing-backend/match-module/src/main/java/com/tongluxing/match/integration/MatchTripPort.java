package com.tongluxing.match.integration;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 匹配模块访问行程数据的跨模块端口。
 */
public interface MatchTripPort {

    /**
     * 根据行程 ID 获取用于匹配计算的行程摘要。
     */
    MatchTripDTO getTrip(Long tripId);

    /**
     * 查询公开行程列表，作为推荐候选池。
     */
    List<MatchTripDTO> listPublicTrips(int limit);

    /**
     * 行程匹配所需的最小字段集合。
     */
    record MatchTripDTO(
            Long tripId,
            Long userId,
            Long vehicleId,
            String ownerNickname,
            String ownerAvatarImageKey,
            Boolean driverVerified,
            String ownerLevelCode,
            Integer ownerTotalTripCount,
            Long ownerTotalDistanceMeters,
            String ownerLastActiveAt,
            Integer ownerBadgeCount,
            String vehicleType,
            String vehicleSummary,
            String title,
            String description,
            String startName,
            String endName,
            Double startLatitude,
            Double startLongitude,
            Double endLatitude,
            Double endLongitude,
            LocalDateTime departureTime,
            Integer estimatedDays,
            Integer routeDistance,
            Integer routeDuration,
            String routePolyline,
            String waypointsJson,
            String remark,
            String travelDepth,
            Integer expectedPeople,
            Integer maxVehicleCount,
            Integer joinedVehicleCount,
            String status,
            Integer publicFlag
    ) {
    }
}
