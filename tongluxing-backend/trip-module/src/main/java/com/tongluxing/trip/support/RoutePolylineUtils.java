package com.tongluxing.trip.support;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

/** 路线折线辅助：完整路线始终保留，预览与匹配只使用临时简化结果。 */
public final class RoutePolylineUtils {
    private static final double METERS_PER_LATITUDE_DEGREE = 110_540D;
    private static final double METERS_PER_LONGITUDE_DEGREE = 111_320D;

    private RoutePolylineUtils() { }

    /** 均匀采样 JSON 折线；仅用于页面预览以及 RDP 后仍超限时的兜底。 */
    public static String simplify(ObjectMapper objectMapper, String polyline, int maxPoints) {
        if (polyline == null || polyline.isBlank() || maxPoints < 2) return polyline == null ? "" : polyline;
        try {
            JsonNode root = objectMapper.readTree(polyline);
            if (!root.isArray() || root.size() <= maxPoints) return polyline;
            ArrayNode target = objectMapper.createArrayNode();
            for (JsonNode node : uniformSample(nodes(root), maxPoints)) target.add(node);
            return objectMapper.writeValueAsString(target);
        } catch (JsonProcessingException ignored) {
            return polyline;
        }
    }

    /**
     * 统一匹配入口：固定 epsilon 做 RDP，结果仍超限时再均匀采样。
     * 首尾节点始终保留，原始 JSON 以及数据库中的完整 polyline 不会被修改。
     */
    public static String simplifyForMatching(ObjectMapper objectMapper, String polyline,
                                             double epsilonMeters, int maxPoints) {
        if (polyline == null || polyline.isBlank()) return polyline == null ? "" : polyline;
        requireValidMaxPoints(maxPoints);
        try {
            JsonNode root = objectMapper.readTree(polyline);
            if (!root.isArray()) return polyline;
            List<Point> points = parsePoints(root);
            if (points.size() != root.size()) return simplify(objectMapper, polyline, maxPoints);
            List<Integer> indexes = simplifyRdpIndexes(points, epsilonMeters);
            List<Integer> selected = indexes.size() <= maxPoints ? indexes : uniformSample(indexes, maxPoints);
            ArrayNode target = objectMapper.createArrayNode();
            for (Integer index : selected) target.add(root.get(index));
            return objectMapper.writeValueAsString(target);
        } catch (JsonProcessingException ignored) {
            return polyline;
        }
    }

    /** Ramer-Douglas-Peucker 简化，epsilon 单位为米。 */
    public static List<Point> simplifyRdp(List<Point> points, double epsilonMeters) {
        if (points == null || points.isEmpty()) return List.of();
        if (points.size() <= 2) return List.copyOf(points);
        List<Integer> indexes = simplifyRdpIndexes(points, epsilonMeters);
        List<Point> result = new ArrayList<>(indexes.size());
        for (Integer index : indexes) result.add(points.get(index));
        return List.copyOf(result);
    }

    /** 固定 epsilon 做 RDP，必要时均匀压缩到 maxPoints。 */
    public static List<Point> simplifyForMatching(List<Point> points, double epsilonMeters, int maxPoints) {
        if (points == null || points.isEmpty()) return List.of();
        requireValidMaxPoints(maxPoints);
        List<Point> simplified = simplifyRdp(points, epsilonMeters);
        return simplified.size() <= maxPoints ? simplified : uniformSample(simplified, maxPoints);
    }

    /** 均匀按索引采样，始终保留首尾元素。 */
    public static <T> List<T> uniformSample(List<T> values, int maxPoints) {
        if (values == null || values.isEmpty()) return List.of();
        requireValidMaxPoints(maxPoints);
        if (values.size() <= maxPoints) return List.copyOf(values);
        List<T> result = new ArrayList<>(maxPoints);
        for (int i = 0; i < maxPoints; i++) {
            int index = (int) Math.round((double) i * (values.size() - 1) / (maxPoints - 1));
            result.add(values.get(index));
        }
        return List.copyOf(result);
    }

    /** 点到线段的局部等距投影距离，单位为米。 */
    public static double perpendicularDistance(Point point, Point start, Point end) {
        double referenceLatitude = Math.toRadians((start.latitude() + end.latitude() + point.latitude()) / 3D);
        double longitudeScale = METERS_PER_LONGITUDE_DEGREE * Math.cos(referenceLatitude);
        double sx = start.longitude() * longitudeScale;
        double sy = start.latitude() * METERS_PER_LATITUDE_DEGREE;
        double ex = end.longitude() * longitudeScale;
        double ey = end.latitude() * METERS_PER_LATITUDE_DEGREE;
        double px = point.longitude() * longitudeScale;
        double py = point.latitude() * METERS_PER_LATITUDE_DEGREE;
        double dx = ex - sx;
        double dy = ey - sy;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 1e-9D) return Math.hypot(px - sx, py - sy);
        double t = Math.max(0D, Math.min(1D, ((px - sx) * dx + (py - sy) * dy) / lengthSquared));
        return Math.hypot(px - (sx + t * dx), py - (sy + t * dy));
    }

    private static List<Integer> simplifyRdpIndexes(List<Point> points, double epsilonMeters) {
        int size = points.size();
        if (size == 0) return List.of();
        if (size <= 2) return size == 2 ? List.of(0, 1) : List.of(0);
        double epsilon = Math.max(0D, epsilonMeters);
        boolean[] keep = new boolean[size];
        keep[0] = true;
        keep[size - 1] = true;
        Deque<Segment> stack = new ArrayDeque<>();
        stack.push(new Segment(0, size - 1));
        while (!stack.isEmpty()) {
            Segment segment = stack.pop();
            double maxDistance = -1D;
            int maxIndex = -1;
            for (int i = segment.start() + 1; i < segment.end(); i++) {
                double distance = perpendicularDistance(points.get(i), points.get(segment.start()), points.get(segment.end()));
                if (distance > maxDistance) {
                    maxDistance = distance;
                    maxIndex = i;
                }
            }
            if (maxIndex >= 0 && maxDistance > epsilon) {
                keep[maxIndex] = true;
                stack.push(new Segment(segment.start(), maxIndex));
                stack.push(new Segment(maxIndex, segment.end()));
            }
        }
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < size; i++) if (keep[i]) indexes.add(i);
        return List.copyOf(indexes);
    }

    private static List<Point> parsePoints(JsonNode root) {
        List<Point> points = new ArrayList<>(root.size());
        for (JsonNode node : root) {
            double latitude = node.has("latitude") ? node.path("latitude").asDouble(Double.NaN)
                    : node.path("lat").asDouble(Double.NaN);
            double longitude = node.has("longitude") ? node.path("longitude").asDouble(Double.NaN)
                    : node.path("lng").asDouble(Double.NaN);
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) return List.of();
            points.add(new Point(latitude, longitude));
        }
        return points;
    }

    private static List<JsonNode> nodes(JsonNode root) {
        List<JsonNode> result = new ArrayList<>(root.size());
        root.forEach(result::add);
        return result;
    }

    private static void requireValidMaxPoints(int maxPoints) {
        if (maxPoints < 2) throw new IllegalArgumentException("maxPoints must be at least 2");
    }

    public record Point(double latitude, double longitude) { }
    private record Segment(int start, int end) { }
}
