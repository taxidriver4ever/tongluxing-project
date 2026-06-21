package com.tongdao.map.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class MapLocationSearchLog {
    private Long id;
    private Long userId;
    private String keyword;
    private String selectedName;
    private String selectedAddress;
    private BigDecimal selectedLatitude;
    private BigDecimal selectedLongitude;
    private String scene;
    private String providerType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
