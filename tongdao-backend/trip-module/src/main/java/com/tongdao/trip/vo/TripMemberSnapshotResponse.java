package com.tongdao.trip.vo;

public record TripMemberSnapshotResponse(
        String userId,
        String vehicleId,
        String memberRole,
        String joinStatus,
        String nicknameSnapshot,
        String vehicleSnapshot,
        String joinedAt
) {
}
