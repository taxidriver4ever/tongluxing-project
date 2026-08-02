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
 * 高德 Web 服务 POI 搜索 2.0 客户端。
 *
 * <p>封装文本搜索与周边搜索的 HTTP 参数、分页、超时、业务状态校验和 JSON 解析。
 * 客户端只返回系统统一地点模型，上层不依赖高德原始 JSON 结构。</p>
 */
@Component
public class AmapPlaceSearchClient {

    /** 高德 POI 文本搜索 2.0 路径。 */
    private static final String PLACE_TEXT_PATH = "/v5/place/text";
    /** 高德 POI 周边搜索 2.0 路径。 */
    private static final String PLACE_AROUND_PATH = "/v5/place/around";
    /** 高德单页允许的最大 POI 数量。 */
    private static final int MAX_PAGE_SIZE = 25;

    /** 高德 Web 服务配置。 */
    private final AmapWebServiceProperties properties;
    /** 只面向高德 baseUrl 的同步 HTTP 客户端。 */
    private final RestClient restClient;

    /** 初始化带连接/读取超时的高德 POI 客户端。 */
    public AmapPlaceSearchClient(AmapWebServiceProperties properties) {
        this.properties = properties;
        // 连接超时限制建连卡顿，读取超时限制高德响应过慢占用请求线程。
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory).build();
    }

    /**
     * 按关键词分页搜索高德 POI，并批量转换为系统统一地点结构。
     *
     * <p>高德单页最多返回 25 条；调用方需要更多结果时自动请求后续页，
     * 但不会把第三方地点数据落入本地数据库。</p>
     */
    public List<LocationDto> search(String keyword, int limit) {
        // 在发起 HTTP 前检查 Key，配置错误应明确报服务端异常。
        requireConfiguredKey();
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (!StringUtils.hasText(normalizedKeyword)) {
            // 空关键词视为无搜索结果，不浪费第三方调用额度。
            return List.of();
        }
        if (normalizedKeyword.length() > 80) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "地点搜索关键词不能超过80个字符");
        }

        // 系统层最多返回 50 条，超过单页 25 条时内部自动翻页。
        int normalizedLimit = Math.max(1, Math.min(limit, 50));
        List<LocationDto> locations = new ArrayList<>(normalizedLimit);
        int pageNumber = 1;
        try {
            while (locations.size() < normalizedLimit) {
                // 最后一页只请求尚缺数量，避免获取后丢弃过多数据。
                int pageSize = Math.min(MAX_PAGE_SIZE, normalizedLimit - locations.size());
                JsonNode response = requestPage(normalizedKeyword, pageSize, pageNumber);
                PageResult page = parseResponse(response);
                locations.addAll(page.pois().stream().map(AmapPoi::location).toList());
                if (page.rawPoiCount() < pageSize) {
                    // 原始 POI 数少于 pageSize 说明已到最后一页。
                    break;
                }
                pageNumber++;
            }
        } catch (BusinessException exception) {
            // 高德业务错误保留已映射的错误码与提示。
            throw exception;
        } catch (RestClientException exception) {
            // DNS、建连、超时或 HTTP 协议失败统一映射为上游 502。
            throw new BusinessException(502, "高德地点搜索服务暂不可用，请稍后重试", exception);
        }
        return List.copyOf(locations.subList(0, Math.min(locations.size(), normalizedLimit)));
    }

    /** 按中心点和半径查询附近的真实高德 POI。 */
    public List<AmapPoi> searchNearby(
            BigDecimal latitude, BigDecimal longitude, int radiusMeters, int limit) {
        requireConfiguredKey();
        // 对数量和半径再次做防御性限制，不依赖上层必然校验。
        int normalizedLimit = Math.max(1, Math.min(limit, 50));
        int normalizedRadius = Math.max(1, Math.min(radiusMeters, 50_000));
        List<AmapPoi> pois = new ArrayList<>(normalizedLimit);
        int pageNumber = 1;
        try {
            while (pois.size() < normalizedLimit) {
                int pageSize = Math.min(MAX_PAGE_SIZE, normalizedLimit - pois.size());
                JsonNode response = requestAroundPage(
                        latitude, longitude, normalizedRadius, pageSize, pageNumber);
                PageResult page = parseResponse(response);
                pois.addAll(page.pois());
                if (page.rawPoiCount() < pageSize) {
                    break;
                }
                pageNumber++;
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new BusinessException(502, "高德周边地点搜索服务暂不可用，请稍后重试", exception);
        }
        return List.copyOf(pois.subList(0, Math.min(pois.size(), normalizedLimit)));
    }

    /** 请求一页文本 POI，Key 只作为服务端 query 参数传给高德。 */
    private JsonNode requestPage(String keyword, int pageSize, int pageNumber) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(PLACE_TEXT_PATH)
                        .queryParam("key", properties.getKey())
                        .queryParam("keywords", keyword)
                        .queryParam("page_size", pageSize)
                        .queryParam("page_num", pageNumber)
                        .queryParam("output", "json")
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    /** 请求一页周边 POI，坐标按高德要求使用“经度,纬度”。 */
    private JsonNode requestAroundPage(
            BigDecimal latitude, BigDecimal longitude, int radiusMeters,
            int pageSize, int pageNumber) {
        // 固定 6 位小数并使用 toPlainString，避免科学计数法被第三方拒绝。
        String coordinate = normalizeCoordinate(longitude).toPlainString()
                + "," + normalizeCoordinate(latitude).toPlainString();
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(PLACE_AROUND_PATH)
                        .queryParam("key", properties.getKey())
                        .queryParam("location", coordinate)
                        .queryParam("radius", radiusMeters)
                        .queryParam("sortrule", "distance")
                        .queryParam("page_size", pageSize)
                        .queryParam("page_num", pageNumber)
                        .queryParam("output", "json")
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    /** 校验高德公共状态字段并解析 POI 数组。 */
    private PageResult parseResponse(JsonNode response) {
        if (response == null) {
            throw new BusinessException(502, "高德地点搜索未返回数据");
        }
        String status = response.path("status").asText();
        String infocode = response.path("infocode").asText();
        if (!"1".equals(status) || !"10000".equals(infocode)) {
            // status=1 且 infocode=10000 才表示高德业务成功，HTTP 200 本身不足以判定成功。
            String message = response.path("info").asText("地点搜索失败");
            throw new BusinessException(ResultCode.BAD_REQUEST, "高德地点搜索失败：" + message);
        }

        JsonNode pois = response.path("pois");
        if (!pois.isArray()) {
            return new PageResult(List.of(), 0);
        }
        List<AmapPoi> locations = new ArrayList<>(pois.size());
        for (JsonNode poi : pois) {
            AmapPoi location = toLocation(poi);
            if (location != null) {
                // 单条 POI 缺名称/坐标或坐标损坏时跳过，不影响同页其他有效结果。
                locations.add(location);
            }
        }
        return new PageResult(List.copyOf(locations), pois.size());
    }

    /** 将单个高德 POI JSON 容错转换为系统坐标模型。 */
    private AmapPoi toLocation(JsonNode poi) {
        String name = scalarText(poi.path("name"));
        String rawLocation = scalarText(poi.path("location"));
        if (!StringUtils.hasText(name) || !StringUtils.hasText(rawLocation)) {
            return null;
        }
        String[] coordinates = rawLocation.split(",");
        if (coordinates.length != 2) {
            return null;
        }
        try {
            BigDecimal longitude = normalizeCoordinate(new BigDecimal(coordinates[0].trim()));
            BigDecimal latitude = normalizeCoordinate(new BigDecimal(coordinates[1].trim()));
            String address = scalarText(poi.path("address"));
            String id = scalarText(poi.path("id"));
            String typeCode = scalarText(poi.path("typecode"));
            return new AmapPoi(id, typeCode, new LocationDto(name, address, latitude, longitude));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** 只读取 JSON 标量节点，数组/对象异常结构按空串处理。 */
    private String scalarText(JsonNode node) {
        return node != null && node.isValueNode() ? node.asText("") : "";
    }

    /** 将坐标统一保留 6 位小数，精度约为 0.1 米量级。 */
    private BigDecimal normalizeCoordinate(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    /** 确保高德 Web Key 已由后端环境注入。 */
    private void requireConfiguredKey() {
        if (!StringUtils.hasText(properties.getKey())) {
            throw new BusinessException(
                    ResultCode.INTERNAL_SERVER_ERROR,
                    "高德 Web 服务 Key 未配置，请设置 AMAP_WEB_SERVICE_KEY");
        }
    }

    /**
     * 高德 POI 标识、分类和地点信息。
     *
     * @param id 高德 POI ID
     * @param typeCode 高德 POI 分类编码
     * @param location 系统统一地点坐标模型
     */
    public record AmapPoi(String id, String typeCode, LocationDto location) {
    }

    /** 单页 POI 解析结果；rawPoiCount 用于判断高德是否还有下一页。 */
    private record PageResult(List<AmapPoi> pois, int rawPoiCount) {
    }
}
