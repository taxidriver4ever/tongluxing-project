package com.tongluxing.trip.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.map.dto.LocationDto;
import com.tongluxing.map.dto.RoutePlanRequest;
import com.tongluxing.map.service.MapService;
import com.tongluxing.map.vo.RoutePlanResponse;
import com.tongluxing.trip.dto.CreateTripRequest;
import com.tongluxing.trip.dto.ContinueTripRequest;
import com.tongluxing.trip.dto.LocationRequest;
import com.tongluxing.trip.dto.UpdateTripRequest;
import com.tongluxing.trip.dto.TripTimeConflictRequest;
import com.tongluxing.trip.dto.WaypointLocationRequest;
import com.tongluxing.trip.config.RecommendationRouteProperties;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.entity.TripRoute;
import com.tongluxing.trip.entity.TripMemberSnapshot;
import com.tongluxing.trip.integration.TripParticipationPort;
import com.tongluxing.trip.integration.TripUserProfilePort;
import com.tongluxing.trip.integration.TripUserProfilePort.TripUserProfileDTO;
import com.tongluxing.trip.integration.TripVehiclePort;
import com.tongluxing.trip.integration.TripVehiclePort.TripVehicleDTO;
import com.tongluxing.trip.mapper.TripAuditLogMapper;
import com.tongluxing.trip.mapper.TripMapper;
import com.tongluxing.trip.mapper.TripMemberSnapshotMapper;
import com.tongluxing.trip.mapper.TripRouteMapper;
import com.tongluxing.trip.mapper.TripWaypointMapper;
import com.tongluxing.trip.mapper.TripExecutionSettlementMapper;
import com.tongluxing.trip.service.TripService;
import com.tongluxing.trip.service.TripFinishedEvent;
import com.tongluxing.trip.service.TripPublishedEvent;
import com.tongluxing.trip.service.TripStartedEvent;
import com.tongluxing.trip.support.RoutePolylineUtils;
import com.tongluxing.trip.support.RouteSignatureUtils;
import com.tongluxing.trip.support.RouteSignatureUtils.Node;
import com.tongluxing.trip.service.TripUpdatedEvent;
import com.tongluxing.trip.vo.ActiveTripStateResponse;
import com.tongluxing.trip.vo.ArrivalDecisionResponse;
import com.tongluxing.trip.vo.MyTripDashboardResponse;
import com.tongluxing.trip.vo.TripListResponse;
import com.tongluxing.trip.vo.TripMemberSnapshotResponse;
import com.tongluxing.trip.vo.TripResponse;
import com.tongluxing.trip.vo.TripRouteResponse;
import com.tongluxing.trip.vo.TripTimeConflictResponse;
import com.tongluxing.trip.vo.LocationResponse;
import com.tongluxing.trip.vo.WaypointLocationResponse;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 行程模块业务服务实现，负责行程发布、查询、编辑、状态流转、成员快照和缓存维护。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TripServiceImpl implements TripService {

    private static final int PUBLISH_LIMIT = 10;
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_CONFIRMING = "CONFIRMING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_LEGACY_ONGOING = "ONGOING";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String CERT_APPROVED = "APPROVED";
    private static final String TRIP_TYPE_DRIVER = "DRIVER_TRIP";
    private static final String TRIP_TYPE_PASSENGER = "PASSENGER_DEMAND";
    private static final String PUBLISHER_DRIVER = "DRIVER";
    private static final String PUBLISHER_PASSENGER = "PASSENGER";

    private static final String DETAIL_CACHE_KEY = "trip:cache:v2:detail:%d";
    private static final String MINE_CACHE_KEY = "trip:cache:mine:%d:%s";
    private static final String PUBLIC_CACHE_KEY = "trip:cache:v2:public:list:%d:%d";
    /** 升级前公开列表缓存不区分观察者，清理时仍需兼容删除。 */
    private static final String LEGACY_PUBLIC_CACHE_KEY = "trip:cache:public:list:%d";
    private static final String PUBLIC_CACHE_KEYS = "trip:cache:public:keys";
    private static final String PUBLISH_RL_KEY = "trip:rl:publish:%d";
    private static final String START_LOCK_KEY = "trip:lock:start:user:%d";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final CurrentUserContext currentUserContext;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TripMapper tripMapper;
    private final TripWaypointMapper waypointMapper;
    private final TripRouteMapper routeMapper;
    private final TripMemberSnapshotMapper memberMapper;
    private final TripAuditLogMapper auditLogMapper;
    private final TripExecutionSettlementMapper executionSettlementMapper;
    private final TripVehiclePort vehiclePort;
    private final TripParticipationPort participationPort;
    private final TripUserProfilePort userProfilePort;
    private final MapService mapService;
    private final ApplicationEventPublisher eventPublisher;
    private final RecommendationRouteProperties recommendationRouteProperties;

    /**
     * 创建行程：校验发布频率、路线参数、车辆认证后写入行程和车主成员快照。
     */
    @Override
    @Transactional
    public TripResponse createTrip(CreateTripRequest request) {
        return createTripInternal(request, null);
    }

    @Override
    @Transactional
    public TripResponse createTripFromDraft(CreateTripRequest request, Long draftId) {
        if (draftId == null) throw new BusinessException(ResultCode.BAD_REQUEST, "草稿 ID 不能为空");
        return createTripInternal(request, draftId);
    }

    private TripResponse createTripInternal(CreateTripRequest request, Long draftId) {
        Long userId = currentUserContext.requireUserId();
        checkRateLimit(PUBLISH_RL_KEY.formatted(userId), PUBLISH_LIMIT, Duration.ofHours(1), "行程发布太频繁，请稍后再试");
        validateRequest(request.startLocation(), request.endLocation(), request.travelDepth(), request.waypoints());
        TripVehicleDTO vehicle = resolvePublisherVehicle(request.vehicleId(), userId);
        boolean driverTrip = vehicle != null;

        LocalDateTime now = LocalDateTime.now();
        Trip trip = new Trip();
        trip.setId(SnowflakeIdGenerator.nextId());
        trip.setTripNumber(toTripNumber(trip.getId()));
        trip.setUserId(userId);
        trip.setVehicleId(driverTrip ? vehicle.vehicleId() : null);
        trip.setTripType(driverTrip ? TRIP_TYPE_DRIVER : TRIP_TYPE_PASSENGER);
        trip.setPublisherRole(driverTrip ? PUBLISHER_DRIVER : PUBLISHER_PASSENGER);
        trip.setCaptainUserId(userId);
        trip.setAutoStartEnabled(driverTrip && Boolean.TRUE.equals(request.autoStartEnabled()) ? 1 : 0);
        trip.setArrivalStatus("NOT_ARRIVED");
        trip.setContinueCount(0);
        trip.setJoinedVehicleCount(driverTrip ? 1 : 0);
        trip.setStatus(STATUS_PUBLISHED);
        trip.setCreatedAt(now);
        trip.setUpdatedAt(now);
        trip.setDeleted(0);
        fillTrip(trip, request);

        TripRoute route;
        if (draftId != null) {
            // 草稿发布只取轻量元数据；完整 polyline 留在数据库中并通过 UPDATE 直接提升。
            route = routeMapper.findMetaByDraftId(draftId);
            if (route == null || !"VALID".equals(route.getRouteStatus())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "草稿路线不存在或已失效，请重新规划");
            }
        } else {
            route = StringUtils.hasText(request.routePolyline())
                    && request.routeDistance() != null && request.routeDuration() != null
                    ? buildProvidedRouteSnapshot(trip.getId(), request.startLocation(), request.endLocation(),
                            request.waypoints(), request.routePolyline(), request.routeDistance(), request.routeDuration(), now)
                    : buildRouteSnapshot(trip.getId(), request.startLocation(), request.endLocation(), request.waypoints(), now);
        }
        applyRouteToTrip(trip, route);
        tripMapper.insert(trip);
        if (draftId != null) {
            if (routeMapper.promoteDraftRoute(draftId, trip.getId(), now) == 0) {
                throw new BusinessException(409, "草稿路线已被其他发布请求处理");
            }
        } else {
            routeMapper.insert(route);
        }
        insertPublisherSnapshot(trip, vehicle, now);
        insertAuditLog(trip.getId(), userId, "PUBLISH", null, trip, "发布行程");
        clearListCaches(userId);
        eventPublisher.publishEvent(new TripPublishedEvent(
                trip.getId(),
                StringUtils.hasText(trip.getTitle()) ? trip.getTitle() : "行程车队群",
                userId, trip.getVehicleId(), trip.getMaxVehicleCount(),
                trip.getPublicFlag() != null && trip.getPublicFlag() == 1, List.of(userId),
                trip.getTripType(), trip.getCaptainUserId()));
        return toResponse(trip);
    }

    /**
     * 查询当前用户行程列表，并按 active/history 维度使用短期缓存。
     */
    @Override
    public TripListResponse getMyTrips(String scope) {
        Long userId = currentUserContext.requireUserId();
        String normalizedScope = "history".equalsIgnoreCase(scope)
                ? "history"
                : "exited".equalsIgnoreCase(scope) ? "exited" : "active";
        if ("exited".equals(normalizedScope)) {
            return new TripListResponse(tripMapper.findExitedByUserId(userId, 50).stream()
                    .map(this::toResponseWithoutChildren)
                    .toList());
        }
        // “我的行程”必须反映数据库实时状态，不读取 Redis 列表缓存。
        // 这样数据库重置、取消发布或成员状态变化后不会继续显示旧行程。
        List<Trip> trips = "history".equals(normalizedScope)
                ? tripMapper.findHistoryByUserId(userId, 50)
                : tripMapper.findActiveByUserId(userId);
        return new TripListResponse(trips.stream().map(this::toResponseWithoutChildren).toList());
    }

    /**
     * 聚合 App「我的行程」首页数据。当前进行中的行程优先展示，
     * 其余待出发行程按出发时间升序，历史预览按出发时间倒序。
     */
    @Override
    public MyTripDashboardResponse getMyTripDashboard() {
        long started = System.nanoTime();
        Long userId = currentUserContext.requireUserId();
        List<Trip> active = tripMapper.findActiveByUserId(userId);
        List<Trip> history = tripMapper.findHistoryByUserId(userId, 12);
        long databaseFinished = System.nanoTime();
        TripResponse current = getCurrentDrivingTrip();
        TripResponse publishedCurrent = active.stream()
                .findFirst()
                .map(this::toResponseWithoutChildren)
                .orElse(null);
        Long joinedTripId = participationPort.findCurrentParticipatingTripId(userId);
        Trip joinedTrip = joinedTripId == null ? null : tripMapper.findById(joinedTripId);
        TripResponse joinedCurrent = joinedTrip == null ? null : toResponseWithoutChildren(joinedTrip);
        String currentId = current == null ? null : current.tripId();
        String publishedId = publishedCurrent == null ? null : publishedCurrent.tripId();
        String joinedId = joinedCurrent == null ? null : joinedCurrent.tripId();
        List<TripResponse> upcoming = active.stream()
                // P0：双当前行程已经固定置顶，不能再混入普通即将出发列表。
                .filter(trip -> {
                    String tripId = String.valueOf(trip.getId());
                    return !tripId.equals(currentId)
                            && !tripId.equals(publishedId)
                            && !tripId.equals(joinedId);
                })
                .limit(8)
                .map(this::toResponseWithoutChildren)
                .toList();
        List<TripResponse> recent = history.stream()
                .limit(6)
                .map(this::toResponseWithoutChildren)
                .toList();
        MyTripDashboardResponse response = new MyTripDashboardResponse(
                current, upcoming, recent, active.size(), history.size(), publishedCurrent, joinedCurrent);
        long finished = System.nanoTime();
        log.info("my_trip_dashboard_timing userId={} databaseMs={} assembleMs={} totalMs={}", userId,
                (databaseFinished - started) / 1_000_000L, (finished - databaseFinished) / 1_000_000L,
                (finished - started) / 1_000_000L);
        return response;
    }

    /** 查询当前用户拥有或参加的进行中行程状态。 */
    @Override
    public ActiveTripStateResponse getActiveTripState() {
        Long userId = currentUserContext.requireUserId();
        Long ownedTripId = tripMapper.findRunningTripIdByUserId(userId);
        if (ownedTripId != null) {
            return new ActiveTripStateResponse(true, String.valueOf(ownedTripId),
                    "你已有一个进行中的行程，同一时间只能进行一个行程");
        }
        Long participatingTripId = participationPort.findRunningParticipatingTripId(userId);
        if (participatingTripId != null) {
            return new ActiveTripStateResponse(true, String.valueOf(participatingTripId),
                    "你正在参加一个进行中的行程，同一时间只能参与一个进行中的行程");
        }
        return new ActiveTripStateResponse(false, null, "");
    }

    @Override
    public TripTimeConflictResponse checkTimeConflict(TripTimeConflictRequest request) {
        Long userId = currentUserContext.requireUserId();
        LocalDateTime startTime = parseTime(request.departureTime());
        int days = request.estimatedDays() == null ? 1 : Math.max(1, request.estimatedDays());
        LocalDateTime endTime = startTime.plusDays(days);
        Trip conflict = tripMapper.findTimeConflict(userId, request.excludeTripId(), startTime, endTime);
        if (conflict == null) {
            return new TripTimeConflictResponse(false, null, null, null, null, "");
        }
        LocalDateTime conflictEnd = conflict.getDepartureTime()
                .plusDays(Math.max(1, conflict.getEstimatedDays() == null ? 1 : conflict.getEstimatedDays()));
        return new TripTimeConflictResponse(
                true,
                String.valueOf(conflict.getId()),
                conflict.getTitle(),
                formatTime(conflict.getDepartureTime()),
                formatTime(conflictEnd),
                "该行程与您已发布的行程时间存在冲突，请调整出发时间"
        );
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

    @Override
    public TripRouteResponse getRoute(Long tripId) {
        Trip trip = requireReadableTrip(tripId);
        TripRoute route = routeMapper.findByTripId(tripId);
        if (route == null || !"VALID".equals(route.getRouteStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程路线不存在");
        }
        return new TripRouteResponse(String.valueOf(tripId),
                route.getRoutePlanId() == null ? null : String.valueOf(route.getRoutePlanId()),
                new LocationResponse(trip.getStartLocationName(), trip.getStartLocationAddress(),
                        trip.getStartLatitude(), trip.getStartLongitude()),
                new LocationResponse(trip.getEndLocationName(), trip.getEndLocationAddress(),
                        trip.getEndLatitude(), trip.getEndLongitude()),
                readWaypoints(trip.getWaypointsJson()), route.getPolyline(), route.getPlanDistance(),
                route.getPlanDuration(), route.getProviderType(), route.getRouteStatus());
    }

    /**
     * 编辑本人可变更状态下的行程，并记录变更审计日志。
     */
    @Override
    @Transactional
    public TripResponse updateTrip(Long tripId, UpdateTripRequest request) {
        Long userId = currentUserContext.requireUserId();
        validateRequest(request.startLocation(), request.endLocation(), request.travelDepth(), request.waypoints());
        Trip before = requireOwnerTrip(tripId, userId);
        TripVehicleDTO vehicle = null;
        if (TRIP_TYPE_DRIVER.equals(before.getTripType())) {
            vehicle = requireCertifiedVehicle(request.vehicleId() == null ? before.getVehicleId() : request.vehicleId(), userId);
        }
        ensureMutable(before);

        Trip trip = copyTrip(before);
        fillTrip(trip, request);
        if (TRIP_TYPE_DRIVER.equals(before.getTripType())) {
            trip.setVehicleId(vehicle.vehicleId());
        } else {
            trip.setVehicleId(null);
        }
        // 产品规则：行程创建者始终是当前行程队长，不能因为是否绑定车辆而丢失队长身份。
        trip.setCaptainUserId(userId);
        if (request.autoStartEnabled() != null && TRIP_TYPE_DRIVER.equals(before.getTripType())) {
            trip.setAutoStartEnabled(Boolean.TRUE.equals(request.autoStartEnabled()) ? 1 : 0);
        }
        boolean routeChanged = routeChanged(before, request);
        TripRoute route = routeChanged
                ? buildRouteSnapshot(trip.getId(), request.startLocation(), request.endLocation(), request.waypoints(), LocalDateTime.now())
                : routeMapper.findMetaByTripId(tripId);
        if (route != null) applyRouteToTrip(trip, route);
        trip.setUpdatedAt(LocalDateTime.now());
        tripMapper.update(trip);
        if (routeChanged && route != null) upsertRoute(route);
        insertAuditLog(tripId, userId, "UPDATE", before, trip, "编辑行程");
        clearTripCaches(userId, tripId);
        eventPublisher.publishEvent(new TripUpdatedEvent(tripId, userId));
        return buildResponse(tripId);
    }

    /** 开启行程：招募中、待出发或待确认均可进入 RUNNING，并按全部参与用户加锁。 */
    @Override
    @Transactional
    public TripResponse startTrip(Long tripId) {
        return startTrip(tripId, (List<Long>) null);
    }

    /** App 开启入口：定位误差过大或距离起点超过 5 公里时拒绝开启。 */
    @Override
    @Transactional
    public TripResponse startTrip(
            Long tripId, BigDecimal latitude, BigDecimal longitude, BigDecimal accuracy) {
        Long userId = currentUserContext.requireUserId();
        Trip trip = requireCaptainTrip(tripId, userId);
        BigDecimal startLatitude = trip.getStartLatitude() != null
                ? trip.getStartLatitude() : trip.getStartLat();
        BigDecimal startLongitude = trip.getStartLongitude() != null
                ? trip.getStartLongitude() : trip.getStartLng();
        if (startLatitude == null || startLongitude == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "行程起点缺少坐标，请先编辑并重新选择起点");
        }
        if (accuracy != null && accuracy.doubleValue() > 500D) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前定位精度较低，请到开阔位置后重试");
        }
        double distanceMeters = haversineMeters(
                latitude.doubleValue(), longitude.doubleValue(),
                startLatitude.doubleValue(), startLongitude.doubleValue());
        if (distanceMeters > 5_000D) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "你距离行程起点约 %.1f 公里，需进入 5 公里范围内才能开启行程"
                            .formatted(distanceMeters / 1_000D));
        }
        return startTrip(tripId, (List<Long>) null);
    }

    /** 行程确认卡入口只让明确确认的用户进入本次行程。 */
    @Override
    @Transactional
    public TripResponse startTrip(Long tripId, List<Long> confirmedParticipantUserIds) {
        Long userId = currentUserContext.requireUserId();
        Trip before = requireCaptainTrip(tripId, userId);
        if (!STATUS_PUBLISHED.equals(before.getStatus())
                && !STATUS_READY.equals(before.getStatus())
                && !STATUS_CONFIRMING.equals(before.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有招募中、待出发或待确认的行程可以开始");
        }

        Set<Long> confirmationFilter = confirmedParticipantUserIds == null
                ? null : new LinkedHashSet<>(confirmedParticipantUserIds);
        if (confirmationFilter != null) {
            confirmationFilter.add(userId);
            memberMapper.declineUnconfirmedMembers(
                    tripId, userId, List.copyOf(confirmationFilter), LocalDateTime.now());
        }

        LinkedHashSet<Long> participantIds = new LinkedHashSet<>();
        participantIds.add(userId);
        memberMapper.findByTripId(tripId).stream()
                .filter(member -> "OWNER".equals(member.getJoinStatus()) || "APPROVED".equals(member.getJoinStatus()))
                .map(TripMemberSnapshot::getUserId)
                .filter(memberId -> confirmationFilter == null || confirmationFilter.contains(memberId))
                .forEach(participantIds::add);
        participationPort.findActiveParticipantUserIds(tripId).stream()
                .filter(memberId -> confirmationFilter == null || confirmationFilter.contains(memberId))
                .forEach(participantIds::add);

        String lockValue = UUID.randomUUID().toString();
        List<String> acquiredLocks = acquireStartLocks(participantIds.stream().sorted().toList(), lockValue);
        boolean releaseInFinally = registerLockReleaseAfterTransaction(acquiredLocks, lockValue);
        try {
            for (Long participantId : participantIds) {
                Long ownedRunningTripId = tripMapper.findOtherRunningTripId(participantId, tripId);
                Long joinedRunningTripId = participationPort.findRunningParticipatingTripId(participantId);
                if (ownedRunningTripId != null
                        || (joinedRunningTripId != null && !tripId.equals(joinedRunningTripId))) {
                    String message = participantId.equals(userId)
                            ? "你已有其他进行中的行程，请先返回当前行程"
                            : "有成员正在参加其他进行中的行程，暂时不能开启当前行程";
                    throw new BusinessException(ResultCode.BUSINESS_ERROR, message);
                }
            }

            LocalDateTime now = LocalDateTime.now();
            int rows = tripMapper.startTrip(tripId, userId, now);
            if (rows == 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "当前状态不允许开始行程");
            }
            Trip after = tripMapper.findById(tripId);
            int plannedDistance = after.getTotalDistanceMeters() != null
                    ? after.getTotalDistanceMeters()
                    : after.getRouteDistance() == null ? 0 : after.getRouteDistance();
            executionSettlementMapper.createExecution(
                    SnowflakeIdGenerator.nextId(), tripId, userId, plannedDistance, now);
            Long executionId = executionSettlementMapper.executionId(tripId);
            for (Long participantId : participantIds) {
                executionSettlementMapper.createExecutionMember(
                        SnowflakeIdGenerator.nextId(), executionId, tripId, participantId,
                        participantId.equals(userId) ? "CAPTAIN" : "MEMBER", now);
            }
            insertAuditLog(tripId, userId, "START", before, after, "开始行程");
            List<Long> chatMemberIds = List.copyOf(participantIds);
            eventPublisher.publishEvent(new TripStartedEvent(
                    tripId,
                    StringUtils.hasText(after.getTitle()) ? after.getTitle() : "行程车队群",
                    userId,
                    chatMemberIds
            ));
            redisTemplate.delete(DETAIL_CACHE_KEY.formatted(tripId));
            participantIds.forEach(this::clearListCaches);
            return toResponse(after);
        } finally {
            if (releaseInFinally) {
                releaseStartLocks(acquiredLocks, lockValue);
            }
        }
    }

    /** 结束行程，只执行 RUNNING -> FINISHED；成长值由独立结算接口产生。 */
    @Override
    @Transactional
    public TripResponse endTrip(Long tripId) {
        Long userId = currentUserContext.requireUserId();
        Trip before = requireCaptainTrip(tripId, userId);
        // 结束接口需要支持幂等重试：如果 FINISHED 后自动结算网络瞬时失败，
        // 客户端再次点击结束时允许继续进入结算链路，而不是卡在“只能结束行驶中行程”。
        if ("FINISHED".equals(before.getStatus()) || "ENDED".equals(before.getStatus())
                || "SETTLED".equals(before.getStatus())) {
            return toResponse(before);
        }
        if (!STATUS_RUNNING.equals(before.getStatus()) && !STATUS_LEGACY_ONGOING.equals(before.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有行驶中行程可以结束");
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = tripMapper.endOngoingTrip(tripId, userId, now);
        if (rows == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前状态不允许结束行程");
        }
        Trip after = tripMapper.findById(tripId);
        insertAuditLog(tripId, userId, "END", before, after, "结束行程");
        eventPublisher.publishEvent(new TripFinishedEvent(tripId));
        clearTripCaches(userId, tripId);
        return toResponse(after);
    }

    /** 查询到达终点后的开放式结束状态。 */
    @Override
    public ArrivalDecisionResponse getArrivalDecision(Long tripId) {
        Trip trip = requireReadableTrip(tripId);
        boolean pending = "AWAITING_DECISION".equals(trip.getArrivalStatus());
        return new ArrivalDecisionResponse(
                String.valueOf(trip.getId()),
                trip.getArrivalStatus() == null ? "NOT_ARRIVED" : trip.getArrivalStatus(),
                formatTime(trip.getArrivalEnteredAt()),
                formatTime(trip.getArrivalDecisionDeadline()),
                pending,
                pending
        );
    }

    /** 队长在到达提示中确认结束；重复请求会返回当前最终状态。 */
    @Override
    @Transactional
    public TripResponse finishArrival(Long tripId) {
        Long userId = currentUserContext.requireUserId();
        Trip before = requireCaptainTrip(tripId, userId);
        if ("FINISHED".equals(before.getStatus()) || "SETTLED".equals(before.getStatus())) {
            return toResponse(before);
        }
        if (!"AWAITING_DECISION".equals(before.getArrivalStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "尚未满足终点停留条件，暂不能结束行程");
        }
        LocalDateTime now = LocalDateTime.now();
        if (tripMapper.finishArrivedTrip(tripId, now) == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前状态不允许结束行程");
        }
        Trip after = tripMapper.findById(tripId);
        insertAuditLog(tripId, userId, "ARRIVAL_END", before, after, "到达后确认结束行程");
        eventPublisher.publishEvent(new TripFinishedEvent(tripId));
        clearTripCaches(userId, tripId);
        return toResponse(after);
    }

    /**
     * 队长选择继续行程：清除本次到达状态，替换终点并重新生成下一段真实路线。
     */
    @Override
    @Transactional
    public TripResponse continueTrip(Long tripId, ContinueTripRequest request) {
        Long userId = currentUserContext.requireUserId();
        Trip before = requireCaptainTrip(tripId, userId);
        if (!"AWAITING_DECISION".equals(before.getArrivalStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有到达待确认状态可以继续行程");
        }

        LocationRequest oldDestination = new LocationRequest(
                before.getEndLocationName(), before.getEndLocationAddress(),
                before.getEndLatitude(), before.getEndLongitude());
        LocationRequest newDestination = request.endLocation();
        validateContinueDestination(oldDestination, newDestination);

        LocalDateTime now = LocalDateTime.now();
        int rows = tripMapper.continueTrip(
                tripId, userId,
                displayName(newDestination.name(), newDestination.address()),
                normalize(newDestination.address()),
                newDestination.latitude(), newDestination.longitude(), now);
        if (rows == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前状态不允许继续行程");
        }

        TripRoute nextRoute = buildRouteSnapshot(tripId, oldDestination, newDestination, List.of(), now);
        upsertRoute(nextRoute);
        Trip after = tripMapper.findById(tripId);
        applyRouteToTrip(after, nextRoute);
        after.setUpdatedAt(now);
        tripMapper.update(after);
        insertAuditLog(tripId, userId, "CONTINUE", before, after, "到达后继续行程并更新终点");
        clearTripCaches(userId, tripId);
        eventPublisher.publishEvent(new TripUpdatedEvent(tripId, userId));
        return buildResponse(tripId);
    }

    private void validateContinueDestination(LocationRequest oldDestination, LocationRequest newDestination) {
        if (newDestination == null || newDestination.latitude() == null || newDestination.longitude() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新的终点和坐标必填");
        }
        if (Objects.equals(oldDestination.latitude(), newDestination.latitude())
                && Objects.equals(oldDestination.longitude(), newDestination.longitude())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新的终点不能与当前终点相同");
        }
    }

    /**
     * 取消本人可变更状态下的行程。
     */
    @Override
    @Transactional
    public TripResponse cancelTrip(Long tripId) {
        return changeStatus(tripId, STATUS_CANCELLED, "CANCEL", "取消行程");
    }

    /** 系统自动出发入口，前置成员范围检测由生命周期协调器完成。 */
    @Override
    @Transactional
    public TripResponse autoStartTrip(Long tripId, List<Long> participantUserIds) {
        Trip before = tripMapper.findById(tripId);
        if (before == null || before.getCaptainUserId() == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "自动出发行程不存在或尚无队长");
        }
        if (!List.of(STATUS_PUBLISHED, STATUS_READY, STATUS_CONFIRMING).contains(before.getStatus())) {
            return toResponse(before);
        }
        LocalDateTime now = LocalDateTime.now();
        if (tripMapper.autoStartTrip(tripId, now) == 0) {
            return buildResponse(tripId);
        }
        LinkedHashSet<Long> participants = new LinkedHashSet<>();
        participants.add(before.getCaptainUserId());
        if (participantUserIds != null) participants.addAll(participantUserIds);
        int plannedDistance = before.getTotalDistanceMeters() != null
                ? before.getTotalDistanceMeters() : before.getRouteDistance() == null ? 0 : before.getRouteDistance();
        executionSettlementMapper.createExecution(
                SnowflakeIdGenerator.nextId(), tripId, before.getCaptainUserId(), plannedDistance, now);
        Long executionId = executionSettlementMapper.executionId(tripId);
        for (Long participantId : participants) {
            executionSettlementMapper.createExecutionMember(
                    SnowflakeIdGenerator.nextId(), executionId, tripId, participantId,
                    participantId.equals(before.getCaptainUserId()) ? "CAPTAIN" : "MEMBER", now);
        }
        Trip after = tripMapper.findById(tripId);
        insertAuditLog(tripId, before.getCaptainUserId(), "AUTO_START", before, after, "到达出发时间后自动出发");
        eventPublisher.publishEvent(new TripStartedEvent(
                tripId, StringUtils.hasText(after.getTitle()) ? after.getTitle() : "行程车队群",
                before.getCaptainUserId(), List.copyOf(participants)));
        participants.forEach(this::clearListCaches);
        redisTemplate.delete(DETAIL_CACHE_KEY.formatted(tripId));
        return toResponse(after);
    }

    /** 到达待确认超过 24 小时后的系统幂等结束入口。 */
    @Override
    @Transactional
    public TripResponse autoFinishArrival(Long tripId) {
        Trip before = tripMapper.findById(tripId);
        if (before == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        if (!"AWAITING_DECISION".equals(before.getArrivalStatus())) {
            return toResponse(before);
        }
        LocalDateTime now = LocalDateTime.now();
        if (tripMapper.finishArrivedTrip(tripId, now) == 0) {
            return buildResponse(tripId);
        }
        Trip after = tripMapper.findById(tripId);
        insertAuditLog(tripId, before.getCaptainUserId(), "AUTO_END", before, after, "到达后超时自动结束");
        eventPublisher.publishEvent(new TripFinishedEvent(tripId));
        clearTripCaches(before.getUserId(), tripId);
        return toResponse(after);
    }

    /**
     * 查询公开行程列表，限制最大返回数量并使用短期缓存。
     */
    @Override
    public TripListResponse getPublicTrips(Integer limit) {
        Long userId = currentUserContext.requireUserId();
        int size = limit == null ? 20 : Math.max(1, Math.min(limit, 50));
        String cacheKey = PUBLIC_CACHE_KEY.formatted(userId, size);
        TripListResponse cached = readJson(cacheKey, TripListResponse.class);
        if (cached != null) {
            return cached;
        }
        TripListResponse response = new TripListResponse(tripMapper.findPublicTrips(userId, size).stream().map(this::toResponseWithoutChildren).toList());
        writeJson(cacheKey, response, Duration.ofMinutes(3));
        redisTemplate.opsForSet().add(PUBLIC_CACHE_KEYS, cacheKey);
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

    @Override
    public TripResponse getCurrentDrivingTrip() {
        Long userId = currentUserContext.requireUserId();
        Trip trip = tripMapper.findCurrentDrivingByUserId(userId);
        if (trip == null) {
            Long participatingTripId = participationPort.findRunningParticipatingTripId(userId);
            trip = participatingTripId == null ? null : tripMapper.findById(participatingTripId);
        }
        return trip == null ? null : toResponse(trip);
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
        trip.setTitle(StringUtils.hasText(request.title()) ? normalize(request.title())
                : displayName(request.startLocation().name(), request.startLocation().address()) + "到"
                + displayName(request.endLocation().name(), request.endLocation().address()));
        trip.setDescription(StringUtils.hasText(request.description()) ? normalize(request.description()) : normalize(request.remark()));
        trip.setExpectedPeople(request.expectedPeople() == null ? defaultCapacity(request.maxVehicleCount()) : request.expectedPeople());
        fillLocations(trip, request.startLocation(), request.endLocation());
        trip.setRouteSummary(normalize(request.routeSummary()));
        trip.setRoutePolylineKey("");
        // routeDistance/routeDuration 由 TripRoute 规划结果统一回填；完整 polyline 不进入 trip 主表。
        trip.setWaypointsJson(toJson(sortWaypoints(request.waypoints())));
        trip.setDepartureTime(parseFutureTime(request.departureTime()));
        trip.setEstimatedDays(request.estimatedDays());
        trip.setMaxVehicleCount(defaultCapacity(request.maxVehicleCount()));
        trip.setVehicleRequirements(vehicleRequirements(request.vehicleRequirements()));
        trip.setBudgetDescription(normalize(request.budgetDescription()));
        trip.setTravelDepth(normalize(request.travelDepth()));
        trip.setPublicFlag(Boolean.TRUE.equals(request.publicFlag()) ? 1 : 0);
        trip.setRemark(normalize(request.remark()));
    }

    /**
     * 根据更新请求填充行程主表字段。
     */
    private void fillTrip(Trip trip, UpdateTripRequest request) {
        trip.setTitle(StringUtils.hasText(request.title()) ? normalize(request.title())
                : displayName(request.startLocation().name(), request.startLocation().address()) + "到"
                + displayName(request.endLocation().name(), request.endLocation().address()));
        trip.setDescription(StringUtils.hasText(request.description()) ? normalize(request.description()) : normalize(request.remark()));
        trip.setExpectedPeople(request.expectedPeople() == null ? defaultCapacity(request.maxVehicleCount()) : request.expectedPeople());
        fillLocations(trip, request.startLocation(), request.endLocation());
        trip.setRouteSummary(normalize(request.routeSummary()));
        trip.setRoutePolylineKey("");
        // routeDistance/routeDuration 由 TripRoute 规划结果统一回填；完整 polyline 不进入 trip 主表。
        trip.setWaypointsJson(toJson(sortWaypoints(request.waypoints())));
        trip.setDepartureTime(parseFutureTime(request.departureTime()));
        trip.setEstimatedDays(request.estimatedDays());
        trip.setMaxVehicleCount(defaultCapacity(request.maxVehicleCount()));
        trip.setVehicleRequirements(vehicleRequirements(request.vehicleRequirements()));
        trip.setBudgetDescription(normalize(request.budgetDescription()));
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
     * 调用地图模块生成规划路线快照。
     */
    private TripRoute buildRouteSnapshot(Long tripId, LocationRequest startLocation, LocationRequest endLocation,
                                         List<WaypointLocationRequest> waypoints, LocalDateTime now) {
        List<LocationDto> waypointDtos = sortWaypoints(waypoints).stream()
                .map(waypoint -> new LocationDto(waypoint.name(), waypoint.address(), waypoint.latitude(), waypoint.longitude()))
                .toList();
        RoutePlanResponse plan = mapService.planRoute(new RoutePlanRequest(
                new LocationDto(startLocation.name(), startLocation.address(), startLocation.latitude(), startLocation.longitude()),
                new LocationDto(endLocation.name(), endLocation.address(), endLocation.latitude(), endLocation.longitude()),
                waypointDtos
        ));

        TripRoute route = new TripRoute();
        route.setId(SnowflakeIdGenerator.nextId());
        route.setTripId(tripId);
        route.setRoutePlanId(Long.valueOf(plan.routePlanId()));
        route.setOrigin(toJson(startLocation));
        route.setDestination(toJson(endLocation));
        route.setWaypoints(toJson(sortWaypoints(waypoints)));
        route.setPolyline(plan.routePolyline());
        route.setMatchPolyline(RoutePolylineUtils.simplifyForMatching(objectMapper, plan.routePolyline(),
                recommendationRouteProperties.getRdpEpsilon(), recommendationRouteProperties.getMaxPoints()));
        route.setPlanDistance(plan.routeDistance());
        route.setPlanDuration(plan.routeDuration());
        route.setProviderType(plan.providerType());
        route.setRouteStatus("VALID");
        route.setRouteSignature(routeSignature(startLocation, endLocation, waypoints));
        route.setCreatedAt(now);
        route.setUpdatedAt(now);
        route.setDeleted(0);
        return route;
    }

    private String routeSignature(LocationRequest start, LocationRequest end, List<WaypointLocationRequest> waypoints) {
        List<Node> nodes = sortWaypoints(waypoints).stream()
                .map(value -> new Node(value.latitude(), value.longitude())).toList();
        return RouteSignatureUtils.signature(new Node(start.latitude(), start.longitude()),
                new Node(end.latitude(), end.longitude()), nodes);
    }

    private boolean routeChanged(Trip before, UpdateTripRequest request) {
        List<Node> oldWaypoints = readWaypoints(before.getWaypointsJson()).stream()
                .sorted(Comparator.comparing(WaypointLocationResponse::sortOrder,
                        Comparator.nullsLast(Integer::compareTo)))
                .map(value -> new Node(value.latitude(), value.longitude()))
                .toList();
        String oldSignature = RouteSignatureUtils.signature(
                new Node(firstNonNull(before.getStartLatitude(), before.getStartLat()),
                        firstNonNull(before.getStartLongitude(), before.getStartLng())),
                new Node(firstNonNull(before.getEndLatitude(), before.getEndLat()),
                        firstNonNull(before.getEndLongitude(), before.getEndLng())),
                oldWaypoints);
        String newSignature = routeSignature(request.startLocation(), request.endLocation(), request.waypoints());
        return !Objects.equals(oldSignature, newSignature);
    }

    /**
     * 将草稿阶段已经验证通过的真实道路路线提升为正式行程快照。
     * 发布链路因此不必对完全相同的节点再次调用地图服务；导航和匹配仍使用该真实路线。
     */
    private TripRoute buildProvidedRouteSnapshot(Long tripId, LocationRequest startLocation,
                                                   LocationRequest endLocation,
                                                   List<WaypointLocationRequest> waypoints,
                                                   String polyline, Integer distance, Integer duration,
                                                   LocalDateTime now) {
        TripRoute route = new TripRoute();
        route.setId(SnowflakeIdGenerator.nextId());
        route.setTripId(tripId);
        route.setOrigin(toJson(startLocation));
        route.setDestination(toJson(endLocation));
        route.setWaypoints(toJson(sortWaypoints(waypoints)));
        route.setPolyline(polyline);
        route.setMatchPolyline(RoutePolylineUtils.simplifyForMatching(objectMapper, polyline,
                recommendationRouteProperties.getRdpEpsilon(), recommendationRouteProperties.getMaxPoints()));
        route.setPlanDistance(distance);
        route.setPlanDuration(duration);
        route.setProviderType("AMAP_WEB_V5");
        route.setRouteStatus("VALID");
        route.setRouteSignature(routeSignature(startLocation, endLocation, waypoints));
        route.setCreatedAt(now);
        route.setUpdatedAt(now);
        route.setDeleted(0);
        return route;
    }

    /**
     * 将规划路线快照同步到 trip 主表兼容字段。
     */
    private void applyRouteToTrip(Trip trip, TripRoute route) {
        trip.setRouteDistance(route.getPlanDistance());
        trip.setRouteDuration(route.getPlanDuration());
        trip.setTotalDistanceMeters(route.getPlanDistance());
    }

    /**
     * 新增或更新规划路线快照。
     */
    private void upsertRoute(TripRoute route) {
        if (routeMapper.updateByTripId(route) == 0) {
            routeMapper.insert(route);
        }
    }

    /**
     * 发布行程时写入车主成员快照，保留昵称和车辆展示信息。
     */
    private void insertPublisherSnapshot(Trip trip, TripVehicleDTO vehicle, LocalDateTime now) {
        TripUserProfileDTO profile = userProfilePort.getCurrentProfile();
        TripMemberSnapshot member = new TripMemberSnapshot();
        member.setId(SnowflakeIdGenerator.nextId());
        member.setTripId(trip.getId());
        member.setUserId(trip.getUserId());
        member.setVehicleId(vehicle == null ? null : trip.getVehicleId());
        member.setMemberRole(vehicle == null ? "REQUESTER" : "OWNER");
        member.setJoinStatus(vehicle == null ? "REQUESTED" : "OWNER");
        member.setNicknameSnapshot(profile == null ? "同路行车友" : profile.nickname());
        member.setVehicleSnapshot(vehicle == null ? "乘客出行需求" : (vehicle.brand() + " " + vehicle.model()).trim());
        member.setJoinedAt(now);
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        memberMapper.insert(member);
    }

    /** 为全部参与用户加短期 Redis 锁，防止连续点击或并发请求同时开启两条行程。 */
    private List<String> acquireStartLocks(List<Long> userIds, String lockValue) {
        List<String> acquired = new ArrayList<>();
        for (Long userId : userIds) {
            String key = START_LOCK_KEY.formatted(userId);
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, lockValue, Duration.ofSeconds(60));
            if (!Boolean.TRUE.equals(locked)) {
                releaseStartLocks(acquired, lockValue);
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "行程正在开启处理中，请勿重复点击");
            }
            acquired.add(key);
        }
        return acquired;
    }


    /**
     * 将锁延迟到事务提交或回滚后释放，避免第一条行程尚未提交时第二个请求读到旧状态。
     * 返回 true 表示当前没有事务同步，需要由 finally 立即释放。
     */
    private boolean registerLockReleaseAfterTransaction(List<String> keys, String lockValue) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return true;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                releaseStartLocks(keys, lockValue);
            }
        });
        return false;
    }

    private void releaseStartLocks(List<String> keys, String lockValue) {
        for (String key : keys) {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), lockValue);
        }
    }

    /**
     * 根据认证状态确定发布身份。优先使用用户显式选择的车辆；未选择时自动取默认认证车辆。
     * 没有任何认证车辆时返回 null，调用方据此创建乘客出行需求。
     */
    private TripVehicleDTO resolvePublisherVehicle(Long requestedVehicleId, Long userId) {
        if (requestedVehicleId != null) {
            // P0 要求所有用户都可发布：显式选择了未认证车辆时不再硬拦截，
            // 而是按乘客出行需求发布；只有已通过行驶证认证的车辆才赋予队长身份。
            TripVehicleDTO selected = vehiclePort.getCertifiedVehicle(requestedVehicleId, userId);
            return selected != null && CERT_APPROVED.equals(selected.certificationStatus()) ? selected : null;
        }
        TripVehicleDTO vehicle = vehiclePort.getDefaultCertifiedVehicle(userId);
        return vehicle != null && CERT_APPROVED.equals(vehicle.certificationStatus()) ? vehicle : null;
    }

    private int defaultCapacity(Integer value) {
        return value == null ? 4 : Math.max(1, Math.min(value, 50));
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
     * 查询可读行程。
     *
     * <p>除发布者和公开行程外，已加入该行程队伍的有效成员也必须能够读取详情，
     * 否则首页地图和队员行程状态会因为行程设为非公开而失效。</p>
     */
    private Trip requireReadableTrip(Long tripId) {
        Long userId = currentUserContext.requireUserId();
        Trip trip = tripMapper.findById(tripId);
        if (trip == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        boolean participant = participationPort.findActiveParticipantUserIds(tripId).contains(userId);
        if (!trip.getUserId().equals(userId) && trip.getPublicFlag() != 1 && !participant) {
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
     * 队长专属操作校验。
     *
     * <p>当前产品规则中，行程创建者就是队长。历史数据可能因为旧逻辑只在绑定车辆时
     * 写入 captain_user_id，导致创建者被误判为普通成员；这里以 user_id 为最终依据，
     * 并在发现旧数据不一致时顺便修复 captain_user_id。</p>
     */
    private Trip requireCaptainTrip(Long tripId, Long userId) {
        Trip trip = tripMapper.findById(tripId);
        if (trip == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        if (!trip.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有当前行程队长可以执行此操作");
        }
        if (!userId.equals(trip.getCaptainUserId())) {
            tripMapper.ensureCreatorCaptain(tripId, userId);
            trip.setCaptainUserId(userId);
        }
        return trip;
    }

    /**
     * 校验当前行程状态是否允许修改。
     */
    private void ensureMutable(Trip trip) {
        if (!STATUS_PUBLISHED.equals(trip.getStatus())
                && !STATUS_READY.equals(trip.getStatus())
                && !STATUS_CONFIRMING.equals(trip.getStatus())
                && !STATUS_RUNNING.equals(trip.getStatus())
                && !STATUS_LEGACY_ONGOING.equals(trip.getStatus())) {
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
        if (waypoints != null && waypoints.size() > 20) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "途经点最多 20 个");
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
    private String routePreviewPolyline(Long tripId) {
        TripRoute route = routeMapper.findByTripId(tripId);
        return route == null ? "" : RoutePolylineUtils.simplify(objectMapper, route.getPolyline(), 600);
    }

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
        return toResponse(trip, readWaypoints(trip.getWaypointsJson()), true);
    }

    /** 列表场景不读取 trip_route MEDIUMTEXT，只返回起终点/途经点摘要。 */
    private TripResponse toResponseWithoutChildren(Trip trip) {
        return toResponse(trip, readWaypoints(trip.getWaypointsJson()), false);
    }

    /**
     * 将行程实体和途经点列表转换为接口响应对象。
     */
    private TripResponse toResponse(Trip trip, List<WaypointLocationResponse> waypoints, boolean includeRoutePreview) {
        // 创建者是队长。响应层继续做一次兜底，避免旧数据缓存让客户端隐藏“开始行程”入口。
        Long captainUserId = trip.getUserId();
        return new TripResponse(
                String.valueOf(trip.getId()),
                trip.getTripNumber(),
                String.valueOf(trip.getUserId()),
                trip.getVehicleId() == null ? null : String.valueOf(trip.getVehicleId()),
                trip.getTitle(),
                trip.getDescription(),
                trip.getExpectedPeople(),
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
                includeRoutePreview ? routePreviewPolyline(trip.getId()) : "",
                formatTime(trip.getDepartureTime()),
                trip.getEstimatedDays(),
                trip.getTotalDistanceMeters(),
                trip.getMaxVehicleCount(),
                trip.getJoinedVehicleCount(),
                vehicleRequirementsList(trip.getVehicleRequirements()),
                trip.getBudgetDescription(),
                trip.getTravelDepth(),
                trip.getPublicFlag() != null && trip.getPublicFlag() == 1,
                trip.getStatus(),
                trip.getRemark(),
                formatTime(trip.getActualStartTime()),
                formatTime(trip.getActualEndTime()),
                waypoints,
                formatTime(trip.getCreatedAt()),
                formatTime(trip.getUpdatedAt()),
                trip.getTripType(),
                trip.getPublisherRole(),
                String.valueOf(captainUserId),
                trip.getAutoStartEnabled() != null && trip.getAutoStartEnabled() == 1,
                trip.getArrivalStatus(),
                formatTime(trip.getArrivalEnteredAt()),
                formatTime(trip.getArrivalDecisionDeadline()),
                trip.getContinueCount(),
                TRIP_TYPE_PASSENGER.equals(trip.getTripType()),
                true
        );
    }

    /** Snowflake ID 的 36 进制表示天然唯一，增加 TLX 前缀后作为可公开分享的行程号。 */
    private String toTripNumber(Long tripId) {
        return "TLX" + Long.toUnsignedString(tripId, 36).toUpperCase(java.util.Locale.ROOT);
    }

    private String vehicleRequirements(List<String> values) {
        if (values == null || values.isEmpty() || values.stream().anyMatch("不限"::equals)) return "不限";
        List<String> allowed = List.of("SUV", "轿车", "越野车", "摩托车", "MPV", "新能源");
        List<String> normalized = values.stream()
                .filter(StringUtils::hasText).map(String::trim).filter(allowed::contains).distinct().toList();
        if (normalized.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择有效的车辆要求");
        }
        return String.join(",", normalized);
    }

    private List<String> vehicleRequirementsList(String value) {
        if (!StringUtils.hasText(value)) return List.of("不限");
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim).filter(v -> !v.isEmpty()).distinct().toList();
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
        trip.setTripNumber(source.getTripNumber());
        trip.setUserId(source.getUserId());
        trip.setVehicleId(source.getVehicleId());
        trip.setTripType(source.getTripType());
        trip.setPublisherRole(source.getPublisherRole());
        trip.setCaptainUserId(source.getCaptainUserId());
        trip.setAutoStartEnabled(source.getAutoStartEnabled());
        trip.setArrivalStatus(source.getArrivalStatus());
        trip.setArrivalEnteredAt(source.getArrivalEnteredAt());
        trip.setArrivalDecisionDeadline(source.getArrivalDecisionDeadline());
        trip.setContinueCount(source.getContinueCount());
        trip.setTitle(source.getTitle());
        trip.setDescription(source.getDescription());
        trip.setExpectedPeople(source.getExpectedPeople());
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
        trip.setActualStartTime(source.getActualStartTime());
        trip.setActualEndTime(source.getActualEndTime());
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
        Set<String> keysToDelete = new LinkedHashSet<>();
        Set<String> publicKeys = redisTemplate.opsForSet().members(PUBLIC_CACHE_KEYS);
        if (publicKeys != null) keysToDelete.addAll(publicKeys);
        // 兼容升级前已写入但尚未登记到 registry 的 1~50 条公开列表缓存，
        // 避免部署后的第一条新行程仍被旧缓存遮挡三分钟。
        for (int size = 1; size <= 50; size++) {
            keysToDelete.add(LEGACY_PUBLIC_CACHE_KEY.formatted(size));
        }
        redisTemplate.delete(keysToDelete);
        redisTemplate.delete(PUBLIC_CACHE_KEYS);
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

    private LocalDateTime parseFutureTime(String value) {
        LocalDateTime parsed = parseTime(value);
        if (!parsed.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "出发时间必须晚于当前时间");
        }
        return parsed;
    }

    private double haversineMeters(double latitude1, double longitude1, double latitude2, double longitude2) {
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double startLatitude = Math.toRadians(latitude1);
        double endLatitude = Math.toRadians(latitude2);
        double value = Math.sin(latitudeDelta / 2D) * Math.sin(latitudeDelta / 2D)
                + Math.cos(startLatitude) * Math.cos(endLatitude)
                * Math.sin(longitudeDelta / 2D) * Math.sin(longitudeDelta / 2D);
        // 浮点运算在边界位置可能得到略小于 0 或略大于 1 的值，直接开方会产生 NaN。
        // 将其限制到 Haversine 公式的合法定义域，避免 5 公里校验变成未处理异常。
        double safeValue = Math.max(0D, Math.min(1D, value));
        return 6_371_000D * 2D
                * Math.atan2(Math.sqrt(safeValue), Math.sqrt(1D - safeValue));
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
     * 优先使用标准经纬度字段，并兼容迁移前只写入旧坐标字段的历史数据。
     */
    private BigDecimal firstNonNull(BigDecimal primary, BigDecimal fallback) {
        return primary == null ? fallback : primary;
    }

    /**
     * 统一处理空字符串和前后空格。
     */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
