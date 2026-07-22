package com.tongluxing.drivertrack.vo;

/** GPS 点上传、偏航提醒和分段结算响应。 */
public record DriverTrackUploadResponse(
        String trackId,
        Integer distanceFromPrev,
        Integer deviationStatus,
        Integer deviationDistance,
        Integer totalDistance,
        Integer settledStages,
        Integer grantedPoints,
        String reachedWaypointId,
        String reachedWaypointName
) {
}
