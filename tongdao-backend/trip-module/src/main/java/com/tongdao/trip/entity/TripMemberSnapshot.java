package com.tongdao.trip.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
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
