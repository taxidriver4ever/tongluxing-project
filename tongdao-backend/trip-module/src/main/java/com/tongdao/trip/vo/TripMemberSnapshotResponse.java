package com.tongdao.trip.vo;

/**
 * TripMemberSnapshotResponse 响应数据对象。
 */
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
