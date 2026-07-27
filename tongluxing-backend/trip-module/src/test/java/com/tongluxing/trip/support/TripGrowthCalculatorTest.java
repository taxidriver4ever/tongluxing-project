package com.tongluxing.trip.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class TripGrowthCalculatorTest {
    @Test
    void followsFiveKilometresPerPointRule() {
        assertEquals(0, TripGrowthCalculator.points(4_900));
        assertEquals(1, TripGrowthCalculator.points(5_000));
        assertEquals(1, TripGrowthCalculator.points(9_900));
        assertEquals(2, TripGrowthCalculator.points(10_000));
        assertEquals(5, TripGrowthCalculator.points(27_400));
    }
}
