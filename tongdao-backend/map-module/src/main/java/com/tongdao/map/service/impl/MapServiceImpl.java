package com.tongdao.map.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.map.dto.LocationDto;
import com.tongdao.map.dto.RoutePlanRequest;
import com.tongdao.map.entity.MapLocationSearchLog;
import com.tongdao.map.entity.MapRoutePlan;
import com.tongdao.map.mapper.MapLocationSearchLogMapper;
import com.tongdao.map.mapper.MapRoutePlanMapper;
import com.tongdao.map.service.MapService;
import com.tongdao.map.vo.MapMarkerResponse;
import com.tongdao.map.vo.NearbyMapResponse;
import com.tongdao.map.vo.RoutePlanResponse;
import com.tongdao.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 地图业务服务实现。
 *
 * <p>当前使用 MOCK 服务商：路线距离通过经纬度球面距离估算，路线结果按路线点 hash 缓存到 MySQL。
 * 后续接入真实地图服务商时，可保留接口形态和缓存结构。</p>
 */
@Service
@RequiredArgsConstructor
public class MapServiceImpl implements MapService {

    /** 当前地图服务商标识。 */
    private static final String PROVIDER_TYPE = "MOCK";

    /** 路线规划 Mapper。 */
    private final MapRoutePlanMapper routePlanMapper;
    /** 地点搜索日志 Mapper。 */
    private final MapLocationSearchLogMapper searchLogMapper;
    /** 当前登录用户上下文。 */
    private final CurrentUserContext currentUserContext;
    /** JSON 工具，用于路线点和路线结果序列化。 */
    private final ObjectMapper objectMapper;

    /** 生成路线规划；相同路线点会复用缓存结果。 */
    @Override
    public RoutePlanResponse planRoute(RoutePlanRequest request) {
        Long userId = currentUserContext.requireUserId();
        List<LocationDto> points = new ArrayList<>();
        points.add(validateLocation(request.startLocation()));
        if (request.waypoints() != null) {
            for (LocationDto waypoint : request.waypoints()) {
                points.add(validateLocation(waypoint));
            }
        }
        points.add(validateLocation(request.endLocation()));

        String routePointsJson = toJson(points);
        String routeHash = sha256(routePointsJson);
        // 路线点完全一致且服务商一致时，直接复用历史规划结果。
        MapRoutePlan cached = routePlanMapper.findByHash(routeHash, PROVIDER_TYPE);
        if (cached != null) {
            return toRouteResponse(cached, points);
        }

        // MOCK 实现：按球面距离估算总里程，并用固定速度估算时长。
        int distance = estimateDistance(points);
        int duration = distance == 0 ? 0 : Math.max(1, distance / 800);
        String routePolyline = routePointsJson;
        String resultJson = toJson(new RoutePlanResult(distance, duration, routePolyline));
        LocalDateTime now = LocalDateTime.now();
        MapRoutePlan plan = new MapRoutePlan();
        plan.setId(SnowflakeIdGenerator.nextId());
        plan.setUserId(userId);
        plan.setRouteHash(routeHash);
        plan.setRoutePointsJson(routePointsJson);
        plan.setRouteResultJson(resultJson);
        plan.setProviderType(PROVIDER_TYPE);
        plan.setPlanStatus("SUCCESS");
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        routePlanMapper.insert(plan);
        return toRouteResponse(plan, points);
    }

    /** 解析地点并记录用户选择日志。 */
    @Override
    public LocationDto resolveLocation(LocationDto location) {
        Long userId = currentUserContext.requireUserId();
        LocationDto normalized = validateLocation(location);
        MapLocationSearchLog log = new MapLocationSearchLog();
        log.setId(SnowflakeIdGenerator.nextId());
        log.setUserId(userId);
        log.setKeyword(displayName(normalized));
        log.setSelectedName(normalized.name());
        log.setSelectedAddress(normalized.address());
        log.setSelectedLatitude(normalized.latitude());
        log.setSelectedLongitude(normalized.longitude());
        log.setScene("RESOLVE");
        log.setProviderType(PROVIDER_TYPE);
        log.setCreatedAt(LocalDateTime.now());
        log.setUpdatedAt(log.getCreatedAt());
        searchLogMapper.insert(log);
        return normalized;
    }

    /** 查询附近地图标记；当前返回当前位置模拟点。 */
    @Override
    public NearbyMapResponse getNearby(String latitude, String longitude, Integer radiusMeters) {
        BigDecimal lat = parseDecimal(latitude, "纬度不能为空");
        BigDecimal lng = parseDecimal(longitude, "经度不能为空");
        return new NearbyMapResponse(List.of(
                new MapMarkerResponse("mock-current", "CURRENT", "当前位置", radiusMeters + "米范围", lat, lng)
        ));
    }

    /** 将路线规划实体转换为响应对象。 */
    private RoutePlanResponse toRouteResponse(MapRoutePlan plan, List<LocationDto> points) {
        RoutePlanResult result = readResult(plan.getRouteResultJson());
        return new RoutePlanResponse(
                String.valueOf(plan.getId()),
                result.routeDistance(),
                result.routeDuration(),
                result.routePolyline(),
                points,
                plan.getProviderType(),
                plan.getPlanStatus()
        );
    }

    /** 校验地点经纬度和名称/地址是否可用。 */
    private LocationDto validateLocation(LocationDto location) {
        if (location == null || location.latitude() == null || location.longitude() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "地点经纬度不能为空");
        }
        if (!StringUtils.hasText(location.name()) && !StringUtils.hasText(location.address())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "地点名称或地址不能为空");
        }
        return location;
    }

    /** 将字符串经纬度解析为 BigDecimal。 */
    private BigDecimal parseDecimal(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, message);
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "经纬度格式错误");
        }
    }

    /** 估算路线所有相邻点之间的总距离，单位米。 */
    private int estimateDistance(List<LocationDto> points) {
        int total = 0;
        for (int i = 1; i < points.size(); i++) {
            total += haversineMeters(points.get(i - 1), points.get(i));
        }
        return total;
    }

    /** 使用 haversine 公式估算两个经纬度点之间的球面距离。 */
    private int haversineMeters(LocationDto from, LocationDto to) {
        double lat1 = Math.toRadians(from.latitude().doubleValue());
        double lat2 = Math.toRadians(to.latitude().doubleValue());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(to.longitude().doubleValue() - from.longitude().doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return (int) Math.round(6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    /** 获取地点展示名称，优先使用名称，其次使用地址。 */
    private String displayName(LocationDto location) {
        if (StringUtils.hasText(location.name())) {
            return location.name();
        }
        return location.address();
    }

    /** 将对象序列化为 JSON。 */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "JSON序列化失败");
        }
    }

    /** 解析路线规划结果；历史数据异常时返回空路线结果。 */
    private RoutePlanResult readResult(String json) {
        try {
            return objectMapper.readValue(json, RoutePlanResult.class);
        } catch (JsonProcessingException exception) {
            return new RoutePlanResult(0, 0, "[]");
        }
    }

    /** 生成路线点 JSON 的 SHA-256 哈希，用于路线缓存命中。 */
    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "路线哈希生成失败");
        }
    }

    /** 路线规划结果缓存结构。 */
    private record RoutePlanResult(Integer routeDistance, Integer routeDuration, String routePolyline) {
    }
}
