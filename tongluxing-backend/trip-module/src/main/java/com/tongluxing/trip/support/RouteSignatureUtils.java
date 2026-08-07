package com.tongluxing.trip.support;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** 根据真正影响路线的坐标和顺序生成稳定签名，避免无关草稿编辑误把路线标记为 STALE。 */
public final class RouteSignatureUtils {
    private RouteSignatureUtils() {
    }

    public record Node(BigDecimal latitude, BigDecimal longitude) { }

    public static String signature(Node start, Node end, List<Node> waypoints) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, start);
        canonical.append('|');
        if (waypoints != null) {
            for (Node waypoint : waypoints) {
                append(canonical, waypoint);
                canonical.append(';');
            }
        }
        canonical.append('|');
        append(canonical, end);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void append(StringBuilder builder, Node node) {
        if (node == null) {
            builder.append("null");
            return;
        }
        builder.append(normalize(node.latitude())).append(',').append(normalize(node.longitude()));
    }

    private static String normalize(BigDecimal value) {
        if (value == null) return "null";
        return value.setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
