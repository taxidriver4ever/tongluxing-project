package com.tongdao.map.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class MapRoutePlan {
    private Long id;
    private Long userId;
    private String routeHash;
    private String routePointsJson;
    private String routeResultJson;
    private String providerType;
    private String planStatus;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
