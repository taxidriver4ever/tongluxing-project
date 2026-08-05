package com.tongluxing.match.vo;

/**
 * App「行程-推荐」列表卡片。
 *
 * <p>字段严格对应推荐页展示：行程名称、路线、日期、车辆容量、顺路率/热度、
 * 距离、队长评分以及卡片操作权限。距离同时返回米和公里，前端展示公里、排序
 * 与过滤统一使用米，避免浮点误差。</p>
 */
public record TripRecommendCardResponse(
        String tripId,
        String teamId,
        String tripName,
        String startLocation,
        String endLocation,
        String departureTime,
        Integer currentVehicleCount,
        Integer vehicleLimit,
        Integer matchRate,
        Integer heat,
        Integer distanceMeters,
        Double distance,
        Long timeGapMinutes,
        Double leaderRating,
        String ownerUserId,
        String ownerNickname,
        String ownerAvatarImageKey,
        String relationshipStatus,
        Boolean allowGreeting,
        Boolean allowApply,
        String status
) {
}
