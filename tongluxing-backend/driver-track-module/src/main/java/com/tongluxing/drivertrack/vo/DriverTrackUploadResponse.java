package com.tongluxing.drivertrack.vo;

/** GPS 点上传、轨迹风控和分段状态响应。 */
public record DriverTrackUploadResponse(
        String trackId,
        Integer distanceFromPrev,
        Integer deviationStatus,
        Integer deviationDistance,
        Integer totalDistance,
        Integer settledStages,
        Integer grantedPoints,
        String reachedWaypointId,
        String reachedWaypointName,
        String pointStatus,
        Integer riskScore,
        String riskLevel,
        Boolean settlementReviewRequired,
        String message,
        Boolean accepted
) {
}
