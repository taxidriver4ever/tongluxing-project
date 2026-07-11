package com.tongluxing.drivertrack.vo;

/**
 * 里程结算结果。
 */
public record MileageSettlementResponse(
        String tripId,
        String userId,
        Integer distanceMeters,
        Integer settledStages,
        Integer grantedPoints,
        Boolean duplicate
) {
}
