package com.tongdao.trip.service.impl;

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
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.trip.dto.CreateTripRequest;
import com.tongdao.trip.dto.LocationRequest;
import com.tongdao.trip.dto.UpdateTripRequest;
import com.tongdao.trip.dto.WaypointLocationRequest;
import com.tongdao.trip.entity.Trip;
import com.tongdao.trip.entity.TripMemberSnapshot;
import com.tongdao.trip.mapper.TripAuditLogMapper;
import com.tongdao.trip.mapper.TripMapper;
import com.tongdao.trip.mapper.TripMemberSnapshotMapper;
import com.tongdao.trip.mapper.TripWaypointMapper;
import com.tongdao.trip.service.TripService;
import com.tongdao.trip.vo.TripListResponse;
import com.tongdao.trip.vo.TripMemberSnapshotResponse;
import com.tongdao.trip.vo.TripResponse;
import com.tongdao.trip.vo.LocationResponse;
import com.tongdao.trip.vo.WaypointLocationResponse;
import com.tongdao.user.model.UserModels.UserProfileVO;
import com.tongdao.user.service.UserDomainEventService;
import com.tongdao.user.service.UserService;
import com.tongdao.user.support.CurrentUserContext;
import com.tongdao.vehicle.entity.VehicleProfile;
import com.tongdao.vehicle.mapper.VehicleProfileMapper;

import lombok.RequiredArgsConstructor;

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
    private final VehicleProfileMapper vehicleProfileMapper;
    private final UserService userService;
    private final UserDomainEventService userDomainEventService;

    @Override
    @Transactional
    public TripResponse createTrip(CreateTripRequest request) {
        Long userId = currentUserContext.requireUserId();
        checkRateLimit(PUBLISH_RL_KEY.formatted(userId), PUBLISH_LIMIT, Duration.ofHours(1), "行程发布太频繁，请稍后再试");
        validateRequest(request.startLocation(), request.endLocation(), request.travelDepth(), request.waypoints());
        VehicleProfile vehicle = requireCertifiedVehicle(request.vehicleId(), userId);

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

    @Override
    @Transactional
    public TripResponse endTrip(Long tripId) {
        TripResponse response = changeStatus(tripId, STATUS_ENDED, "END", "结束行程");
        memberMapper.findByTripId(tripId).stream()
                .filter(member -> List.of("OWNER", "APPROVED").contains(member.getJoinStatus()))
                .forEach(member -> userDomainEventService.handleTeamTripCompleted(
                        tripId, member.getUserId(), "trip-completed:" + tripId));
        return response;
    }

    @Override
    @Transactional
    public TripResponse cancelTrip(Long tripId) {
        return changeStatus(tripId, STATUS_CANCELLED, "CANCEL", "取消行程");
    }

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

    @Override
    public List<TripMemberSnapshotResponse> getMembers(Long tripId) {
        requireReadableTrip(tripId);
        return memberMapper.findByTripId(tripId).stream().map(this::toMemberResponse).toList();
    }

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

    private void insertOwnerSnapshot(Trip trip, VehicleProfile vehicle, LocalDateTime now) {
        UserProfileVO profile = userService.getCurrentProfile();
        TripMemberSnapshot member = new TripMemberSnapshot();
        member.setId(SnowflakeIdGenerator.nextId());
        member.setTripId(trip.getId());
        member.setUserId(trip.getUserId());
        member.setVehicleId(trip.getVehicleId());
        member.setMemberRole("OWNER");
        member.setJoinStatus("OWNER");
        member.setNicknameSnapshot(profile == null ? "同道车友" : profile.nickname());
        member.setVehicleSnapshot((vehicle.getBrand() + " " + vehicle.getModel()).trim());
        member.setJoinedAt(now);
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        memberMapper.insert(member);
    }

    private VehicleProfile requireCertifiedVehicle(Long vehicleId, Long userId) {
        VehicleProfile vehicle = vehicleProfileMapper.findByIdAndUserId(vehicleId, userId);
        if (vehicle == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "车辆不存在或不属于当前用户");
        }
        if (!CERT_APPROVED.equals(vehicle.getCertificationStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "车辆未认证，请先完成车辆认证");
        }
        return vehicle;
    }

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

    private void ensureMutable(Trip trip) {
        if (!STATUS_PUBLISHED.equals(trip.getStatus()) && !STATUS_ONGOING.equals(trip.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前状态不允许操作");
        }
    }

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

    private TripResponse buildResponse(Long tripId) {
        Trip trip = tripMapper.findById(tripId);
        if (trip == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        return toResponse(trip);
    }

    private TripResponse toResponse(Trip trip) {
        return toResponse(trip, readWaypoints(trip.getWaypointsJson()));
    }

    private TripResponse toResponseWithoutChildren(Trip trip) {
        return toResponse(trip, readWaypoints(trip.getWaypointsJson()));
    }

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

    private List<WaypointLocationRequest> sortWaypoints(List<WaypointLocationRequest> waypoints) {
        if (waypoints == null || waypoints.isEmpty()) {
            return List.of();
        }
        return waypoints.stream()
                .sorted(Comparator.comparing(waypoint -> waypoint.sortOrder() == null ? Integer.MAX_VALUE : waypoint.sortOrder()))
                .toList();
    }

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

    private void clearTripCaches(Long userId, Long tripId) {
        redisTemplate.delete(DETAIL_CACHE_KEY.formatted(tripId));
        clearListCaches(userId);
    }

    private void clearListCaches(Long userId) {
        redisTemplate.delete(MINE_CACHE_KEY.formatted(userId, "active"));
        redisTemplate.delete(MINE_CACHE_KEY.formatted(userId, "history"));
        redisTemplate.delete(PUBLIC_CACHE_KEY.formatted(20));
        redisTemplate.delete(PUBLIC_CACHE_KEY.formatted(50));
    }

    private void checkRateLimit(String key, int limit, Duration ttl, String message) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, ttl);
        }
        if (count != null && count > limit) {
            throw new BusinessException(message);
        }
    }

    private void insertAuditLog(Long tripId, Long userId, String operationType, Object before, Object after, String remark) {
        auditLogMapper.insert(SnowflakeIdGenerator.nextId(), tripId, userId, operationType, toJson(before), toJson(after), remark, LocalDateTime.now());
    }

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

    private void writeJson(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException ignored) {
            redisTemplate.delete(key);
        }
    }

    private LocalDateTime parseTime(String value) {
        String text = normalize(value);
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String displayName(String name, String address) {
        String normalizedName = normalize(name);
        if (StringUtils.hasText(normalizedName)) {
            return normalizedName;
        }
        return normalize(address);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
