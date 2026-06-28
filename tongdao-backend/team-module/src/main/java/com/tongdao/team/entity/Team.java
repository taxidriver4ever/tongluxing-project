package com.tongdao.team.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
/**
 * Team 数据库实体。
 */
public class Team {
    private Long id;
    private Long tripId;
    private Long ownerUserId;
    private Long ownerVehicleId;
    private String teamName;
    private String teamDesc;
    private String startName;
    private String endName;
    private LocalDateTime departureTime;
    private Integer maxMemberCount;
    private Integer currentMemberCount;
    private String joinMode;
    private String teamStatus;
    private Integer publicFlag;
    private Long chatConversationId;
    private String notice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
