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

    public static int filterStationaryDrift(int rawDistanceMeters,
                                            int lowerBoundDistanceMeters,
                                            double calculatedSpeedKmh,
                                            double reportedSpeedMps,
                                            int previousAccuracyMeters,
                                            int currentAccuracyMeters,
                                            int driftMeters,
                                            int driftSpeedKmh,
                                            int stationaryMaxJumpMeters) {
        int rawDistance = Math.max(0, rawDistanceMeters);
        int lowerBoundDistance = Math.max(0, lowerBoundDistanceMeters);
        int accuracyEnvelope = Math.max(0, previousAccuracyMeters)
                + Math.max(0, currentAccuracyMeters)
                + Math.max(0, driftMeters);
        boolean reportedStationary = !Double.isFinite(reportedSpeedMps)
                || reportedSpeedMps <= 0.8d;
        boolean noReliableMovement = lowerBoundDistance <= Math.max(0, driftMeters)
                || calculatedSpeedKmh < Math.max(0, driftSpeedKmh);
        boolean insideAccuracyEnvelope = rawDistance <= accuracyEnvelope;
        boolean stationaryJump = reportedStationary
                && rawDistance <= Math.max(0, stationaryMaxJumpMeters);
        return reportedStationary && (noReliableMovement && insideAccuracyEnvelope
                || stationaryJump) ? 0 : rawDistance;
    }

    /** 保留旧签名，供已有调用和测试兼容。 */
    public static int filterStationaryDrift(int rawDistanceMeters, double speedKmh,
                                            int driftMeters, int driftSpeedKmh) {
        return rawDistanceMeters < driftMeters && speedKmh < driftSpeedKmh
                ? 0 : Math.max(0, rawDistanceMeters);
    }
}
