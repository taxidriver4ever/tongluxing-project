package com.tongluxing.trip.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
/**
 * TripWaypoint 数据库实体。
 */
public class TripWaypoint {
    private Long id;
    private Long tripId;
    private Long draftId;
    private Integer seqNo;
    private String placeName;
    private String placeAddress;
    private String waypointType;
    private BigDecimal lat;
    private BigDecimal lng;
    private Integer stayMinutes;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
