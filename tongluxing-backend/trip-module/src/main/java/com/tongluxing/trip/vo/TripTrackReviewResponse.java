package com.tongluxing.trip.vo;

/** 管理员轨迹结算审核结果。 */
public record TripTrackReviewResponse(
        String tripId,
        String decision,
        String settlementStatus,
        Integer approvedDistanceMeters,
        Integer growthPerMember,
        Integer memberCount,
        Integer totalGrowth,
        Boolean duplicate,
        String reviewedAt
) {
}
