package com.tongluxing.trip.support;

/** 行程成长值只按审核通过的有效里程计算。 */
public final class TripGrowthCalculator {
    public static final int DISTANCE_UNIT_METERS = 5_000;

    private TripGrowthCalculator() {
    }

    public static int points(int approvedDistanceMeters) {
        return Math.max(0, approvedDistanceMeters) / DISTANCE_UNIT_METERS;
    }
}
