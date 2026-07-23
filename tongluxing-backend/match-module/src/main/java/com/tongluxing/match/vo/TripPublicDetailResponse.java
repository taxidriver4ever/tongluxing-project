package com.tongluxing.match.vo;

import java.util.List;

/** 发现行程公开详情聚合，不返回聊天消息或成员敏感字段。 */
public record TripPublicDetailResponse(
        String tripId,
        String title,
        String status,
        String startName,
        List<String> waypoints,
        String endName,
        String departureTime,
        Integer estimatedDays,
        String description,
        String routePolyline,
        Integer routeDistanceMeters,
        Integer routeDurationSeconds,
        Integer joinedVehicleCount,
        Integer maxVehicleCount,
        Integer memberCount,
        Integer maxMemberCount,
        Integer remainingSeats,
        String vehicleRequirement,
        String budgetDescription,
        String costSharingType,
        String meetingPoint,
        String notes,
        String announcement,
        String joinRequirement,
        List<String> tags,
        TripDiscoverOwnerResponse owner,
        List<TripPublicMemberResponse> members,
        String relationshipStatus,
        Boolean ownerTrip,
        Boolean allowConsultation,
        Boolean allowApply,
        Boolean joinable,
        Boolean favorited
) {
}
