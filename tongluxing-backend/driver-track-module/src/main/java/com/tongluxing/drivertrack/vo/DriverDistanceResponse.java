package com.tongluxing.drivertrack.vo;

/**
 * 实际里程累计与结算响应。
 */
public record DriverDistanceResponse(
        String tripId,
        String driverId,
        Integer totalDistance,
        Integer lastSettleDistance,
        String settleTime
) {
}
