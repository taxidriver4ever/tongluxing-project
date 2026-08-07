package com.tongluxing.trip.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class TripGrowthCalculatorTest {
    @Test
    void followsFiveKilometresTenPointsRule() {
        assertEquals(0, TripGrowthCalculator.points(4_900));
        assertEquals(10, TripGrowthCalculator.points(5_000));
        assertEquals(10, TripGrowthCalculator.points(9_900));
        assertEquals(20, TripGrowthCalculator.points(10_000));
        assertEquals(50, TripGrowthCalculator.points(27_400));
    }
}
