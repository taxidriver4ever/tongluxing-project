package com.tongluxing.map.integration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.map.config.AmapWebServiceProperties;
import com.tongluxing.map.dto.LocationDto;

/** 高德 Web 服务驾车路线规划客户端。 */
@Component
public class AmapRouteClient {

    private static final String ROUTE_PATH = "/v5/direction/driving";

    private final AmapWebServiceProperties properties;
    private final RestClient restClient;

    public AmapRouteClient(AmapWebServiceProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create(properties.getBaseUrl());
    }

    /**
     * 请求高德驾车路线规划 2.0，并把首条推荐路径转换为系统统一路线格式。
     */
    public AmapRouteResult planDriving(
            LocationDto start, LocationDto end, List<LocationDto> waypoints) {
        if (!StringUtils.hasText(properties.getKey())) {
            throw new BusinessException(
                    ResultCode.INTERNAL_SERVER_ERROR,
                    "高德 Web 服务 Key 未配置，请设置 AMAP_WEB_SERVICE_KEY");
        }

        String waypointValue = waypoints == null || waypoints.isEmpty()
                ? null
                : waypoints.stream().map(this::coordinate).reduce((left, right) -> left + ";" + right).orElse(null);
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path(ROUTE_PATH)
                                .queryParam("key", properties.getKey())
                                .queryParam("origin", coordinate(start))
                                .queryParam("destination", coordinate(end))
                                .queryParam("strategy", properties.getDrivingStrategy())
                                .queryParam("count", 3)
                                .queryParam("show_fields", "cost,polyline");
                        if (StringUtils.hasText(waypointValue)) {
                            builder.queryParam("waypoints", waypointValue);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(JsonNode.class);
            return parseResponse(response, start, end);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new BusinessException(
                    502,
                    "高德路线规划服务暂不可用，请稍后重试",
                    exception);
        }
    }

    private AmapRouteResult parseResponse(JsonNode response, LocationDto start, LocationDto end) {
        if (response == null) {
            throw new BusinessException(502, "高德路线规划未返回数据");
        }
        String status = response.path("status").asText();
        String infocode = response.path("infocode").asText();
        if (!"1".equals(status) || !"10000".equals(infocode)) {
            String message = response.path("info").asText("路线规划失败");
            throw new BusinessException(ResultCode.BAD_REQUEST, "高德路线规划失败：" + message);
        }

        JsonNode paths = response.path("route").path("paths");
        if (!paths.isArray() || paths.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "高德未找到可用驾车路线");
        }
        // 高德推荐策略可能返回多条可驾驶路线。展示场景选择其中道路距离
        // 最短的一条，避免固定取第一条推荐路线造成不必要的绕行。
        JsonNode path = paths.get(0);
        int shortestDistance = safeDistance(path);
        for (int index = 1; index < paths.size(); index++) {
            JsonNode candidate = paths.get(index);
            int candidateDistance = safeDistance(candidate);
            if (candidateDistance < shortestDistance) {
                path = candidate;
                shortestDistance = candidateDistance;
            }
        }
        int distance = parsePositiveInt(path.path("distance").asText(), "路线距离");
        int duration = parsePositiveInt(path.path("cost").path("duration").asText(), "路线耗时");
        List<LocationDto> polyline = parsePolyline(path.path("steps"));
        ensureEndpoint(polyline, start, true);
        ensureEndpoint(polyline, end, false);
        if (polyline.size() < 2) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "高德路线几何数据为空");
        }
        return new AmapRouteResult(distance, duration, List.copyOf(polyline));
    }

    private int safeDistance(JsonNode path) {
        try {
            return new BigDecimal(path.path("distance").asText())
                    .setScale(0, RoundingMode.HALF_UP).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private List<LocationDto> parsePolyline(JsonNode steps) {
        List<LocationDto> points = new ArrayList<>();
        if (!steps.isArray()) {
            return points;
        }
        for (JsonNode step : steps) {
            String rawPolyline = step.path("polyline").asText();
            if (!StringUtils.hasText(rawPolyline)) {
                continue;
            }
            for (String rawPoint : rawPolyline.split(";")) {
                String[] coordinate = rawPoint.trim().split(",");
                if (coordinate.length != 2) {
                    continue;
                }
                try {
                    BigDecimal longitude = normalizeCoordinate(new BigDecimal(coordinate[0]));
                    BigDecimal latitude = normalizeCoordinate(new BigDecimal(coordinate[1]));
                    addIfDifferent(points, new LocationDto("", "", latitude, longitude));
                } catch (NumberFormatException ignored) {
                    // 忽略单个损坏坐标；若最终无有效路线，调用方会统一报错。
                }
            }
        }
        return points;
    }

    private void ensureEndpoint(List<LocationDto> points, LocationDto endpoint, boolean first) {
        LocationDto normalized = new LocationDto(
                endpoint.name(), endpoint.address(),
                normalizeCoordinate(endpoint.latitude()), normalizeCoordinate(endpoint.longitude()));
        if (points.isEmpty()) {
            points.add(normalized);
            return;
        }
        LocationDto current = first ? points.get(0) : points.get(points.size() - 1);
        if (sameCoordinate(current, normalized)) {
            if (first) {
                points.set(0, normalized);
            } else {
                points.set(points.size() - 1, normalized);
            }
            return;
        }
        if (first) {
            points.add(0, normalized);
        } else {
            points.add(normalized);
        }
    }

    private void addIfDifferent(List<LocationDto> points, LocationDto point) {
        if (points.isEmpty() || !sameCoordinate(points.get(points.size() - 1), point)) {
            points.add(point);
        }
    }

    private boolean sameCoordinate(LocationDto left, LocationDto right) {
        return left.latitude().compareTo(right.latitude()) == 0
                && left.longitude().compareTo(right.longitude()) == 0;
    }

    private BigDecimal normalizeCoordinate(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private String coordinate(LocationDto location) {
        return normalizeCoordinate(location.longitude()).toPlainString()
                + "," + normalizeCoordinate(location.latitude()).toPlainString();
    }

    private int parsePositiveInt(String value, String fieldName) {
        try {
            int result = new BigDecimal(value).setScale(0, RoundingMode.HALF_UP).intValueExact();
            if (result < 0) {
                throw new NumberFormatException();
            }
            return result;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new BusinessException(502, "高德返回的" + fieldName + "格式错误");
        }
    }

    /** 系统内部使用的高德路线规划结果。 */
    public record AmapRouteResult(int distanceMeters, int durationSeconds, List<LocationDto> polyline) {
    }
}
