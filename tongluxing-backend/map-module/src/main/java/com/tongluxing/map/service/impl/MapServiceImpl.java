package com.tongluxing.map.service.impl;

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
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.map.dto.LocationDto;
import com.tongluxing.map.dto.RoutePlanRequest;
import com.tongluxing.map.entity.MapLocationSearchLog;
import com.tongluxing.map.entity.MapRoutePlan;
import com.tongluxing.map.integration.AmapPlaceSearchClient;
import com.tongluxing.map.integration.AmapPlaceSearchClient.AmapPoi;
import com.tongluxing.map.integration.AmapRouteClient;
import com.tongluxing.map.integration.AmapRouteClient.AmapRouteResult;
import com.tongluxing.map.mapper.MapLocationSearchLogMapper;
import com.tongluxing.map.mapper.MapRoutePlanMapper;
import com.tongluxing.map.service.MapService;
import com.tongluxing.map.vo.MapMarkerResponse;
import com.tongluxing.map.vo.LocationHistoryResponse;
import com.tongluxing.map.vo.LocationSearchResponse;
import com.tongluxing.map.vo.NearbyMapResponse;
import com.tongluxing.map.vo.RoutePlanResponse;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 地图业务服务实现。
 *
 * <p>路线规划通过高德 Web 服务完成，结果按路线点 hash 缓存到 MySQL，供草稿预览、
 * 发现详情、行程导航和偏航检测统一复用。</p>
 */
@Service
@RequiredArgsConstructor
public class MapServiceImpl implements MapService {

    /** 驾车路线缓存的服务商与选路策略版本标识。 */
    private static final String ROUTE_PROVIDER_TYPE = "AMAP_WEB_V5_DISTANCE_OPTIMIZED";
    /** POI 搜索日志中记录的服务商版本标识。 */
    private static final String PLACE_PROVIDER_TYPE = "AMAP_WEB_V5_POI";

    /** 路线规划 Mapper。 */
    private final MapRoutePlanMapper routePlanMapper;
    /** 地点搜索日志 Mapper。 */
    private final MapLocationSearchLogMapper searchLogMapper;
    /** 当前登录用户上下文。 */
    private final CurrentUserContext currentUserContext;
    /** 高德 POI 地点搜索客户端。 */
    private final AmapPlaceSearchClient amapPlaceSearchClient;
    /** 高德驾车路线规划客户端。 */
    private final AmapRouteClient amapRouteClient;
    /** JSON 工具，用于路线点和路线结果序列化。 */
    private final ObjectMapper objectMapper;

    /** 生成路线规划；相同路线点会复用缓存结果。 */
    @Override
    public RoutePlanResponse planRoute(RoutePlanRequest request) {
        // 路线记录保留发起用户，用户 ID 始终从认证上下文获取。
        Long userId = currentUserContext.requireUserId();

        // 将起点、途经点、终点合并为一个有序列表，路线 hash 和响应均使用该顺序。
        List<LocationDto> points = new ArrayList<>();
        points.add(validateLocation(request.startLocation()));
        if (request.waypoints() != null) {
            for (LocationDto waypoint : request.waypoints()) {
                // 每个途经点单独校验，任意一个坐标异常都不请求第三方服务。
                points.add(validateLocation(waypoint));
            }
        }
        points.add(validateLocation(request.endLocation()));

        // 序列化有序路线点并计算 SHA-256；顺序不同会产生不同路线键。
        String routePointsJson = toJson(points);
        String routeHash = sha256(routePointsJson);
        // 路线点完全一致且服务商一致时，直接复用历史规划结果。
        MapRoutePlan cached = routePlanMapper.findByHash(routeHash, ROUTE_PROVIDER_TYPE);
        if (cached != null) {
            // 缓存存在仍要验证结果 JSON 的距离、时长和折线，损坏时自动回源。
            RoutePlanResult cachedResult = readResult(cached.getRouteResultJson());
            if (cachedResult != null) {
                return toRouteResponse(cached, points, cachedResult);
            }
        }

        // 高德客户端单独接收起终点和中间途经点，因此从合并列表中切分。
        List<LocationDto> waypoints = points.size() <= 2
                ? List.of()
                : List.copyOf(points.subList(1, points.size() - 1));
        // 实际道路折线、距离和时长必须来自高德，不用直线距离伪造。
        AmapRouteResult amapResult = amapRouteClient.planDriving(
                points.get(0), points.get(points.size() - 1), waypoints);
        // routePolyline 存储为 JSON 字符串，便于行程导航、顺路率计算和前端绘制共用。
        String routePolyline = toJson(amapResult.polyline());
        String resultJson = toJson(new RoutePlanResult(
                amapResult.distanceMeters(), amapResult.durationSeconds(), routePolyline));
        LocalDateTime now = LocalDateTime.now();
        MapRoutePlan plan;
        if (cached == null) {
            // 首次规划创建新缓存记录，ID 在应用层生成。
            plan = new MapRoutePlan();
            plan.setId(SnowflakeIdGenerator.nextId());
            plan.setUserId(userId);
            plan.setRouteHash(routeHash);
            plan.setRoutePointsJson(routePointsJson);
            plan.setRouteResultJson(resultJson);
            plan.setProviderType(ROUTE_PROVIDER_TYPE);
            plan.setPlanStatus("SUCCESS");
            plan.setCreatedAt(now);
            plan.setUpdatedAt(now);
            routePlanMapper.insert(plan);
        } else {
            // hash 记录存在但结果损坏/不完整时，用本次高德成功结果覆盖修复。
            plan = cached;
            plan.setRouteResultJson(resultJson);
            plan.setPlanStatus("SUCCESS");
            plan.setErrorMessage(null);
            plan.setUpdatedAt(now);
            routePlanMapper.updateSuccess(plan);
        }
        // 返回本次内存结果，避免插入/更新后再查询数据库。
        return toRouteResponse(plan, points, new RoutePlanResult(
                amapResult.distanceMeters(), amapResult.durationSeconds(), routePolyline));
    }

    /** 解析地点并记录用户选择日志。 */
    @Override
    public LocationDto resolveLocation(LocationDto location) {
        // resolve 表示用户已在候选列表中选中地点，此时才写入历史记录。
        Long userId = currentUserContext.requireUserId();
        LocationDto normalized = validateLocation(location);

        // 日志同时保存展示名称、地址和坐标，历史列表不需要再请求高德。
        MapLocationSearchLog log = new MapLocationSearchLog();
        log.setId(SnowflakeIdGenerator.nextId());
        log.setUserId(userId);
        log.setKeyword(displayName(normalized));
        log.setSelectedName(normalized.name());
        log.setSelectedAddress(normalized.address());
        log.setSelectedLatitude(normalized.latitude());
        log.setSelectedLongitude(normalized.longitude());
        log.setScene("RESOLVE");
        log.setProviderType(PLACE_PROVIDER_TYPE);
        log.setCreatedAt(LocalDateTime.now());
        log.setUpdatedAt(log.getCreatedAt());
        // 同用户+同坐标已存在时只刷新内容与时间，使其回到最近列表顶部。
        if (searchLogMapper.touchExisting(log) == 0) {
            // 没有可复用记录时才插入，避免用户反复选同一地点产生大量重复行。
            searchLogMapper.insert(log);
        }
        return normalized;
    }

    /** 通过高德 POI 搜索 2.0 批量查询真实地点，不在本地维护地点目录。 */
    @Override
    public List<LocationSearchResponse> searchLocations(
            String keyword, Integer limit, BigDecimal latitude, BigDecimal longitude) {
        // limit 限制在 1~50，防止客户端请求过多高德分页。
        int normalizedLimit = normalizeLimit(limit);
        // 搜索结果不落库；仅在客户端提供当前坐标时附加直线距离。
        return amapPlaceSearchClient.search(keyword, normalizedLimit).stream()
                .map(location -> toSearchResponse(location, latitude, longitude))
                .toList();
    }

    /** 返回当前用户最近选择并成功落库的地点。 */
    @Override
    public List<LocationHistoryResponse> getLocationHistory(
            Integer limit, BigDecimal latitude, BigDecimal longitude) {
        Long userId = currentUserContext.requireUserId();
        // Mapper 只查询当前用户未删除记录，按最后更新时间倒序。
        return searchLogMapper.findRecent(userId, normalizeLimit(limit)).stream()
                .map(log -> toHistoryResponse(log, latitude, longitude))
                .toList();
    }

    /** 删除操作同时匹配 historyId 与当前 userId，避免越权删除。 */
    @Override
    public int deleteLocationHistory(Long historyId) {
        // 先拒绝 null/非正数，避免无意义 SQL 和参数类型边界问题。
        if (historyId == null || historyId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "搜索历史ID不合法");
        }
        // SQL 同时包含 historyId 与当前 userId，不先查再删，避免 TOCTOU 越权窗口。
        int affected = searchLogMapper.softDeleteByIdAndUser(
                historyId, currentUserContext.requireUserId());
        if (affected == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "搜索历史不存在或不属于当前用户");
        }
        return affected;
    }

    /** 清空操作只影响当前登录用户且保持幂等。 */
    @Override
    public int clearLocationHistory() {
        // 逻辑删除本身幂等：已清空时影响行数为 0，仍视为成功。
        return searchLogMapper.softDeleteAllByUser(currentUserContext.requireUserId());
    }

    /** 将搜索日志转为历史响应，并按需计算距当前位置的球面距离。 */
    private LocationHistoryResponse toHistoryResponse(
            MapLocationSearchLog log, BigDecimal latitude, BigDecimal longitude) {
        LocationDto location = new LocationDto(
                log.getSelectedName(), log.getSelectedAddress(),
                log.getSelectedLatitude(), log.getSelectedLongitude());
        Integer distanceMeters = null;
        if (latitude != null && longitude != null) {
            // 只有经纬度成对提供时才计算，避免用单个坐标生成误导数据。
            distanceMeters = haversineMeters(latitude, longitude, location);
        }
        return new LocationHistoryResponse(
                String.valueOf(log.getId()), location.name(), location.address(),
                location.latitude(), location.longitude(), distanceMeters);
    }

    /** 将高德统一地点模型转为搜索响应，可选附加距离。 */
    private LocationSearchResponse toSearchResponse(
            LocationDto location, BigDecimal latitude, BigDecimal longitude) {
        Integer distanceMeters = null;
        if (latitude != null && longitude != null) {
            distanceMeters = haversineMeters(latitude, longitude, location);
        }
        return new LocationSearchResponse(
                location.name(), location.address(), location.latitude(),
                location.longitude(), distanceMeters);
    }

    /** 将地点列表数量限制为 1~50，未传时默认 20。 */
    private int normalizeLimit(Integer limit) {
        return limit == null ? 20 : Math.max(1, Math.min(limit, 50));
    }

    /** 通过高德周边搜索返回指定范围内的真实 POI。 */
    @Override
    public NearbyMapResponse getNearby(String latitude, String longitude, Integer radiusMeters) {
        // Controller 保留字符串参数以兼容 URL 调用，Service 内统一转 BigDecimal 并校验范围。
        BigDecimal lat = parseDecimal(latitude, "纬度不能为空");
        BigDecimal lng = parseDecimal(longitude, "经度不能为空");
        validateCoordinate(lat, lng);
        // 未传搜索半径时默认 5km，最大 50km 与高德接口能力保持一致。
        int radius = radiusMeters == null ? 5000 : radiusMeters;
        if (radius < 1 || radius > 50_000) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "搜索半径必须在1到50000米之间");
        }
        // 附近点位固定最多返回 25 条，并保留高德按距离排序的顺序。
        List<MapMarkerResponse> markers = amapPlaceSearchClient
                .searchNearby(lat, lng, radius, 25)
                .stream()
                .map(this::toMarkerResponse)
                .toList();
        return new NearbyMapResponse(markers);
    }

    /** 将高德 POI 转为地图标记点；POI ID 缺失时生成稳定备用 ID。 */
    private MapMarkerResponse toMarkerResponse(AmapPoi poi) {
        LocationDto location = poi.location();
        String markerId = StringUtils.hasText(poi.id())
                ? poi.id()
                // 经度、纬度和名称共同参与 hash，同一点位在多次请求中 ID 稳定。
                : sha256(location.longitude() + "," + location.latitude() + "," + location.name());
        return new MapMarkerResponse(
                markerId, "AMAP_POI", location.name(), location.address(),
                location.latitude(), location.longitude());
    }

    /** 将路线规划实体转换为响应对象。 */
    private RoutePlanResponse toRouteResponse(
            MapRoutePlan plan, List<LocationDto> points, RoutePlanResult result) {
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
        // 坐标是路线规划的必需字段，名称与地址则至少需要一个用于展示。
        if (location == null || location.latitude() == null || location.longitude() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "地点经纬度不能为空");
        }
        if (!StringUtils.hasText(location.name()) && !StringUtils.hasText(location.address())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "地点名称或地址不能为空");
        }
        validateCoordinate(location.latitude(), location.longitude());
        return location;
    }

    /** 校验 WGS/GCJ 通用经纬度数值范围：纬度 ±90、经度 ±180。 */
    private void validateCoordinate(BigDecimal latitude, BigDecimal longitude) {
        if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "地点经纬度超出有效范围");
        }
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

    /** 使用 haversine 公式估算两个经纬度点之间的球面距离。 */
    private int haversineMeters(BigDecimal fromLatitude, BigDecimal fromLongitude, LocationDto to) {
        // 将角度转为弧度，避免直接对经纬度差做平面距离计算。
        double lat1 = Math.toRadians(fromLatitude.doubleValue());
        double lat2 = Math.toRadians(to.latitude().doubleValue());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(to.longitude().doubleValue() - fromLongitude.doubleValue());
        // haversine 公式中间量 a，可在短距离下保持较好数值稳定性。
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        // 使用地球平均半径 6,371,000m，结果四舍五入到整米。
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
            // JSON 是路线缓存键和结果的必需格式，失败时不能继续产生不可复用记录。
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "JSON序列化失败");
        }
    }

    /** 解析真实路线缓存；损坏缓存返回 null，由调用方重新请求高德。 */
    private RoutePlanResult readResult(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            RoutePlanResult result = objectMapper.readValue(json, RoutePlanResult.class);
            if (result.routeDistance() == null
                    || result.routeDuration() == null
                    || !StringUtils.hasText(result.routePolyline())
                    || "[]".equals(result.routePolyline().trim())) {
                // 旧版空折线缓存不能用于导航和顺路率，必须视为未命中。
                return null;
            }
            return result;
        } catch (JsonProcessingException exception) {
            // 缓存结构升级或内容损坏时静默回源，不向用户暴露序列化细节。
            return null;
        }
    }

    /** 生成路线点 JSON 的 SHA-256 哈希，用于路线缓存命中。 */
    private String sha256(String text) {
        try {
            // UTF-8 和小写十六进制输出保证不同平台上 hash 完全一致。
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "路线哈希生成失败");
        }
    }

    /**
     * 路线规划结果缓存结构。
     *
     * @param routeDistance 实际道路距离，单位米
     * @param routeDuration 高德估算驾车时长，单位秒
     * @param routePolyline 完整实际道路折线的 JSON 字符串
     */
    private record RoutePlanResult(Integer routeDistance, Integer routeDuration, String routePolyline) {
    }
}
