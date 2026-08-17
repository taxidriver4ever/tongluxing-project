package com.tongluxing.trip.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tongluxing.trip.support.RoutePolylineUtils.Point;

class RoutePolylineUtilsTest {
    private static final double EPSILON_METERS = 10D;

    @Test
    void testStraightLine() {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < 500; i++) points.add(new Point(30D + i * 0.0001D, 120D + i * 0.0001D));

        List<Point> simplified = RoutePolylineUtils.simplifyRdp(points, EPSILON_METERS);

        assertEquals(2, simplified.size());
        assertEquals(points.get(0), simplified.get(0));
        assertEquals(points.get(points.size() - 1), simplified.get(1));
    }

    @Test
    void testCornerPreserved() {
        Point corner = new Point(30.01D, 120.01D);
        List<Point> points = List.of(
                new Point(30D, 120D), new Point(30.005D, 120.005D), corner,
                new Point(30.01D, 120.015D), new Point(30.01D, 120.02D));

        assertTrue(RoutePolylineUtils.simplifyRdp(points, 20D).contains(corner));
    }

    @Test
    void testMaxPoints() {
        List<Point> turns = zigzag(500);

        List<Point> result = RoutePolylineUtils.simplifyForMatching(turns, 0D, 60);

        assertEquals(60, result.size());
    }

    @Test
    void testKeepEndpoints() {
        List<Point> points = zigzag(200);

        List<Point> result = RoutePolylineUtils.simplifyForMatching(points, 0D, 60);

        assertEquals(points.get(0), result.get(0));
        assertEquals(points.get(points.size() - 1), result.get(result.size() - 1));
    }

    @Test
    void testSmallPolyline() {
        assertEquals(List.of(), RoutePolylineUtils.simplifyForMatching(List.of(), 10D, 60));
        Point only = new Point(30D, 120D);
        assertEquals(List.of(only), RoutePolylineUtils.simplifyForMatching(List.of(only), 10D, 60));
        List<Point> pair = List.of(only, new Point(31D, 121D));
        assertEquals(pair, RoutePolylineUtils.simplifyForMatching(pair, 10D, 60));
    }

    @Test
    void testDuplicatePointsAndClosedRoute() {
        Point endpoint = new Point(30D, 120D);
        List<Point> duplicates = new ArrayList<>();
        for (int i = 0; i < 100; i++) duplicates.add(endpoint);
        List<Point> duplicateResult = RoutePolylineUtils.simplifyForMatching(duplicates, 10D, 60);
        assertEquals(2, duplicateResult.size());
        assertEquals(endpoint, duplicateResult.get(0));
        assertEquals(endpoint, duplicateResult.get(1));

        List<Point> closed = List.of(endpoint, new Point(30.01D, 120D),
                new Point(30.01D, 120.01D), new Point(30D, 120.01D), endpoint);
        List<Point> closedResult = RoutePolylineUtils.simplifyForMatching(closed, 10D, 60);
        assertEquals(endpoint, closedResult.get(0));
        assertEquals(endpoint, closedResult.get(closedResult.size() - 1));
        assertTrue(closedResult.size() > 2);
    }

    private List<Point> zigzag(int size) {
        List<Point> points = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            points.add(new Point(30D + i * 0.0001D, 120D + (i % 2 == 0 ? 0D : 0.001D)));
        }
        return points;
    }
}
