package com.tongluxing.trip.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.growth.integration.GrowthFacade;
import com.tongluxing.invite.integration.InviteFacade;
import com.tongluxing.trip.dto.CreateTripRequest;
import com.tongluxing.trip.dto.LocationRequest;
import com.tongluxing.trip.dto.UpdateTripRequest;
import com.tongluxing.trip.dto.WaypointLocationRequest;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.entity.TripMemberSnapshot;
import com.tongluxing.trip.integration.TripUserProfilePort;
import com.tongluxing.trip.integration.TripUserProfilePort.TripUserProfileDTO;
import com.tongluxing.trip.integration.TripVehiclePort;
import com.tongluxing.trip.integration.TripVehiclePort.TripVehicleDTO;
import com.tongluxing.trip.mapper.TripAuditLogMapper;
import com.tongluxing.trip.mapper.TripMapper;
import com.tongluxing.trip.mapper.TripMemberSnapshotMapper;
import com.tongluxing.trip.mapper.TripWaypointMapper;
import com.tongluxing.trip.service.TripService;
import com.tongluxing.trip.vo.TripListResponse;
import com.tongluxing.trip.vo.TripMemberSnapshotResponse;
import com.tongluxing.trip.vo.TripResponse;
import com.tongluxing.trip.vo.LocationResponse;
import com.tongluxing.trip.vo.WaypointLocationResponse;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 行程模块业务服务实现，负责行程发布、查询、编辑、状态流转、成员快照和缓存维护。
 */
@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private static final int PUBLISH_LIMIT = 10;
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_ONGOING = "ONGOING";
    private static final String STATUS_ENDED = "ENDED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String CERT_APPROVED = "APPROVED";

    private static final String DETAIL_CACHE_KEY = "trip:cache:detail:%d";
    private static final String MINE_CACHE_KEY = "trip:cache:mine:%d:%s";
    private static final String PUBLIC_CACHE_KEY = "trip:cache:public:list:%d";
    private static final String PUBLISH_RL_KEY = "trip:rl:publish:%d";

    private final CurrentUserContext currentUserContext;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TripMapper tripMapper;
    private final TripWaypointMapper waypointMapper;
    private final TripMemberSnapshotMapper memberMapper;
    private final TripAuditLogMapper auditLogMapper;
    private final TripVehiclePort vehiclePort;
    private final TripUserProfilePort userProfilePort;
    private final GrowthFacade growthFacade;
    private final InviteFacade inviteFacade;

    /**
     * 创建行程：校验发布频率、路线参数、车辆认证后写入行程和车主成员快照。
     */
    @Override
    @Transactional
    public TripResponse createTrip(CreateTripRequest request) {
        Long userId = currentUserContext.requireUserId();
        checkRateLimit(PUBLISH_RL_KEY.formatted(userId), PUBLISH_LIMIT, Duration.ofHours(1), "行程发布太频繁，请稍后再试");
        validateRequest(request.startLocation(), request.endLocation(), request.travelDepth(), request.waypoints());
        TripVehicleDTO vehicle = requireCertifiedVehicle(request.vehicleId(), userId);

        LocalDateTime now = LocalDateTime.now();
        Trip trip = new Trip();
        trip.setId(SnowflakeIdGenerator.nextId());
        trip.setUserId(userId);
        trip.setJoinedVehicleCount(1);
        trip.setStatus(STATUS_PUBLISHED);
        trip.setCreatedAt(now);
        trip.setUpdatedAt(now);
        trip.setDeleted(0);
        fillTrip(trip, request);
        tripMapper.insert(trip);
        insertOwnerSnapshot(trip, vehicle, now);
        insertAuditLog(trip.getId(), userId, "PUBLISH", null, trip, "发布行程");
        clearListCaches(userId);
        return buildResponse(trip.getId());
    }

    /**
     * 查询当前用户行程列表，并按 active/history 维度使用短期缓存。
     */
    @Override
    public TripListResponse getMyTrips(String scope) {
        Long userId = currentUserContext.requireUserId();
        String normalizedScope = "history".equalsIgnoreCase(scope) ? "history" : "active";
        String cacheKey = MINE_CACHE_KEY.formatted(userId, normalizedScope);
        TripListResponse cached = readJson(cacheKey, TripListResponse.class);
        if (cached != null) {
            return cached;
        }
        List<Trip> trips = "history".equals(normalizedScope)
                ? tripMapper.findHistoryByUserId(userId, 50)
                : tripMapper.findActiveByUserId(userId);
        TripListResponse response = new TripListResponse(trips.stream().map(this::toResponseWithoutChildren).toList());
        writeJson(cacheKey, response, Duration.ofMinutes(5));
        return response;
    }

    /**
     * 查询行程详情，公开行程或本人行程可读，并写入详情缓存。
     */
    @Override
    public TripResponse getTrip(Long tripId) {
        Trip trip = requireReadableTrip(tripId);
        String cacheKey = DETAIL_CACHE_KEY.formatted(tripId);
        TripResponse cached = readJson(cacheKey, TripResponse.class);
        if (cached != null) {
            return cached;
        }
        TripResponse response = toResponse(trip);
        writeJson(cacheKey, response, Duration.ofMinutes(20));
        return response;
    }

    /**
     * 编辑本人可变更状态下的行程，并记录变更审计日志。
     */
    @Override
    @Transactional
    public TripResponse updateTrip(Long tripId, UpdateTripRequest request) {
        Long userId = currentUserContext.requireUserId();
        validateRequest(request.startLocation(), request.endLocation(), request.travelDepth(), request.waypoints());
        requireCertifiedVehicle(request.vehicleId(), userId);
        Trip before = requireOwnerTrip(tripId, userId);
        ensureMutable(before);

        Trip trip = copyTrip(before);
        fillTrip(trip, request);
        trip.setUpdatedAt(LocalDateTime.now());
        tripMapper.update(trip);
        insertAuditLog(tripId, userId, "UPDATE", before, trip, "编辑行程");
        clearTripCaches(userId, tripId);
        return buildResponse(tripId);
    }

    /**
     * 结束行程，并对有效同行成员发放成长值和触发邀请首队完成事件。
     */
    @Override
    @Transactional
    public TripResponse endTrip(Long tripId) {
        TripResponse response = changeStatus(tripId, STATUS_ENDED, "END", "结束行程");
        memberMapper.findByTripId(tripId).stream()
                .filter(member -> List.of("OWNER", "APPROVED").contains(member.getJoinStatus()))
                .forEach(member -> handleTripCompleted(tripId, member.getUserId()));
        return response;
    }

    /**
     * 完成行程后的跨模块奖励和邀请进度处理。
     */
    private void handleTripCompleted(Long tripId, Long userId) {
        String bizId = "trip-completed:" + tripId;
        growthFacade.grant(userId, "TEAM_TRIP_COMPLETED", bizId + ":" + userId, 100, "完成有效同行");
        inviteFacade.completeFirstTeam(userId, tripId, bizId);
    }

    /**
     * 取消本人可变更状态下的行程。
     */
    @Override
    @Transactional
    public TripResponse cancelTrip(Long tripId) {
        return changeStatus(tripId, STATUS_CANCELLED, "CANCEL", "取消行程");
    }

    /**
     * 查询公开行程列表，限制最大返回数量并使用短期缓存。
     */
    @Override
    public TripListResponse getPublicTrips(Integer limit) {
        int size = limit == null ? 20 : Math.max(1, Math.min(limit, 50));
        String cacheKey = PUBLIC_CACHE_KEY.formatted(size);
        TripListResponse cached = readJson(cacheKey, TripListResponse.class);
        if (cached != null) {
            return cached;
        }
        TripListResponse response = new TripListResponse(tripMapper.findPublicTrips(size).stream().map(this::toResponseWithoutChildren).toList());
        writeJson(cacheKey, response, Duration.ofMinutes(3));
        return response;
    }

    /**
     * 查询行程成员快照。
     */
    @Override
    public List<TripMemberSnapshotResponse> getMembers(Long tripId) {
        requireReadableTrip(tripId);
        return memberMapper.findByTripId(tripId).stream().map(this::toMemberResponse).toList();
    }

    /**
     * 统一处理结束/取消等状态变更，并写入审计日志、清理缓存。
     */
    private TripResponse changeStatus(Long tripId, String status, String operation, String remark) {
        Long userId = currentUserContext.requireUserId();
        Trip before = requireOwnerTrip(tripId, userId);
        ensureMutable(before);
        int rows = tripMapper.updateStatus(tripId, userId, status, LocalDateTime.now());
        if (rows == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        Trip after = tripMapper.findById(tripId);
        insertAuditLog(tripId, userId, operation, before, after, remark);
        clearTripCaches(userId, tripId);
        return toResponse(after);
    }

    /**
     * 根据创建请求填充行程主表字段。
     */
    private void fillTrip(Trip trip, CreateTripRequest request) {
        trip.setVehicleId(request.vehicleId());
        fillLocations(trip, request.startLocation(), request.endLocation());
        trip.setRouteSummary(normalize(request.routeSummary()));
        trip.setRoutePolylineKey("");
        trip.setRouteDistance(request.routeDistance());
        trip.setRouteDuration(request.routeDuration());
        trip.setRoutePolyline(normalize(request.routePolyline()));
        trip.setWaypointsJson(toJson(sortWaypoints(request.waypoints())));
        trip.setDepartureTime(parseTime(request.departureTime()));
        trip.setEstimatedDays(request.estimatedDays());
        trip.setTotalDistanceMeters(request.routeDistance());
        trip.setMaxVehicleCount(request.maxVehicleCount());
        trip.setTravelDepth(normalize(request.travelDepth()));
        trip.setPublicFlag(Boolean.TRUE.equals(request.publicFlag()) ? 1 : 0);
        trip.setRemark(normalize(request.remark()));
    }

    /**
     * 根据更新请求填充行程主表字段。
     */
    private void fillTrip(Trip trip, UpdateTripRequest request) {
        trip.setVehicleId(request.vehicleId());
        fillLocations(trip, request.startLocation(), request.endLocation());
        trip.setRouteSummary(normalize(request.routeSummary()));
        trip.setRoutePolylineKey("");
        trip.setRouteDistance(request.routeDistance());
        trip.setRouteDuration(request.routeDuration());
        trip.setRoutePolyline(normalize(request.routePolyline()));
        trip.setWaypointsJson(toJson(sortWaypoints(request.waypoints())));
        trip.setDepartureTime(parseTime(request.departureTime()));
        trip.setEstimatedDays(request.estimatedDays());
        trip.setTotalDistanceMeters(request.routeDistance());
        trip.setMaxVehicleCount(request.maxVehicleCount());
        trip.setTravelDepth(normalize(request.travelDepth()));
        trip.setPublicFlag(Boolean.TRUE.equals(request.publicFlag()) ? 1 : 0);
        trip.setRemark(normalize(request.remark()));
    }

    /**
     * 填充起终点展示字段和标准位置字段。
     */
    private void fillLocations(Trip trip, LocationRequest startLocation, LocationRequest endLocation) {
        trip.setStartLocationName(normalize(startLocation.name()));
        trip.setStartLocationAddress(normalize(startLocation.address()));
        trip.setStartLatitude(startLocation.latitude());
        trip.setStartLongitude(startLocation.longitude());
        trip.setStartName(displayName(startLocation.name(), startLocation.address()));
        trip.setStartLat(startLocation.latitude());
        trip.setStartLng(startLocation.longitude());
        trip.setEndLocationName(normalize(endLocation.name()));
        trip.setEndLocationAddress(normalize(endLocation.address()));
        trip.setEndLatitude(endLocation.latitude());
        trip.setEndLongitude(endLocation.longitude());
        trip.setEndName(displayName(endLocation.name(), endLocation.address()));
        trip.setEndLat(endLocation.latitude());
        trip.setEndLng(endLocation.longitude());
    }

    /**
     * 发布行程时写入车主成员快照，保留昵称和车辆展示信息。
     */
    private void insertOwnerSnapshot(Trip trip, TripVehicleDTO vehicle, LocalDateTime now) {
        TripUserProfileDTO profile = userProfilePort.getCurrentProfile();
        TripMemberSnapshot member = new TripMemberSnapshot();
        member.setId(SnowflakeIdGenerator.nextId());
        member.setTripId(trip.getId());
        member.setUserId(trip.getUserId());
        member.setVehicleId(trip.getVehicleId());
        member.setMemberRole("OWNER");
        member.setJoinStatus("OWNER");
        member.setNicknameSnapshot(profile == null ? "同路行车友" : profile.nickname());
        member.setVehicleSnapshot((vehicle.brand() + " " + vehicle.model()).trim());
        member.setJoinedAt(now);
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        memberMapper.insert(member);
    }

    /**
     * 校验车辆属于当前用户且认证状态通过。
     */
    private TripVehicleDTO requireCertifiedVehicle(Long vehicleId, Long userId) {
        TripVehicleDTO vehicle = vehiclePort.getCertifiedVehicle(vehicleId, userId);
        if (vehicle == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "车辆不存在或不属于当前用户");
        }
        if (!CERT_APPROVED.equals(vehicle.certificationStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "车辆未认证，请先完成车辆认证");
        }
        return vehicle;
    }

    /**
     * 查询可读行程；本人行程或公开行程才允许查看。
     */
    private Trip requireReadableTrip(Long tripId) {
        Long userId = currentUserContext.requireUserId();
        Trip trip = tripMapper.findById(tripId);
        if (trip == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        if (!trip.getUserId().equals(userId) && trip.getPublicFlag() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该行程");
        }
        return trip;
    }

    /**
     * 查询本人拥有的行程，用于编辑、结束、取消等写操作。
     */
    private Trip requireOwnerTrip(Long tripId, Long userId) {
        Trip trip = tripMapper.findById(tripId);
        if (trip == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        if (!trip.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该行程");
        }
        return trip;
    }

    /**
     * 校验当前行程状态是否允许修改。
     */
    private void ensureMutable(Trip trip) {
        if (!STATUS_PUBLISHED.equals(trip.getStatus()) && !STATUS_ONGOING.equals(trip.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前状态不允许操作");
        }
    }

    /**
     * 校验起终点、同行深度和途经点等发布参数。
     */
    private void validateRequest(LocationRequest startLocation, LocationRequest endLocation, String travelDepth, List<WaypointLocationRequest> waypoints) {
        if (startLocation == null || endLocation == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "起点和终点必填");
        }
        if (startLocation.latitude() == null || startLocation.longitude() == null || endLocation.latitude() == null || endLocation.longitude() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "起点和终点经纬度必填");
        }
        if (!StringUtils.hasText(displayName(startLocation.name(), startLocation.address())) || !StringUtils.hasText(displayName(endLocation.name(), endLocation.address()))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "起点和终点名称必填");
        }
        if (displayName(startLocation.name(), startLocation.address()).equals(displayName(endLocation.name(), endLocation.address()))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "起点和终点不能相同");
        }
        if (!List.of("LIGHT", "MIDDLE", "DEEP").contains(normalize(travelDepth))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "同行深度不正确");
        }
        if (waypoints != null && waypoints.size() > 5) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "途经点最多 5 个");
        }
        if (waypoints != null) {
            for (WaypointLocationRequest waypoint : waypoints) {
                if (waypoint.latitude() == null || waypoint.longitude() == null) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "途经点经纬度必填");
                }
                if (!StringUtils.hasText(displayName(waypoint.name(), waypoint.address()))) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "途经点名称必填");
                }
            }
        }
    }

    /**
     * 重新读取行程并构建响应对象。
     */
    private TripResponse buildResponse(Long tripId) {
        Trip trip = tripMapper.findById(tripId);
        if (trip == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        return toResponse(trip);
    }

    /**
     * 构建带途经点的行程响应。
     */
    private TripResponse toResponse(Trip trip) {
        return toResponse(trip, readWaypoints(trip.getWaypointsJson()));
    }

    /**
     * 构建列表场景下的行程响应。
     */
    private TripResponse toResponseWithoutChildren(Trip trip) {
        return toResponse(trip, readWaypoints(trip.getWaypointsJson()));
    }

    /**
     * 将行程实体和途经点列表转换为接口响应对象。
     */
    private TripResponse toResponse(Trip trip, List<WaypointLocationResponse> waypoints) {
        return new TripResponse(
                String.valueOf(trip.getId()),
                String.valueOf(trip.getUserId()),
                String.valueOf(trip.getVehicleId()),
                trip.getStartName(),
                trip.getStartLat(),
                trip.getStartLng(),
                new LocationResponse(trip.getStartLocationName(), trip.getStartLocationAddress(), trip.getStartLatitude(), trip.getStartLongitude()),
                trip.getEndName(),
                trip.getEndLat(),
                trip.getEndLng(),
                new LocationResponse(trip.getEndLocationName(), trip.getEndLocationAddress(), trip.getEndLatitude(), trip.getEndLongitude()),
                trip.getRouteSummary(),
                trip.getRoutePolylineKey(),
                trip.getRouteDistance(),
                trip.getRouteDuration(),
                trip.getRoutePolyline(),
                formatTime(trip.getDepartureTime()),
                trip.getEstimatedDays(),
                trip.getTotalDistanceMeters(),
                trip.getMaxVehicleCount(),
                trip.getJoinedVehicleCount(),
                trip.getTravelDepth(),
                trip.getPublicFlag() != null && trip.getPublicFlag() == 1,
                trip.getStatus(),
                trip.getRemark(),
                waypoints,
                formatTime(trip.getCreatedAt()),
                formatTime(trip.getUpdatedAt())
        );
    }

    /**
     * 将成员快照实体转换为接口响应对象。
     */
    private TripMemberSnapshotResponse toMemberResponse(TripMemberSnapshot member) {
        return new TripMemberSnapshotResponse(
                String.valueOf(member.getUserId()),
                member.getVehicleId() == null ? "" : String.valueOf(member.getVehicleId()),
                member.getMemberRole(),
                member.getJoinStatus(),
                member.getNicknameSnapshot(),
                member.getVehicleSnapshot(),
                formatTime(member.getJoinedAt())
        );
    }

    /**
     * 复制行程实体，用于更新前后的审计对比。
     */
    private Trip copyTrip(Trip source) {
        Trip trip = new Trip();
        trip.setId(source.getId());
        trip.setUserId(source.getUserId());
        trip.setVehicleId(source.getVehicleId());
        trip.setStartName(source.getStartName());
        trip.setStartLat(source.getStartLat());
        trip.setStartLng(source.getStartLng());
        trip.setStartLocationName(source.getStartLocationName());
        trip.setStartLocationAddress(source.getStartLocationAddress());
        trip.setStartLatitude(source.getStartLatitude());
        trip.setStartLongitude(source.getStartLongitude());
        trip.setEndName(source.getEndName());
        trip.setEndLat(source.getEndLat());
        trip.setEndLng(source.getEndLng());
        trip.setEndLocationName(source.getEndLocationName());
        trip.setEndLocationAddress(source.getEndLocationAddress());
        trip.setEndLatitude(source.getEndLatitude());
        trip.setEndLongitude(source.getEndLongitude());
        trip.setRouteSummary(source.getRouteSummary());
        trip.setRoutePolylineKey(source.getRoutePolylineKey());
        trip.setRouteDistance(source.getRouteDistance());
        trip.setRouteDuration(source.getRouteDuration());
        trip.setRoutePolyline(source.getRoutePolyline());
        trip.setWaypointsJson(source.getWaypointsJson());
        trip.setDepartureTime(source.getDepartureTime());
        trip.setEstimatedDays(source.getEstimatedDays());
        trip.setTotalDistanceMeters(source.getTotalDistanceMeters());
        trip.setMaxVehicleCount(source.getMaxVehicleCount());
        trip.setJoinedVehicleCount(source.getJoinedVehicleCount());
        trip.setTravelDepth(source.getTravelDepth());
        trip.setPublicFlag(source.getPublicFlag());
        trip.setStatus(source.getStatus());
        trip.setRemark(source.getRemark());
        trip.setCreatedAt(source.getCreatedAt());
        trip.setUpdatedAt(source.getUpdatedAt());
        trip.setDeleted(source.getDeleted());
        return trip;
    }

    /**
     * 按 sortOrder 对途经点排序。
     */
    private List<WaypointLocationRequest> sortWaypoints(List<WaypointLocationRequest> waypoints) {
        if (waypoints == null || waypoints.isEmpty()) {
            return List.of();
        }
        return waypoints.stream()
                .sorted(Comparator.comparing(waypoint -> waypoint.sortOrder() == null ? Integer.MAX_VALUE : waypoint.sortOrder()))
                .toList();
    }

    /**
     * 从 JSON 中读取并排序途经点，解析失败时返回空列表。
     */
    private List<WaypointLocationResponse> readWaypoints(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<WaypointLocationResponse> waypoints = objectMapper.readValue(json, new TypeReference<List<WaypointLocationResponse>>() {
            });
            return waypoints.stream()
                    .sorted(Comparator.comparing(waypoint -> waypoint.sortOrder() == null ? Integer.MAX_VALUE : waypoint.sortOrder()))
                    .toList();
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    /**
     * 清理指定行程详情缓存和用户列表缓存。
     */
    private void clearTripCaches(Long userId, Long tripId) {
        redisTemplate.delete(DETAIL_CACHE_KEY.formatted(tripId));
        clearListCaches(userId);
    }

    /**
     * 清理当前用户行程列表和常用公开列表缓存。
     */
    private void clearListCaches(Long userId) {
        redisTemplate.delete(MINE_CACHE_KEY.formatted(userId, "active"));
        redisTemplate.delete(MINE_CACHE_KEY.formatted(userId, "history"));
        redisTemplate.delete(PUBLIC_CACHE_KEY.formatted(20));
        redisTemplate.delete(PUBLIC_CACHE_KEY.formatted(50));
    }

    /**
     * 使用 Redis 对行程发布频率做简单限流。
     */
    private void checkRateLimit(String key, int limit, Duration ttl, String message) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, ttl);
        }
        if (count != null && count > limit) {
            throw new BusinessException(message);
        }
    }

    /**
     * 写入行程操作审计日志。
     */
    private void insertAuditLog(Long tripId, Long userId, String operationType, Object before, Object after, String remark) {
        auditLogMapper.insert(SnowflakeIdGenerator.nextId(), tripId, userId, operationType, toJson(before), toJson(after), remark, LocalDateTime.now());
    }

    /**
     * 将对象序列化为 JSON，失败时返回空对象字符串。
     */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    /**
     * 从 Redis 读取 JSON 缓存；解析失败时删除脏缓存。
     */
    private <T> T readJson(String key, Class<T> clazz) {
        String json = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException exception) {
            redisTemplate.delete(key);
            return null;
        }
    }

    /**
     * 写入 JSON 缓存；序列化失败时清理对应缓存键。
     */
    private void writeJson(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException ignored) {
            redisTemplate.delete(key);
        }
    }

    /**
     * 解析出发时间，兼容 ISO 和 yyyy-MM-dd HH:mm:ss 两种格式。
     */
    private LocalDateTime parseTime(String value) {
        String text = normalize(value);
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    /**
     * 统一格式化时间字段。
     */
    private String formatTime(LocalDateTime value) {
        return value == null ? "" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 优先使用名称作为展示名，名称为空时回退到地址。
     */
    private String displayName(String name, String address) {
        String normalizedName = normalize(name);
        if (StringUtils.hasText(normalizedName)) {
            return normalizedName;
        }
        return normalize(address);
    }

    /**
     * 统一处理空字符串和前后空格。
     */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
