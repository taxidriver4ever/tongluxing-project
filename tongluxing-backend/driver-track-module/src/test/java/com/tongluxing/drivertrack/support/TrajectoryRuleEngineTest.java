package com.tongluxing.drivertrack.support;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TrajectoryRuleEngineTest {

    @Test
    void subtractsBothAccuracyRadiiForConservativeSpeed() {
        assertEquals(230, TrajectoryRuleEngine.lowerBoundDistance(300, 30, 40));
        assertEquals(0, TrajectoryRuleEngine.lowerBoundDistance(50, 30, 40));
    }

    @Test
    void detectsFatalSpeedAtOneThousandKmhBoundary() {
        double speed = TrajectoryRuleEngine.speedKmh(2_778, 10);
        assertTrue(speed >= 1_000d);
    }

    @Test
    void detectsBothTeleportWindows() {
        assertTrue(TrajectoryRuleEngine.isTeleport(5, 300, 300, 600));
        assertTrue(TrajectoryRuleEngine.isTeleport(10, 600, 300, 600));
        assertFalse(TrajectoryRuleEngine.isTeleport(11, 2_000, 300, 600));
    }

    @Test
    void calculatesShortestBearingDifference() {
        assertEquals(20d, TrajectoryRuleEngine.angularDifferenceDegrees(350, 10));
        assertEquals(150d, TrajectoryRuleEngine.angularDifferenceDegrees(0, 150));
    }

    @Test
    void filtersStationaryGpsDrift() {
        assertEquals(0, TrajectoryRuleEngine.filterStationaryDrift(7, 2.9, 8, 3));
        assertEquals(8, TrajectoryRuleEngine.filterStationaryDrift(8, 2.9, 8, 3));
    }
}
