package com.tongluxing.trip.support;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

/** 路线折线传输辅助：数据库保留完整路线，普通页面只下发等距采样后的预览路线。 */
public final class RoutePolylineUtils {
    private RoutePolylineUtils() {
    }

    /**
     * 将 JSON 数组折线等距采样到最多 maxPoints 个节点；解析失败时返回原值，避免破坏兼容数据。
     */
    public static String simplify(ObjectMapper objectMapper, String polyline, int maxPoints) {
        if (polyline == null || polyline.isBlank() || maxPoints < 2) return polyline == null ? "" : polyline;
        try {
            JsonNode root = objectMapper.readTree(polyline);
            if (!root.isArray() || root.size() <= maxPoints) return polyline;
            ArrayNode source = (ArrayNode) root;
            ArrayNode target = objectMapper.createArrayNode();
            // 保证首尾节点保留，并使中间节点尽可能均匀。
            for (int i = 0; i < maxPoints; i++) {
                int index = (int) Math.round((double) i * (source.size() - 1) / (maxPoints - 1));
                target.add(source.get(index));
            }
            return objectMapper.writeValueAsString(target);
        } catch (JsonProcessingException ignored) {
            return polyline;
        }
    }

    /** 返回采样后的节点，供服务内部需要列表时复用。 */
    public static List<JsonNode> sampledNodes(ObjectMapper objectMapper, String polyline, int maxPoints) {
        String value = simplify(objectMapper, polyline, maxPoints);
        if (value == null || value.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(value);
            if (!root.isArray()) return List.of();
            List<JsonNode> result = new ArrayList<>(root.size());
            root.forEach(result::add);
            return result;
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }
}
