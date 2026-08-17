package com.tongluxing.match.service;

import java.util.List;

/** Redis 中的轻量推荐池，不包含头像、车辆、徽章或完整卡片。 */
public record RecommendationPool(
        String generationId, Boolean userHasTrip, String effectiveSort, String referenceTripId,
        List<RecommendationPoolItem> items
) {
    public RecommendationPool { items = items == null ? List.of() : List.copyOf(items); }

    public record RecommendationPoolItem(
            Long tripId, Integer matchRate, Integer heat, Integer distanceMeters,
            Long timeGapMinutes, Double leaderRating
    ) { }
}
