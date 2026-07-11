package com.tongluxing.drivertrack.vo;

/**
 * 最近一次偏航状态响应。
 */
public record DriverDeviationResponse(
        String tripId,
        Integer deviationStatus,
        Integer deviationDistance,
        String recordTime
) {
}
