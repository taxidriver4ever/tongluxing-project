package com.tongluxing.trip.entity;

import java.time.LocalDateTime;

import lombok.Data;

/** 创建行程流程使用的可增量保存草稿。 */
@Data
public class TripCreationDraft {
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String startLocationJson;
    private String endLocationJson;
    private LocalDateTime departureTime;
    private Integer durationDays;
    private Integer peopleCount;
    private String vehicleRequirements;
    private String budgetDescription;
    private String notes;
    private String remark;
    private String draftStatus;
    private Long publishedTripId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
