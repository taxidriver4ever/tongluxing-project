package com.tongdao.growth.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class GrowthQueryDTO {
    private Long id;
    private Integer totalPoints;
    private String levelCode;
    private Integer version;
    private String bizType;
    private String bizId;
    private Integer pointDelta;
    private Integer balanceAfter;
    private String remark;
    private LocalDateTime createdAt;
    private Long badgeId;
    private String badgeCode;
    private String badgeName;
    private String badgeImageKey;
    private String eventType;
    private Integer threshold;
    private LocalDateTime awardedAt;
}
