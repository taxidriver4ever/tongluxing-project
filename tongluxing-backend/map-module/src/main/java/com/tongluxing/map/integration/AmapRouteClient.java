package com.tongluxing.map.integration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.map.config.AmapWebServiceProperties;
import com.tongluxing.map.dto.LocationDto;

/**
 * 高德 Web 服务驾车路线规划客户端。
 *
 * <p>负责构造高德驾车路线 2.0 请求，从多条候选中选取道路距离最短的可用路线，
 * 解析 step polyline，去除相邻重复点，并校准起终点。</p>
 */
@Component
public class AmapRouteClient {

    /** 高德驾车路线规划 2.0 路径。 */
    private static final String ROUTE_PATH = "/v5/direction/driving";

    /** 高德 Web 服务配置。 */
    private final AmapWebServiceProperties properties;
    /** 只面向高德 baseUrl 的同步 HTTP 客户端。 */
    private final RestClient restClient;

    /** 初始化带 3 秒建连和 8 秒读取超时的路线客户端。 */
    public AmapRouteClient(AmapWebServiceProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory).build();
    }

    /**
     * 请求高德驾车路线规划 2.0，并把首条推荐路径转换为系统统一路线格式。
     */
    public AmapRouteResult planDriving(
            LocationDto start, LocationDto end, List<LocationDto> waypoints) {
        // Key 为后端必需配置，缺失时不发起无效第三方请求。
        if (!StringUtils.hasText(properties.getKey())) {
            throw new BusinessException(
                    ResultCode.INTERNAL_SERVER_ERROR,
                    "高德 Web 服务 Key 未配置，请设置 AMAP_WEB_SERVICE_KEY");
        }

        // 高德 waypoints 格式为多个“经度,纬度”使用分号连接；无途经点时不传该参数。
        String waypointValue = waypoints == null || waypoints.isEmpty()
                ? null
                : waypoints.stream().map(this::coordinate).reduce((left, right) -> left + ";" + right).orElse(null);
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> {
                        // count=3 请求多条候选，show_fields 显式要求时长和折线字段。
                        var builder = uriBuilder
                                .path(ROUTE_PATH)
                                .queryParam("key", properties.getKey())
                                .queryParam("origin", coordinate(start))
                                .queryParam("destination", coordinate(end))
                                .queryParam("strategy", properties.getDrivingStrategy())
                                .queryParam("count", 3)
                                .queryParam("show_fields", "cost,polyline");
                        if (StringUtils.hasText(waypointValue)) {
                            // 只在非空时加入，避免高德把空 waypoints 解析为错误参数。
                            builder.queryParam("waypoints", waypointValue);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(JsonNode.class);
            return parseResponse(response, start, end);
        } catch (BusinessException exception) {
            // 已解析的高德业务错误保持原业务提示。
            throw exception;
        } catch (RestClientException exception) {
            // HTTP 建连、读取超时和协议异常统一转为 502 上游服务异常。
            throw new BusinessException(
                    502,
                    "高德路线规划服务暂不可用，请稍后重试",
                    exception);
        }
    }

    /** 校验高德通用状态，选择最短候选路线并解析几何数据。 */
    private AmapRouteResult parseResponse(JsonNode response, LocationDto start, LocationDto end) {
        if (response == null) {
            throw new BusinessException(502, "高德路线规划未返回数据");
        }
        String status = response.path("status").asText();
        String infocode = response.path("infocode").asText();
        if (!"1".equals(status) || !"10000".equals(infocode)) {
            // 高德 HTTP 200 仍可能在 JSON 中返回业务失败，必须同时检查两个字段。
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
                // 距离无法解析的候选按 Integer.MAX_VALUE 处理，不会挤掉正常路线。
                path = candidate;
                shortestDistance = candidateDistance;
            }
        }
        // 距离和时长都转换为非负整数，单位分别是米和秒。
        int distance = parsePositiveInt(path.path("distance").asText(), "路线距离");
        int duration = parsePositiveInt(path.path("cost").path("duration").asText(), "路线耗时");
        List<LocationDto> polyline = parsePolyline(path.path("steps"));
        if (polyline.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "高德路线几何数据为空");
        }
        // 高德 step 折线可能与用户选点存在小误差，响应必须明确包含原始起终点。
        ensureEndpoint(polyline, start, true);
        ensureEndpoint(polyline, end, false);
        if (polyline.size() < 2) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "高德路线几何数据为空");
        }
        return new AmapRouteResult(distance, duration, List.copyOf(polyline));
    }

    /** 容错读取候选路线距离，损坏值返回最大整数以降低选中优先级。 */
    private int safeDistance(JsonNode path) {
        try {
            return new BigDecimal(path.path("distance").asText())
                    .setScale(0, RoundingMode.HALF_UP).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            return Integer.MAX_VALUE;
        }
    }

    /** 按 step 顺序解析分号分隔的道路折线，并过滤单点损坏数据。 */
    private List<LocationDto> parsePolyline(JsonNode steps) {
        List<LocationDto> points = new ArrayList<>();
        if (!steps.isArray()) {
            return points;
        }
        for (JsonNode step : steps) {
            // 各 step 对应一段道路，必须按高德原顺序串联。
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
                    // 路线几何点不需要 POI 名称/地址，并过滤段边界的连续重复点。
                    addIfDifferent(points, new LocationDto("", "", latitude, longitude));
                } catch (NumberFormatException ignored) {
                    // 忽略单个损坏坐标；若最终无有效路线，调用方会统一报错。
                }
            }
        }
        return points;
    }

    /** 将用户原始起点或终点补入折线，并保留其名称和地址。 */
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
            // 坐标已存在时替换对象，把高德空名称几何点换为用户选中地点。
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

    /** 仅当新点与当前最后一点坐标不同时追加。 */
    private void addIfDifferent(List<LocationDto> points, LocationDto point) {
        if (points.isEmpty() || !sameCoordinate(points.get(points.size() - 1), point)) {
            points.add(point);
        }
    }

    /** 使用 BigDecimal compareTo 比较坐标，忽略 1.0/1.000000 的 scale 差异。 */
    private boolean sameCoordinate(LocationDto left, LocationDto right) {
        return left.latitude().compareTo(right.latitude()) == 0
                && left.longitude().compareTo(right.longitude()) == 0;
    }

    /** 坐标统一四舍五入到 6 位小数。 */
    private BigDecimal normalizeCoordinate(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    /** 生成高德要求的“经度,纬度”坐标字符串。 */
    private String coordinate(LocationDto location) {
        return normalizeCoordinate(location.longitude()).toPlainString()
                + "," + normalizeCoordinate(location.latitude()).toPlainString();
    }

    /** 把高德数字字符串四舍五入为非负 int，超范围或格式错误统一报 502。 */
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

    /**
     * 系统内部使用的高德路线规划结果。
     *
     * @param distanceMeters 实际道路距离，单位米
     * @param durationSeconds 预计驾车时长，单位秒
     * @param polyline 按驾车顺序排列的完整道路折线点
     */
    public record AmapRouteResult(int distanceMeters, int durationSeconds, List<LocationDto> polyline) {
    }
}
