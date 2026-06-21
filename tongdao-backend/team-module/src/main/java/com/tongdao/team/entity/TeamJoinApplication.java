package com.tongdao.team.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class TeamJoinApplication {
    private Long id;
    private Long teamId;
    private Long tripId;
    private Long applicantUserId;
    private Long applicantVehicleId;
    private Long reviewerUserId;
    private String applicationStatus;
    private String applyMessage;
    private String joinQuestionJson;
    private String reviewMessage;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
