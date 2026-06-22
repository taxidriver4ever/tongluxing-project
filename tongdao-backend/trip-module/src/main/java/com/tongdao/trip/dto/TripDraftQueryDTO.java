package com.tongdao.trip.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class TripDraftQueryDTO {
    private Long draftId;
    private Long userId;
    private String startJson;
    private String endJson;
    private String waypointJson;
    private LocalDateTime departureTime;
    private Integer durationDays;
    private Integer peopleCount;
    private String remark;
    private String draftStatus;
    private Long publishedTripId;
    private LocalDateTime updatedAt;
}
