package com.tongdao.trip.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * TripWaypointRequest 请求参数对象。
 */
public record TripWaypointRequest(
        Long waypointId,
        @Min(1) Integer seqNo,
        @NotBlank @Size(max = 128) String placeName,
        BigDecimal lat,
        BigDecimal lng,
        @Min(0) @Max(1440) Integer stayMinutes
) {
}
