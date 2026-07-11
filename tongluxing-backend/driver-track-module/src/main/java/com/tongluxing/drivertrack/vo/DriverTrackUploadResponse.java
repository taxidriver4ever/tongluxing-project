package com.tongluxing.drivertrack.vo;

/**
 * GPS 点上传响应。
 */
public record DriverTrackUploadResponse(
        String trackId,
        Integer distanceFromPrev,
        Integer deviationStatus,
        Integer deviationDistance,
        Integer totalDistance
) {
}
