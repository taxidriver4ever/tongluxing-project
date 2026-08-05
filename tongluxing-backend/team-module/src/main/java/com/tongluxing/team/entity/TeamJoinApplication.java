package com.tongluxing.team.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
/**
 * TeamJoinApplication 数据库实体。
 */
public class TeamJoinApplication {
    private Long id;
    private Long teamId;
    private Long tripId;
    private Long applicantUserId;
    private Long applicantVehicleId;
    /** JOIN：首次加入；RETURN：退出或被移除后的归队。 */
    private String applicationType;
    /** DRIVER 或 PASSENGER。 */
    private String joinRole;
    private Long linkedOwnerUserId;
    private Long linkedVehicleId;
    private String plateReference;
    private java.math.BigDecimal currentLatitude;
    private java.math.BigDecimal currentLongitude;
    private String ownerConfirmStatus = "NOT_REQUIRED";
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
