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
