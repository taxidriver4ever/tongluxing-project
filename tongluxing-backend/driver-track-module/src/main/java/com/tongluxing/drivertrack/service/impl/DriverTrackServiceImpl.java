package com.tongluxing.drivertrack.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.drivertrack.dto.DriverTrackBatchRequest;
import com.tongluxing.drivertrack.dto.DriverTrackPointRequest;
import com.tongluxing.drivertrack.dto.MockDeviationRequest;
import com.tongluxing.drivertrack.entity.DriverDeviationRecord;
import com.tongluxing.drivertrack.entity.DriverTrackRecord;
import com.tongluxing.drivertrack.entity.DriverTrackDistanceRecord;
import com.tongluxing.drivertrack.mapper.DriverDeviationRecordMapper;
import com.tongluxing.drivertrack.mapper.DriverTrackRecordMapper;
import com.tongluxing.drivertrack.mapper.DriverTrackDistanceRecordMapper;
import com.tongluxing.drivertrack.mapper.DriverMemberDistanceAlertMapper;
import com.tongluxing.drivertrack.mapper.TripExecutionTrackMapper;
import com.tongluxing.drivertrack.service.DriverTrackService;
import com.tongluxing.drivertrack.service.MileageSettlementService;
import com.tongluxing.drivertrack.vo.DriverDeviationResponse;
import com.tongluxing.drivertrack.vo.DriverDistanceResponse;
import com.tongluxing.drivertrack.vo.DriverTrackListResponse;
import com.tongluxing.drivertrack.vo.DriverTrackPointVO;
import com.tongluxing.drivertrack.vo.DriverTrackUploadResponse;
import com.tongluxing.drivertrack.vo.MileageSettlementResponse;
import com.tongluxing.map.dto.LocationDto;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.entity.TripRoute;
import com.tongluxing.trip.mapper.TripMapper;
import com.tongluxing.trip.mapper.TripRouteMapper;
import com.tongluxing.trip.mapper.TripWaypointMapper;
import com.tongluxing.trip.mapper.TripMemberSnapshotMapper;
import com.tongluxing.trip.entity.TripWaypoint;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 驾驶轨迹服务实现。
 */
@Service
@RequiredArgsConstructor
public class DriverTrackServiceImpl implements DriverTrackService {

    /** 与 trip-module 的“行驶中”持久状态保持一致。 */
    private static final String STATUS_RUNNING = "RUNNING";
    private static final int FAR_MEMBER_METERS = 500;
    private static final int SEVERE_MEMBER_METERS = 1_000;
    private static final int RECOVERY_MEMBER_METERS = 300;
    private static final int MILD_DEVIATION_METERS = 100;
    private static final int SEVERE_DEVIATION_METERS = 500;
    private static final int RECOVERY_DEVIATION_METERS = 60;
    private static final int MAX_RELIABLE_ACCURACY_METERS = 50;
    private static final int WAYPOINT_ARRIVAL_METERS = 100;

    private final CurrentUserContext currentUserContext;
    private final ObjectMapper objectMapper;
    private final TripMapper tripMapper;
    private final TripRouteMapper tripRouteMapper;
    private final TripWaypointMapper tripWaypointMapper;
    private final TripMemberSnapshotMapper tripMemberSnapshotMapper;
    private final DriverTrackRecordMapper trackMapper;
    private final DriverDeviationRecordMapper deviationMapper;
    private final DriverTrackDistanceRecordMapper distanceMapper;
    private final DriverMemberDistanceAlertMapper memberAlertMapper;
    private final TripExecutionTrackMapper executionTrackMapper;
    private final MileageSettlementService mileageSettlementService;

    @Override
    @Transactional
    public DriverTrackUploadResponse uploadPoint(DriverTrackPointRequest request) {
        Long driverId = currentUserContext.requireUserId();
        Trip trip = requireOngoingParticipantTrip(request.tripId(), driverId);
        DriverTrackRecord previousRaw = trackMapper.findLast(request.tripId(), driverId);
        DriverTrackRecord previousValid = trackMapper.findLastValid(
                request.tripId(), driverId);
        FilterResult filter = filterPoint(previousRaw, previousValid, request);
        int distanceFromPrev = filter.distanceMeters();
        int rawDistanceFromPrev = previousRaw == null ? 0 : haversineMeters(
                previousRaw.getLatitude(), previousRaw.getLongitude(),
                request.latitude(), request.longitude());

        LocalDateTime now = LocalDateTime.now();
        DriverTrackRecord record = new DriverTrackRecord();
        record.setId(SnowflakeIdGenerator.nextId());
        record.setTripId(request.tripId());
        record.setDriverId(driverId);
        record.setLongitude(request.longitude());
        record.setLatitude(request.latitude());
        record.setSpeed(request.speed());
        record.setDirection(request.direction());
        record.setAccuracy(request.accuracy());
        record.setDistanceFromPrev(distanceFromPrev);
        record.setDeviceId(request.deviceId());
        record.setSequenceNo(request.sequenceNo());
        record.setMockLocation(Boolean.TRUE.equals(request.mockLocation()) ? 1 : 0);
        record.setPointStatus(filter.status());
        record.setValidPoint(filter.valid() ? 1 : 0);
        record.setRecordTime(request.recordTime());
        record.setCreatedAt(now);
        record.setDeleted(0);
        trackMapper.insert(record);
        Long executionId = ensureExecutionTrack(trip, driverId, record, rawDistanceFromPrev, now);
        if (filter.valid()) {
            saveMemberDistanceState(trip, request, driverId, now);
        }

        int totalDistance = trackMapper.sumDistance(request.tripId(), driverId);
        // 路线折线仅用于展示和估算，不再进行道路偏航判定。
        MileageSettlementResponse mileageResult = mileageSettlementService
                .settleMileage(request.tripId(), driverId, totalDistance);
        WaypointArrival waypointArrival = settleReachedWaypoint(
                request.tripId(), driverId, request.latitude(), request.longitude(), totalDistance);
        if (waypointArrival != null) {
            executionTrackMapper.insertWaypointArrival(
                    SnowflakeIdGenerator.nextId(), executionId, trip.getId(),
                    waypointArrival.waypointId(), driverId,
                    waypointArrival.firstInsideAt(), request.recordTime(),
                    waypointArrival.evidenceCount(), waypointArrival.distanceMeters());
        }

        return new DriverTrackUploadResponse(
                String.valueOf(record.getId()),
                distanceFromPrev,
                0,
                0,
                totalDistance,
                mileageResult.settledStages() + (waypointArrival == null ? 0 : 1),
                mileageResult.grantedPoints() + (waypointArrival == null ? 0 : waypointArrival.points()),
                waypointArrival == null ? null : String.valueOf(waypointArrival.waypointId()),
                waypointArrival == null ? null : waypointArrival.waypointName()
        );
    }

    private Long ensureExecutionTrack(
            Trip trip, Long driverId, DriverTrackRecord record,
            int rawDistanceFromPrev, LocalDateTime now) {
        int plannedDistance = trip.getTotalDistanceMeters() != null
                ? trip.getTotalDistanceMeters()
                : trip.getRouteDistance() == null ? 0 : trip.getRouteDistance();
        executionTrackMapper.ensureExecution(
                SnowflakeIdGenerator.nextId(), trip.getId(), trip.getUserId(), plannedDistance, now);
        Long executionId = executionTrackMapper.findExecutionId(trip.getId());
        executionTrackMapper.ensureMember(
                SnowflakeIdGenerator.nextId(), executionId, trip.getId(), driverId,
                driverId.equals(trip.getUserId()) ? "CAPTAIN" : "MEMBER", now);
        executionTrackMapper.insertPoint(executionId, record, rawDistanceFromPrev);
        executionTrackMapper.appendDistance(
                executionId, rawDistanceFromPrev, record.getDistanceFromPrev(), now);
        return executionId;
    }

    @Override
    @Transactional
    public DriverTrackListResponse uploadBatch(DriverTrackBatchRequest request) {
        Long tripId = request.points().get(0).tripId();
        if (request.points().stream().anyMatch(point -> !tripId.equals(point.tripId()))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "一次批量上传只能包含同一行程的轨迹点");
        }
        HashSet<LocalDateTime> recordTimes = new HashSet<>();
        if (request.points().stream().anyMatch(point -> !recordTimes.add(point.recordTime()))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "同一批次不能包含重复时间的轨迹点");
        }
        request.points().stream()
                .sorted(Comparator.comparing(DriverTrackPointRequest::recordTime))
                .forEach(this::uploadPoint);
        return getTrack(tripId);
    }

    @Override
    public DriverTrackListResponse getTrack(Long tripId) {
        requireReadableTrip(tripId);
        return new DriverTrackListResponse(
                String.valueOf(tripId),
                trackMapper.findByTripId(tripId, 5000).stream().map(this::toPointVO).toList()
        );
    }

    @Override
    public DriverDeviationResponse getDeviation(Long tripId) {
        requireReadableTrip(tripId);
        Long userId = currentUserContext.requireUserId();
        DriverDeviationRecord latest = deviationMapper.findLatest(tripId, userId);
        if (latest == null) {
            return new DriverDeviationResponse(String.valueOf(tripId), 0, 0, "");
        }
        int status = latest.getDeviationStatus();
        if (latest.getRecordTime().isBefore(LocalDateTime.now().minusMinutes(2))) {
            status = 3;
        }
        return new DriverDeviationResponse(
                String.valueOf(tripId), status, latest.getDeviationDistance(),
                formatTime(latest.getRecordTime()));
    }

    @Override
    public DriverDistanceResponse getDistance(Long tripId) {
        Long driverId = currentUserContext.requireUserId();
        requireReadableTrip(tripId);
        int totalDistance = trackMapper.sumDistance(tripId, driverId);
        DriverTrackDistanceRecord latest = distanceMapper.findLatest(tripId, driverId);
        return new DriverDistanceResponse(
                String.valueOf(tripId),
                String.valueOf(driverId),
                totalDistance,
                latest == null ? 0 : latest.getLastSettleDistance(),
                latest == null ? "" : formatTime(latest.getSettleTime())
        );
    }

    @Override
    @Transactional
    public DriverDeviationResponse mockDeviation(Long tripId, MockDeviationRequest request) {
        Long driverId = currentUserContext.requireUserId();
        requireOngoingParticipantTrip(tripId, driverId);
        // 旧联调接口保持可调用，但不再写入偏航记录。
        return new DriverDeviationResponse(String.valueOf(tripId), 0, 0, "");
    }

    private Trip requireOngoingParticipantTrip(Long tripId, Long userId) {
        Trip trip = tripMapper.findById(tripId);
        if (trip == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        boolean participant = trip.getUserId().equals(userId)
                || tripMemberSnapshotMapper.findByTripId(tripId).stream()
                .anyMatch(member -> userId.equals(member.getUserId())
                        && ("OWNER".equals(member.getJoinStatus())
                        || "APPROVED".equals(member.getJoinStatus())));
        if (!participant) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有本次行程的有效成员可以上传轨迹");
        }
        if (!STATUS_RUNNING.equals(trip.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "行程未开始，不能上传轨迹");
        }
        return trip;
    }

    private FilterResult filterPoint(
            DriverTrackRecord previousRaw,
            DriverTrackRecord previousValid,
            DriverTrackPointRequest request) {
        if (Boolean.TRUE.equals(request.mockLocation())) {
            return new FilterResult(0, "MOCK_LOCATION", false);
        }
        if (request.accuracy() != null
                && request.accuracy().intValue() > MAX_RELIABLE_ACCURACY_METERS) {
            return new FilterResult(0, "LOW_ACCURACY", false);
        }
        if (previousRaw != null
                && !request.recordTime().isAfter(previousRaw.getRecordTime())) {
            return new FilterResult(0, "OUT_OF_ORDER", false);
        }
        if (previousRaw != null && request.sequenceNo() != null
                && previousRaw.getSequenceNo() != null
                && java.util.Objects.equals(request.deviceId(), previousRaw.getDeviceId())
                && request.sequenceNo() <= previousRaw.getSequenceNo()) {
            return new FilterResult(0, "DUPLICATE_SEQUENCE", false);
        }
        if (previousValid == null) {
            return new FilterResult(0, "VALID", true);
        }
        int distance = haversineMeters(
                previousValid.getLatitude(), previousValid.getLongitude(),
                request.latitude(), request.longitude());
        long millis = java.time.Duration.between(
                previousValid.getRecordTime(), request.recordTime()).toMillis();
        if (millis <= 0) {
            return new FilterResult(0, "OUT_OF_ORDER", false);
        }
        if (millis > 30_000) {
            // 定位中断后的首个可靠点只用于重新锚定，绝不能用缺口两端的直线
            // 充当用户实际走过的里程，否则重新开启定位时会产生巨额跳变。
            return new FilterResult(
                    0, millis > 600_000 ? "LONG_GAP_REVIEW" : "REANCHOR", true);
        }
        double seconds = millis / 1000.0d;
        double calculatedSpeed = distance / seconds;
        double reportedSpeed = request.speed() == null
                ? -1d : Math.max(0d, request.speed().doubleValue());
        int previousAccuracy = previousValid.getAccuracy() == null
                ? 0 : Math.max(0, previousValid.getAccuracy().intValue());
        int currentAccuracy = request.accuracy() == null
                ? 0 : Math.max(0, request.accuracy().intValue());
        int noiseRadius = Math.max(
                8, (int) Math.ceil((previousAccuracy + currentAccuracy) / 2.0d));

        // 位移没有超出两次定位的综合误差范围时认为仍在原地。该点仍作为
        // 下一次计算的可靠锚点，避免静止很久后所有后续点都被当成轨迹缺口。
        if (distance <= noiseRadius
                && (reportedSpeed < 0 || reportedSpeed < 2.0d)) {
            return new FilterResult(0, "STATIONARY", true);
        }

        // 静止/低速状态下突然漂移几十米是常见 GPS 跳点，不累计为里程。
        if (reportedSpeed >= 0 && reportedSpeed < 1.5d
                && distance <= Math.max(50, noiseRadius * 3)) {
            return new FilterResult(0, "GPS_DRIFT", false);
        }

        // 绝对速度上限和设备速度交叉校验同时防止跨城跳点。原始坐标仍会
        // 永久留存供复核，但异常段不会进入实际里程。
        if (calculatedSpeed > (200.0d / 3.6d)
                || reportedSpeed >= 0
                && calculatedSpeed > reportedSpeed * 3.0d + 8.0d
                && distance > 50) {
            return new FilterResult(0, "IMPOSSIBLE_SPEED", false);
        }
        return new FilterResult(distance, "VALID", true);
    }

    /**
     * 队友偏离按“与队长的实时距离”判断，不再混用道路偏航。
     * 500m 持续 60 秒为远离，1000m 持续 120 秒为严重，回到 300m 内恢复。
     */
    private void saveMemberDistanceState(
            Trip trip, DriverTrackPointRequest request, Long driverId, LocalDateTime now) {
        DriverTrackRecord captain = trackMapper.findLastValid(
                trip.getId(), trip.getUserId());
        if (captain == null || driverId.equals(trip.getUserId())) {
            return;
        }
        int distance = haversineMeters(
                request.latitude(), request.longitude(), captain.getLatitude(), captain.getLongitude());
        java.util.Map<String, Object> active = memberAlertMapper.findActive(trip.getId(), driverId);
        int status = 0;
        String nextLevel = null;
        Long alertId = active == null ? null : ((Number) active.get("id")).longValue();
        String level = active == null ? "" : String.valueOf(active.get("alertLevel"));
        LocalDateTime startedAt = active == null ? null : (LocalDateTime) active.get("startedAt");
        if (distance >= SEVERE_MEMBER_METERS) {
            if (active == null || !level.contains("SEVERE")) {
                nextLevel = "OBSERVING_SEVERE";
            } else if (startedAt != null && !startedAt.isAfter(request.recordTime().minusSeconds(120))) {
                nextLevel = "SEVERE";
                status = 2;
            }
        } else if (distance >= FAR_MEMBER_METERS) {
            if (active == null || level.contains("SEVERE")) {
                nextLevel = "OBSERVING_FAR";
            } else if (startedAt != null && !startedAt.isAfter(request.recordTime().minusSeconds(60))) {
                nextLevel = "FAR";
                status = 1;
            }
        } else if (distance < RECOVERY_MEMBER_METERS && alertId != null) {
            memberAlertMapper.recover(alertId, now);
        } else if ("SEVERE".equals(level)) {
            status = 2;
        } else if ("FAR".equals(level)) {
            status = 1;
        }
        if (nextLevel != null) {
            if (alertId == null || !level.equals(nextLevel)
                    && nextLevel.startsWith("OBSERVING")) {
                if (alertId != null) memberAlertMapper.recover(alertId, now);
                alertId = SnowflakeIdGenerator.nextId();
                memberAlertMapper.insert(alertId, trip.getId(), trip.getUserId(),
                        driverId, nextLevel, distance, request.recordTime());
            } else {
                memberAlertMapper.update(alertId, nextLevel, distance,
                        "FAR".equals(nextLevel) || "SEVERE".equals(nextLevel) ? 1 : 0, now);
            }
        }
        DriverDeviationRecord record = new DriverDeviationRecord();
        record.setId(SnowflakeIdGenerator.nextId());
        record.setTripId(trip.getId());
        record.setDriverId(driverId);
        record.setLongitude(request.longitude());
        record.setLatitude(request.latitude());
        record.setDeviationDistance(distance);
        record.setDeviationStatus(status);
        record.setRecordTime(request.recordTime());
        record.setCreatedAt(now);
        record.setDeleted(0);
        deviationMapper.insert(record);
    }

    private Trip requireReadableTrip(Long tripId) {
        Long userId = currentUserContext.requireUserId();
        Trip trip = tripMapper.findById(tripId);
        if (trip == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        if (!trip.getUserId().equals(userId) && (trip.getPublicFlag() == null || trip.getPublicFlag() != 1)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该行程");
        }
        return trip;
    }

    private DriverDeviationRecord saveDeviation(Trip trip, DriverTrackPointRequest request, Long driverId, LocalDateTime now) {
        DriverDeviationRecord previous = deviationMapper.findLatest(trip.getId(), driverId);
        DeviationReading reading = calculateDeviation(
                trip.getId(), request.latitude(), request.longitude(), request.accuracy(), previous);

        DriverDeviationRecord record = new DriverDeviationRecord();
        record.setId(SnowflakeIdGenerator.nextId());
        record.setTripId(trip.getId());
        record.setDriverId(driverId);
        record.setLongitude(request.longitude());
        record.setLatitude(request.latitude());
        record.setDeviationDistance(reading.distanceMeters());
        record.setDeviationStatus(reading.status());
        record.setRecordTime(request.recordTime());
        record.setCreatedAt(now);
        record.setDeleted(0);
        deviationMapper.insert(record);
        return record;
    }

    /**
     * 对真实路线进行偏航判定。
     *
     * <p>先扣除 GPS 精度半径，再通过连续两次轻度偏航与恢复阈值形成迟滞，避免高架、隧道和
     * 定位瞬时漂移触发误报；严重偏航仍会立即告警。</p>
     */
    private DeviationReading calculateDeviation(
            Long tripId, BigDecimal latitude, BigDecimal longitude, BigDecimal accuracy,
            DriverDeviationRecord previous) {
        int rawDistance = calculateDeviationMeters(tripId, latitude, longitude);
        int accuracyMeters = accuracy == null ? 0 : Math.max(0, accuracy.intValue());
        if (accuracyMeters > MAX_RELIABLE_ACCURACY_METERS) {
            return previous == null
                    ? new DeviationReading(0, 0)
                    : new DeviationReading(previous.getDeviationDistance(), previous.getDeviationStatus());
        }

        int adjustedDistance = Math.max(0, rawDistance - accuracyMeters);
        if (adjustedDistance >= SEVERE_DEVIATION_METERS) {
            return new DeviationReading(adjustedDistance, 2);
        }
        if (adjustedDistance >= MILD_DEVIATION_METERS) {
            boolean confirmed = previous != null
                    && (previous.getDeviationStatus() > 0
                    || previous.getDeviationDistance() >= MILD_DEVIATION_METERS);
            return new DeviationReading(adjustedDistance, confirmed ? 1 : 0);
        }
        if (previous != null && previous.getDeviationStatus() > 0
                && adjustedDistance >= RECOVERY_DEVIATION_METERS) {
            return new DeviationReading(adjustedDistance, 1);
        }
        return new DeviationReading(adjustedDistance, 0);
    }

    private int calculateDeviationMeters(Long tripId, BigDecimal latitude, BigDecimal longitude) {
        List<LocationDto> routePoints = readRoutePoints(tripId);
        if (routePoints.isEmpty()) {
            return 0;
        }
        if (routePoints.size() == 1) {
            LocationDto point = routePoints.get(0);
            return haversineMeters(latitude, longitude, point.latitude(), point.longitude());
        }
        int minDistance = Integer.MAX_VALUE;
        for (int i = 1; i < routePoints.size(); i++) {
            minDistance = Math.min(minDistance,
                    distanceToSegmentMeters(latitude, longitude, routePoints.get(i - 1), routePoints.get(i)));
        }
        return minDistance == Integer.MAX_VALUE ? 0 : minDistance;
    }

    /** 使用局部平面近似计算当前位置到 polyline 线段的最短距离。 */
    private int distanceToSegmentMeters(BigDecimal latitude, BigDecimal longitude,
                                        LocationDto from, LocationDto to) {
        double refLat = Math.toRadians(latitude.doubleValue());
        double metersPerLat = 111_320d;
        double metersPerLng = 111_320d * Math.cos(refLat);
        double ax = (from.longitude().doubleValue() - longitude.doubleValue()) * metersPerLng;
        double ay = (from.latitude().doubleValue() - latitude.doubleValue()) * metersPerLat;
        double bx = (to.longitude().doubleValue() - longitude.doubleValue()) * metersPerLng;
        double by = (to.latitude().doubleValue() - latitude.doubleValue()) * metersPerLat;
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 0.0001d) {
            return (int) Math.round(Math.sqrt(ax * ax + ay * ay));
        }
        double t = Math.max(0d, Math.min(1d, -(ax * dx + ay * dy) / lengthSquared));
        double px = ax + t * dx;
        double py = ay + t * dy;
        return (int) Math.round(Math.sqrt(px * px + py * py));
    }

    private List<LocationDto> readRoutePoints(Long tripId) {
        TripRoute route = tripRouteMapper.findByTripId(tripId);
        if (route == null || !StringUtils.hasText(route.getPolyline())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(route.getPolyline(), new TypeReference<List<LocationDto>>() {
            });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    /**
     * 只判断“下一个尚未完成的节点”，禁止越过当前节点直接结算后续节点。
     * 节点在 100 米范围内保持至少 30 秒并形成至少 3 个有效点后记录到达事实。
     */
    private WaypointArrival settleReachedWaypoint(Long tripId, Long driverId,
                                                  BigDecimal latitude, BigDecimal longitude,
                                                  int totalDistance) {
        for (TripWaypoint waypoint : tripWaypointMapper.findByTripId(tripId)) {
            String settleKey = tripId + ":" + driverId + ":TRIP_WAYPOINT:" + waypoint.getId();
            if (distanceMapper.findBySettleKey(settleKey) != null) {
                continue;
            }
            // 当前目标缺少坐标时不能跳过它去结算后续节点。
            if (waypoint.getLat() == null || waypoint.getLng() == null) {
                return null;
            }
            int distance = haversineMeters(latitude, longitude, waypoint.getLat(), waypoint.getLng());
            if (distance > WAYPOINT_ARRIVAL_METERS) {
                return null;
            }
            List<DriverTrackRecord> arrivalEvidence = trackMapper.findRecent(
                    tripId, driverId, LocalDateTime.now().minusSeconds(45)).stream()
                    .filter(point -> point.getAccuracy() == null
                            || point.getAccuracy().intValue() <= MAX_RELIABLE_ACCURACY_METERS)
                    .filter(point -> haversineMeters(
                            point.getLatitude(), point.getLongitude(),
                            waypoint.getLat(), waypoint.getLng()) <= WAYPOINT_ARRIVAL_METERS)
                    .toList();
            if (arrivalEvidence.size() < 3
                    || arrivalEvidence.get(0).getRecordTime()
                    .isAfter(arrivalEvidence.get(arrivalEvidence.size() - 1)
                            .getRecordTime().minusSeconds(30))) {
                return null;
            }
            MileageSettlementResponse result = mileageSettlementService.settleWaypoint(
                    tripId, driverId, waypoint.getId(), waypoint.getPlaceName(), totalDistance);
            if (!Boolean.TRUE.equals(result.duplicate())) {
                return new WaypointArrival(
                        waypoint.getId(), waypoint.getPlaceName(), result.grantedPoints(),
                        arrivalEvidence.get(0).getRecordTime(), arrivalEvidence.size(), distance);
            }
            return null;
        }
        return null;
    }

    private DriverTrackPointVO toPointVO(DriverTrackRecord record) {
        return new DriverTrackPointVO(
                String.valueOf(record.getId()),
                String.valueOf(record.getTripId()),
                String.valueOf(record.getDriverId()),
                record.getLongitude(),
                record.getLatitude(),
                record.getSpeed(),
                record.getDirection(),
                record.getAccuracy(),
                record.getDistanceFromPrev(),
                formatTime(record.getRecordTime())
        );
    }

    private int haversineMeters(BigDecimal fromLat, BigDecimal fromLng, BigDecimal toLat, BigDecimal toLng) {
        double lat1 = Math.toRadians(fromLat.doubleValue());
        double lat2 = Math.toRadians(toLat.doubleValue());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(toLng.doubleValue() - fromLng.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return (int) Math.round(6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    private record DeviationReading(int distanceMeters, int status) {
    }

    private record FilterResult(int distanceMeters, String status, boolean valid) {
    }

    private record WaypointArrival(
            Long waypointId, String waypointName, Integer points,
            LocalDateTime firstInsideAt, int evidenceCount, int distanceMeters) {
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
