package com.tongdao.growth.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class GrowthBadge {
    private Long id;
    private String badgeCode;
    private String badgeName;
    private String badgeImageKey;
    private String eventType;
    private Integer threshold;
    private Boolean enabledFlag;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
