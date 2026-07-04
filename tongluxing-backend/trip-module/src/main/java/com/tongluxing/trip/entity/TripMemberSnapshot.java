package com.tongluxing.trip.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
/**
 * TripMemberSnapshot 数据库实体。
 */
public class TripMemberSnapshot {
    private Long id;
    private Long tripId;
    private Long userId;
    private Long vehicleId;
    private String memberRole;
    private String joinStatus;
    private String nicknameSnapshot;
    private String vehicleSnapshot;
    private LocalDateTime joinedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
