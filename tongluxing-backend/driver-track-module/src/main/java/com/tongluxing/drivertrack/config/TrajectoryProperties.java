package com.tongluxing.drivertrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 轨迹风控阈值，统一配置，禁止散落在业务代码中。 */
@Component
@ConfigurationProperties(prefix = "trajectory")
public class TrajectoryProperties {
    private int movingSampleSeconds = 10;
    private int stationarySampleSeconds = 10;
    private int minSegmentSeconds = 2;
    private int normalSegmentMaxSeconds = 20;
    private int gapSegmentMaxSeconds = 60;
    private int normalAccuracyMeters = 30;
    private int acceptableAccuracyMeters = 80;
    private int lowConfidenceAccuracyMeters = 50;
    private int stationaryDriftMeters = 15;
    private int stationaryDriftSpeedKmh = 3;
    private int stationaryMaxJumpMeters = 250;
    private int waypointRadiusMeters = 100;
    private int waypointMinPoints = 2;
    private int waypointMinDurationSeconds = 10;
    private int waypointEvidenceWindowSeconds = 30;
    private int accelerationMaxSegmentSeconds = 10;
    private int destinationRadiusMeters = 1000;
    private int destinationMinPoints = 1;
    private int destinationMinDurationSeconds = 0;
    private int validPointRatioPercent = 70;
    private int criticalValidPointRatioPercent = 50;
    private int normalSpeedMaxKmh = 300;
    private int warningSpeedMaxKmh = 300;
    private int fatalSpeedKmh = 300;
    private double suspiciousAccelerationMps2 = 8;
    private double abnormalAccelerationMps2 = 12;
    private int teleportFiveSecondsMeters = 300;
    private int teleportTenSecondsMeters = 600;
    private int mediumRiskScore = 5;
    private int highRiskScore = 10;
    private int maxFutureLocationSeconds = 300;
    private double lowConfidenceMaxBearingChangeDegrees = 150;
    private int roundTripWindowSeconds = 60;
    private int roundTripFarDistanceMeters = 300;
    private int roundTripReturnRadiusMeters = 100;

    public int getMovingSampleSeconds() { return movingSampleSeconds; }
    public void setMovingSampleSeconds(int value) { this.movingSampleSeconds = value; }
    public int getStationarySampleSeconds() { return stationarySampleSeconds; }
    public void setStationarySampleSeconds(int value) { this.stationarySampleSeconds = value; }
    public int getMinSegmentSeconds() { return minSegmentSeconds; }
    public void setMinSegmentSeconds(int value) { this.minSegmentSeconds = value; }
    public int getNormalSegmentMaxSeconds() { return normalSegmentMaxSeconds; }
    public void setNormalSegmentMaxSeconds(int value) { this.normalSegmentMaxSeconds = value; }
    public int getGapSegmentMaxSeconds() { return gapSegmentMaxSeconds; }
    public void setGapSegmentMaxSeconds(int value) { this.gapSegmentMaxSeconds = value; }
    public int getNormalAccuracyMeters() { return normalAccuracyMeters; }
    public void setNormalAccuracyMeters(int value) { this.normalAccuracyMeters = value; }
    public int getAcceptableAccuracyMeters() { return acceptableAccuracyMeters; }
    public void setAcceptableAccuracyMeters(int value) { this.acceptableAccuracyMeters = value; }
    public int getLowConfidenceAccuracyMeters() { return lowConfidenceAccuracyMeters; }
    public void setLowConfidenceAccuracyMeters(int value) { this.lowConfidenceAccuracyMeters = value; }
    public int getStationaryDriftMeters() { return stationaryDriftMeters; }
    public void setStationaryDriftMeters(int value) { this.stationaryDriftMeters = value; }
    public int getStationaryDriftSpeedKmh() { return stationaryDriftSpeedKmh; }
    public void setStationaryDriftSpeedKmh(int value) { this.stationaryDriftSpeedKmh = value; }
    public int getStationaryMaxJumpMeters() { return stationaryMaxJumpMeters; }
    public void setStationaryMaxJumpMeters(int value) { this.stationaryMaxJumpMeters = value; }
    public int getWaypointRadiusMeters() { return waypointRadiusMeters; }
    public void setWaypointRadiusMeters(int value) { this.waypointRadiusMeters = value; }
    public int getWaypointMinPoints() { return waypointMinPoints; }
    public void setWaypointMinPoints(int value) { this.waypointMinPoints = value; }
    public int getWaypointMinDurationSeconds() { return waypointMinDurationSeconds; }
    public void setWaypointMinDurationSeconds(int value) { this.waypointMinDurationSeconds = value; }
    public int getWaypointEvidenceWindowSeconds() { return waypointEvidenceWindowSeconds; }
    public void setWaypointEvidenceWindowSeconds(int value) { this.waypointEvidenceWindowSeconds = value; }
    public int getAccelerationMaxSegmentSeconds() { return accelerationMaxSegmentSeconds; }
    public void setAccelerationMaxSegmentSeconds(int value) { this.accelerationMaxSegmentSeconds = value; }
    public int getDestinationRadiusMeters() { return destinationRadiusMeters; }
    public void setDestinationRadiusMeters(int value) { this.destinationRadiusMeters = value; }
    public int getDestinationMinPoints() { return destinationMinPoints; }
    public void setDestinationMinPoints(int value) { this.destinationMinPoints = value; }
    public int getDestinationMinDurationSeconds() { return destinationMinDurationSeconds; }
    public void setDestinationMinDurationSeconds(int value) { this.destinationMinDurationSeconds = value; }
    public int getValidPointRatioPercent() { return validPointRatioPercent; }
    public void setValidPointRatioPercent(int value) { this.validPointRatioPercent = value; }
    public int getCriticalValidPointRatioPercent() { return criticalValidPointRatioPercent; }
    public void setCriticalValidPointRatioPercent(int value) { this.criticalValidPointRatioPercent = value; }
    public int getNormalSpeedMaxKmh() { return normalSpeedMaxKmh; }
    public void setNormalSpeedMaxKmh(int value) { this.normalSpeedMaxKmh = value; }
    public int getWarningSpeedMaxKmh() { return warningSpeedMaxKmh; }
    public void setWarningSpeedMaxKmh(int value) { this.warningSpeedMaxKmh = value; }
    public int getFatalSpeedKmh() { return fatalSpeedKmh; }
    public void setFatalSpeedKmh(int value) { this.fatalSpeedKmh = value; }
    public double getSuspiciousAccelerationMps2() { return suspiciousAccelerationMps2; }
    public void setSuspiciousAccelerationMps2(double value) { this.suspiciousAccelerationMps2 = value; }
    public double getAbnormalAccelerationMps2() { return abnormalAccelerationMps2; }
    public void setAbnormalAccelerationMps2(double value) { this.abnormalAccelerationMps2 = value; }
    public int getTeleportFiveSecondsMeters() { return teleportFiveSecondsMeters; }
    public void setTeleportFiveSecondsMeters(int value) { this.teleportFiveSecondsMeters = value; }
    public int getTeleportTenSecondsMeters() { return teleportTenSecondsMeters; }
    public void setTeleportTenSecondsMeters(int value) { this.teleportTenSecondsMeters = value; }
    public int getMediumRiskScore() { return mediumRiskScore; }
    public void setMediumRiskScore(int value) { this.mediumRiskScore = value; }
    public int getHighRiskScore() { return highRiskScore; }
    public void setHighRiskScore(int value) { this.highRiskScore = value; }
    public int getMaxFutureLocationSeconds() { return maxFutureLocationSeconds; }
    public void setMaxFutureLocationSeconds(int value) { this.maxFutureLocationSeconds = value; }
    public double getLowConfidenceMaxBearingChangeDegrees() { return lowConfidenceMaxBearingChangeDegrees; }
    public void setLowConfidenceMaxBearingChangeDegrees(double value) { this.lowConfidenceMaxBearingChangeDegrees = value; }
    public int getRoundTripWindowSeconds() { return roundTripWindowSeconds; }
    public void setRoundTripWindowSeconds(int value) { this.roundTripWindowSeconds = value; }
    public int getRoundTripFarDistanceMeters() { return roundTripFarDistanceMeters; }
    public void setRoundTripFarDistanceMeters(int value) { this.roundTripFarDistanceMeters = value; }
    public int getRoundTripReturnRadiusMeters() { return roundTripReturnRadiusMeters; }
    public void setRoundTripReturnRadiusMeters(int value) { this.roundTripReturnRadiusMeters = value; }
}
