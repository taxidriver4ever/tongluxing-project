package com.tongluxing.trip.support;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tongluxing.trip.support.RoutePolylineUtils.Point;

/** 可重复的合成路线 A/B 基线；替换为生产脱敏路线后可沿用相同指标。 */
class RouteSimplificationAbTest {
    private static final int MAX_POINTS = 60;
    private static final double EPSILON_METERS = 50D;

    @Test
    void compareUniformSamplingWithRdp() {
        List<List<Point>> routes = routes();
        List<Long> uniformTimes = new ArrayList<>();
        List<Long> rdpTimes = new ArrayList<>();
        double uniformPoints = 0D;
        double rdpPoints = 0D;
        double scoreDifference = 0D;

        for (int i = 0; i < routes.size(); i++) {
            List<Point> source = routes.get(i);
            List<Point> target = routes.get((i + 1) % routes.size());
            long start = System.nanoTime();
            List<Point> uniformSource = RoutePolylineUtils.uniformSample(source, MAX_POINTS);
            List<Point> uniformTarget = RoutePolylineUtils.uniformSample(target, MAX_POINTS);
            int uniformScore = overlapRate(uniformSource, uniformTarget);
            uniformTimes.add(System.nanoTime() - start);

            start = System.nanoTime();
            List<Point> rdpSource = RoutePolylineUtils.simplifyForMatching(source, EPSILON_METERS, MAX_POINTS);
            List<Point> rdpTarget = RoutePolylineUtils.simplifyForMatching(target, EPSILON_METERS, MAX_POINTS);
            int rdpScore = overlapRate(rdpSource, rdpTarget);
            rdpTimes.add(System.nanoTime() - start);

            uniformPoints += uniformSource.size();
            rdpPoints += rdpSource.size();
            scoreDifference += Math.abs(uniformScore - rdpScore);
            assertTrue(rdpSource.size() <= MAX_POINTS);
        }

        Set<Integer> uniformTop = topN(routes, false, 5);
        Set<Integer> rdpTop = topN(routes, true, 5);
        Set<Integer> changed = new HashSet<>(uniformTop);
        changed.addAll(rdpTop);
        Set<Integer> common = new HashSet<>(uniformTop);
        common.retainAll(rdpTop);
        changed.removeAll(common);

        double originalAverage = routes.stream().mapToInt(List::size).average().orElse(0D);
        double uniformAverage = uniformPoints / routes.size();
        double rdpAverage = rdpPoints / routes.size();
        System.out.printf("A/B routes=%d originalAvgPoints=%.1f uniformAvgPoints=%.1f rdpAvgPoints=%.1f "
                        + "uniformAvgUs=%.1f uniformP95Us=%.1f rdpAvgUs=%.1f rdpP95Us=%.1f "
                        + "avgOverlapScoreDiff=%.2f top5SymmetricDiff=%d%n",
                routes.size(), originalAverage, uniformAverage, rdpAverage,
                averageMicros(uniformTimes), p95Micros(uniformTimes), averageMicros(rdpTimes), p95Micros(rdpTimes),
                scoreDifference / routes.size(), changed.size());

        assertTrue(rdpAverage <= uniformAverage);
    }

    private Set<Integer> topN(List<List<Point>> routes, boolean rdp, int n) {
        List<Point> reference = reduce(routes.get(0), rdp);
        List<Integer> indexes = new ArrayList<>();
        for (int i = 1; i < routes.size(); i++) indexes.add(i);
        indexes.sort(Comparator.comparingInt((Integer i) -> overlapRate(reference, reduce(routes.get(i), rdp))).reversed());
        return new HashSet<>(indexes.subList(0, Math.min(n, indexes.size())));
    }

    private List<Point> reduce(List<Point> route, boolean rdp) {
        return rdp ? RoutePolylineUtils.simplifyForMatching(route, EPSILON_METERS, MAX_POINTS)
                : RoutePolylineUtils.uniformSample(route, MAX_POINTS);
    }

    private int overlapRate(List<Point> source, List<Point> target) {
        int matched = 0;
        for (Point point : source) {
            double minimum = Double.POSITIVE_INFINITY;
            for (int i = 1; i < target.size(); i++) {
                minimum = Math.min(minimum,
                        RoutePolylineUtils.perpendicularDistance(point, target.get(i - 1), target.get(i)));
            }
            if (minimum <= 500D) matched++;
        }
        return source.isEmpty() ? 0 : (int) Math.round(matched * 100D / source.size());
    }

    private List<List<Point>> routes() {
        List<List<Point>> routes = new ArrayList<>();
        for (int route = 0; route < 40; route++) {
            int size = 180 + route * 7;
            List<Point> points = new ArrayList<>(size);
            double offset = (route % 8) * 0.0007D;
            for (int i = 0; i < size; i++) {
                double progress = i / (size - 1D);
                double curve = route % 4 == 0 ? 0D
                        : Math.sin(progress * Math.PI * (2 + route % 5)) * (0.001D + (route % 3) * 0.0005D);
                points.add(new Point(30D + progress * 0.15D + curve,
                        120D + progress * 0.2D + offset));
            }
            routes.add(points);
        }
        return routes;
    }

    private double averageMicros(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).average().orElse(0D) / 1_000D;
    }

    private double p95Micros(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get(Math.max(0, (int) Math.ceil(sorted.size() * 0.95D) - 1)) / 1_000D;
    }
}
