package com.tongluxing.drivertrack.support;

/** 纯计算轨迹规则，便于单元测试和服务端统一复用。 */
public final class TrajectoryRuleEngine {
    private TrajectoryRuleEngine() {
    }

    public static int lowerBoundDistance(int rawDistanceMeters,
                                         int previousAccuracyMeters,
                                         int currentAccuracyMeters) {
        return Math.max(0, rawDistanceMeters
                - Math.max(0, previousAccuracyMeters)
                - Math.max(0, currentAccuracyMeters));
    }

    public static double speedKmh(int lowerBoundDistanceMeters, long deltaSeconds) {
        if (deltaSeconds <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0, lowerBoundDistanceMeters) / (double) deltaSeconds * 3.6d;
    }

    public static boolean isTeleport(long deltaSeconds, int lowerBoundDistanceMeters,
                                     int fiveSecondMeters, int tenSecondMeters) {
        return deltaSeconds <= 5 && lowerBoundDistanceMeters >= fiveSecondMeters
                || deltaSeconds <= 10 && lowerBoundDistanceMeters >= tenSecondMeters;
    }

    public static double angularDifferenceDegrees(double first, double second) {
        double normalized = Math.abs(first - second) % 360d;
        return normalized > 180d ? 360d - normalized : normalized;
    }

    public static int filterStationaryDrift(int rawDistanceMeters, double speedKmh,
                                            int driftMeters, int driftSpeedKmh) {
        return rawDistanceMeters < driftMeters && speedKmh < driftSpeedKmh
                ? 0 : Math.max(0, rawDistanceMeters);
    }
}
