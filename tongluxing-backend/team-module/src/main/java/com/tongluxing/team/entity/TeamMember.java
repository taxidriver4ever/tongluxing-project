package com.tongluxing.team.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
/**
 * TeamMember 数据库实体。
 */
public class TeamMember {
    private Long id;
    private Long teamId;
    private Long userId;
    private Long vehicleId;
    private Long linkedOwnerUserId;
    private Long linkedVehicleId;
    /** 只保存脱敏车牌，不保存用户输入的完整车牌。 */
    private String plateReference;
    private String ownerConfirmStatus = "NOT_REQUIRED";
    private Long removedByUserId;
    private String removedReason;
    private String memberRole;
    private String memberStatus;
    private LocalDateTime joinedAt;
    private LocalDateTime exitedAt;
    private String nicknameSnapshot;
    private String vehicleSnapshot;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
