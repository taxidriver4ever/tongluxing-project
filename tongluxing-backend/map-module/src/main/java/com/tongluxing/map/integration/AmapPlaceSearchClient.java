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

/** 高德 Web 服务 POI 搜索 2.0 客户端。 */
@Component
public class AmapPlaceSearchClient {

    private static final String PLACE_TEXT_PATH = "/v5/place/text";
    private static final String PLACE_AROUND_PATH = "/v5/place/around";
    private static final int MAX_PAGE_SIZE = 25;

    private final AmapWebServiceProperties properties;
    private final RestClient restClient;

    public AmapPlaceSearchClient(AmapWebServiceProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create(properties.getBaseUrl());
    }

    /**
     * 按关键词分页搜索高德 POI，并批量转换为系统统一地点结构。
     *
     * <p>高德单页最多返回 25 条；调用方需要更多结果时自动请求后续页，
     * 但不会把第三方地点数据落入本地数据库。</p>
     */
    public List<LocationDto> search(String keyword, int limit) {
        requireConfiguredKey();
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (!StringUtils.hasText(normalizedKeyword)) {
            return List.of();
        }
        if (normalizedKeyword.length() > 80) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "地点搜索关键词不能超过80个字符");
        }

        int normalizedLimit = Math.max(1, Math.min(limit, 50));
        List<LocationDto> locations = new ArrayList<>(normalizedLimit);
        int pageNumber = 1;
        try {
            while (locations.size() < normalizedLimit) {
                int pageSize = Math.min(MAX_PAGE_SIZE, normalizedLimit - locations.size());
                JsonNode response = requestPage(normalizedKeyword, pageSize, pageNumber);
                PageResult page = parseResponse(response);
                locations.addAll(page.pois().stream().map(AmapPoi::location).toList());
                if (page.rawPoiCount() < pageSize) {
                    break;
                }
                pageNumber++;
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new BusinessException(502, "高德地点搜索服务暂不可用，请稍后重试", exception);
        }
        return List.copyOf(locations.subList(0, Math.min(locations.size(), normalizedLimit)));
    }

    /** 按中心点和半径查询附近的真实高德 POI。 */
    public List<AmapPoi> searchNearby(
            BigDecimal latitude, BigDecimal longitude, int radiusMeters, int limit) {
        requireConfiguredKey();
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

    private JsonNode requestAroundPage(
            BigDecimal latitude, BigDecimal longitude, int radiusMeters,
            int pageSize, int pageNumber) {
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

    private PageResult parseResponse(JsonNode response) {
        if (response == null) {
            throw new BusinessException(502, "高德地点搜索未返回数据");
        }
        String status = response.path("status").asText();
        String infocode = response.path("infocode").asText();
        if (!"1".equals(status) || !"10000".equals(infocode)) {
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
                locations.add(location);
            }
        }
        return new PageResult(List.copyOf(locations), pois.size());
    }

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

    private String scalarText(JsonNode node) {
        return node != null && node.isValueNode() ? node.asText("") : "";
    }

    private BigDecimal normalizeCoordinate(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private void requireConfiguredKey() {
        if (!StringUtils.hasText(properties.getKey())) {
            throw new BusinessException(
                    ResultCode.INTERNAL_SERVER_ERROR,
                    "高德 Web 服务 Key 未配置，请设置 AMAP_WEB_SERVICE_KEY");
        }
    }

    /** 高德 POI 标识、分类和地点信息。 */
    public record AmapPoi(String id, String typeCode, LocationDto location) {
    }

    private record PageResult(List<AmapPoi> pois, int rawPoiCount) {
    }
}
