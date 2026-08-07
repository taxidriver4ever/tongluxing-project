package com.tongluxing.drivertrack.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.drivertrack.config.TrajectoryProperties;
import com.tongluxing.drivertrack.dto.DriverTrackBatchRequest;
import com.tongluxing.drivertrack.dto.DriverTrackPointRequest;
import com.tongluxing.drivertrack.dto.MockDeviationRequest;
import com.tongluxing.drivertrack.entity.DriverDeviationRecord;
import com.tongluxing.drivertrack.entity.DriverTrackRecord;
import com.tongluxing.drivertrack.entity.DriverTrackDistanceRecord;
import com.tongluxing.drivertrack.entity.TripMemberLatestLocation;
import com.tongluxing.drivertrack.mapper.DriverDeviationRecordMapper;
import com.tongluxing.drivertrack.mapper.DriverTrackRecordMapper;
import com.tongluxing.drivertrack.mapper.DriverTrackDistanceRecordMapper;
import com.tongluxing.drivertrack.mapper.DriverMemberDistanceAlertMapper;
import com.tongluxing.drivertrack.mapper.TripExecutionTrackMapper;
import com.tongluxing.drivertrack.mapper.TripTrackRiskMapper;
import com.tongluxing.drivertrack.mapper.TripMemberLatestLocationMapper;
import com.tongluxing.drivertrack.service.DriverTrackService;
import com.tongluxing.drivertrack.service.MileageSettlementService;
import com.tongluxing.drivertrack.service.TripTrackSecurityAuditService;
import com.tongluxing.drivertrack.support.TrajectoryRuleEngine;
import com.tongluxing.drivertrack.vo.DriverDeviationResponse;
import com.tongluxing.drivertrack.vo.DriverDistanceResponse;
import com.tongluxing.drivertrack.vo.DriverTrackListResponse;
import com.tongluxing.drivertrack.vo.DriverTrackPointVO;
import com.tongluxing.drivertrack.vo.DriverTrackUploadResponse;
import com.tongluxing.drivertrack.vo.MileageSettlementResponse;
import com.tongluxing.map.dto.LocationDto;
import com.tongluxing.notify.service.AppPushService;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.entity.TripRoute;
import com.tongluxing.trip.mapper.TripMapper;
import com.tongluxing.team.entity.Team;
import com.tongluxing.team.mapper.TeamMapper;
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
    private static final int MILD_DEVIATION_METERS = 100;
    private static final int SEVERE_DEVIATION_METERS = 500;
    private static final int RECOVERY_DEVIATION_METERS = 60;

    private final CurrentUserContext currentUserContext;
    private final ObjectMapper objectMapper;
    private final TripMapper tripMapper;
    private final TeamMapper teamMapper;
    private final TripRouteMapper tripRouteMapper;
    private final TripWaypointMapper tripWaypointMapper;
    private final TripMemberSnapshotMapper tripMemberSnapshotMapper;
    private final DriverTrackRecordMapper trackMapper;
    private final DriverDeviationRecordMapper deviationMapper;
    private final DriverTrackDistanceRecordMapper distanceMapper;
    private final DriverMemberDistanceAlertMapper memberAlertMapper;
    private final TripMemberLatestLocationMapper memberLocationMapper;
    private final TripExecutionTrackMapper executionTrackMapper;
    private final MileageSettlementService mileageSettlementService;
    private final TrajectoryProperties trajectoryProperties;
    private final TripTrackRiskMapper riskMapper;
    private final TripTrackSecurityAuditService securityAuditService;
    private final AppPushService appPushService;

    @Override
    @Transactional
    public DriverTrackUploadResponse uploadPoint(DriverTrackPointRequest request) {
        Long driverId = currentUserContext.requireUserId();
        Trip trip = requireOngoingParticipantTrip(request.tripId(), driverId);
        Long captainUserId = captainUserId(trip);
        if (!driverId.equals(captainUserId)) {
            return uploadMemberPosition(trip, request, driverId, captainUserId);
        }
        DriverTrackRecord previousRaw = trackMapper.findLast(request.tripId(), driverId);
        DriverTrackRecord previousValid = trackMapper.findLastValid(request.tripId(), driverId);
        FilterResult filter = filterPoint(previousRaw, previousValid, request);

        LocalDateTime now = LocalDateTime.now();
        DriverTrackRecord record = new DriverTrackRecord();
        record.setId(SnowflakeIdGenerator.nextId());
        record.setTripId(request.tripId());
        record.setDriverId(driverId);
        record.setLongitude(request.longitude());
        record.setLatitude(request.latitude());
        record.setAltitude(request.altitude());
        record.setSpeed(request.speed());
        record.setDirection(request.direction());
        record.setAccuracy(request.accuracy());
        record.setRawDistanceFromPrev(filter.rawDistanceMeters());
        record.setDistanceFromPrev(filter.distanceMeters());
        record.setCalculatedSpeedKmh(BigDecimal.valueOf(filter.calculatedSpeedKmh()).setScale(2, RoundingMode.HALF_UP));
        record.setProvider(normalizeProvider(request.provider()));
        record.setAppState(normalizeAppState(request.appState()));
        record.setBatteryLevel(request.batteryLevel());
        record.setDeviceId(request.deviceId());
        record.setSequenceNo(request.sequenceNo());
        record.setMockLocation(Boolean.TRUE.equals(request.mockLocation()) ? 1 : 0);
        record.setPointStatus(filter.status());
        record.setValidPoint(filter.valid() ? 1 : 0);
        record.setRiskScore(filter.riskScore());
        record.setRiskFlags(filter.riskFlags());
        record.setRejectReason(filter.rejectReason());
        record.setRecordTime(request.recordTime());
        record.setClientSendTime(request.clientSendTime());
        record.setServerReceiveTime(now);
        record.setCreatedAt(now);
        record.setDeleted(0);
        try {
            trackMapper.insert(record);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "该轨迹点已上传，请勿重复提交");
        }

        Long executionId = ensureExecutionTrack(trip, driverId, record, filter.rawDistanceMeters(), now);
        saveRiskResult(trip, driverId, previousValid, record, filter, now);

        WaypointArrival waypointArrival = null;
        if (filter.valid() && !filter.gap()) {
            saveMemberDistanceState(trip, request, driverId, now);
            if (riskMapper.countFatalAnomaliesSince(
                    request.tripId(), driverId,
                    request.recordTime().minusSeconds(trajectoryProperties.getGapSegmentMaxSeconds())) == 0) {
                waypointArrival = settleReachedWaypoint(
                        request.tripId(), driverId, request.latitude(), request.longitude(),
                        request.recordTime(), trackMapper.sumDistance(request.tripId(), driverId));
            }
            if (waypointArrival != null) {
                executionTrackMapper.insertWaypointArrival(
                        SnowflakeIdGenerator.nextId(), executionId, trip.getId(),
                        waypointArrival.waypointId(), driverId,
                        waypointArrival.firstInsideAt(), request.recordTime(),
                        waypointArrival.evidenceCount(), waypointArrival.distanceMeters());
            }
        }

        int totalDistance = trackMapper.sumDistance(request.tripId(), driverId);
        MileageSettlementResponse mileageResult = mileageSettlementService
                .settleMileage(request.tripId(), driverId, totalDistance);
        java.util.Map<String, Object> summary = riskMapper.findSummary(request.tripId());
        String riskLevel = summary == null ? "LOW" : String.valueOf(summary.get("riskLevel"));
        boolean reviewRequired = summary != null
                && !"LOW".equalsIgnoreCase(riskLevel);

        // riskLevel 是整趟行程累计状态。一旦历史点把它提升到 MEDIUM/HIGH，
        // 后续正常的 ACCEPTED/STATIONARY 点也会一直保持 reviewRequired=true。
        // 不能因此在每次正常定位上传后重复返回警告文案。
        boolean currentPointNeedsAttention = filter.fatal()
                || filter.riskScore() > 0
                || !filter.valid()
                || java.util.Set.of(
                        "IMPOSSIBLE_SPEED",
                        "TELEPORT",
                        "FATAL_REJECTED",
                        "ROUND_TRIP_RECOVERY",
                        "TIME_ANOMALY",
                        "TIME_REVERSED",
                        "DUPLICATE_POINT",
                        "LOW_ACCURACY",
                        "LOW_CONFIDENCE_REJECTED",
                        "LOCATION_GAP")
                .contains(filter.status());

        String message = filter.fatal()
                ? "部分轨迹数据异常，结算需要审核"
                : currentPointNeedsAttention && reviewRequired
                ? "部分轨迹数据异常，结算可能需要审核"
                : "";

        return new DriverTrackUploadResponse(
                String.valueOf(record.getId()),
                filter.distanceMeters(),
                0,
                0,
                totalDistance,
                mileageResult.settledStages() + (waypointArrival == null ? 0 : 1),
                mileageResult.grantedPoints() + (waypointArrival == null ? 0 : waypointArrival.points()),
                waypointArrival == null ? null : String.valueOf(waypointArrival.waypointId()),
                waypointArrival == null ? null : waypointArrival.waypointName(),
                filter.status(),
                summary == null || summary.get("riskScore") == null
                        ? filter.riskScore() : ((Number) summary.get("riskScore")).intValue(),
                riskLevel,
                reviewRequired,
                message,
                filter.valid()
        );
    }

    /**
     * 普通成员只更新最新位置快照，不落驾驶轨迹、不累计里程，也不进入成长值风控。
     * 重传的旧位置由服务端确认后直接忽略，客户端可安全清除对应本地缓存。
     */
    private DriverTrackUploadResponse uploadMemberPosition(
            Trip trip, DriverTrackPointRequest request, Long memberUserId, Long captainUserId) {
        LocalDateTime now = LocalDateTime.now();
        if (request.recordTime().isAfter(
                now.plusSeconds(trajectoryProperties.getMaxFutureLocationSeconds()))
                || request.clientSendTime() != null
                && request.clientSendTime().isBefore(request.recordTime())) {
            return memberPositionResponse(trip, memberUserId, request,
                    "TIME_ANOMALY", false, 0, 0, "定位采集时间异常，位置未更新");
        }
        if (request.latitude().compareTo(BigDecimal.ZERO) == 0
                && request.longitude().compareTo(BigDecimal.ZERO) == 0) {
            return memberPositionResponse(trip, memberUserId, request,
                    "INVALID_COORDINATE", false, 0, 0, "经纬度无效，位置未更新");
        }
        if (request.accuracy().intValue() > trajectoryProperties.getAcceptableAccuracyMeters()) {
            return memberPositionResponse(trip, memberUserId, request,
                    "LOW_ACCURACY", false, 0, 0, "定位精度过低，位置未更新");
        }
        if (Boolean.TRUE.equals(request.mockLocation())) {
            return memberPositionResponse(trip, memberUserId, request,
                    "MOCK_LOCATION", false, 0, 0, "疑似模拟定位，位置未更新");
        }
        TripMemberLatestLocation previous = memberLocationMapper.findOne(trip.getId(), memberUserId);
        if (previous != null && previous.getRecordTime() != null
                && !request.recordTime().isAfter(previous.getRecordTime())) {
            int captainDistance = trackMapper.sumDistance(trip.getId(), captainUserId);
            return memberPositionResponse(trip, memberUserId, request,
                    "STALE_POSITION_CONFIRMED", false, 0, captainDistance,
                    "历史位置重传已确认，不覆盖成员最新位置");
        }
        memberLocationMapper.upsert(
                SnowflakeIdGenerator.nextId(), trip.getId(), captainUserId, memberUserId,
                request.longitude(), request.latitude(), request.speed(), request.accuracy(),
                request.sequenceNo(), Boolean.TRUE.equals(request.mockLocation()) ? 1 : 0,
                request.recordTime(), now, now);
        MemberDistanceReading reading = saveMemberDistanceState(
                trip, request, memberUserId, now);
        int captainDistance = trackMapper.sumDistance(trip.getId(), captainUserId);
        return memberPositionResponse(trip, memberUserId, request,
                "MEMBER_POSITION_UPDATED", true, reading.status(), captainDistance, "");
    }

    private DriverTrackUploadResponse memberPositionResponse(
            Trip trip, Long memberUserId, DriverTrackPointRequest request,
            String status, boolean accepted, int deviationStatus,
            int captainDistance, String message) {
        TripMemberLatestLocation latest = memberLocationMapper.findOne(trip.getId(), memberUserId);
        DriverTrackRecord captain = trackMapper.findLastValid(trip.getId(), captainUserId(trip));
        int deviationDistance = latest == null || captain == null ? 0 : haversineMeters(
                latest.getLatitude(), latest.getLongitude(),
                captain.getLatitude(), captain.getLongitude());
        return new DriverTrackUploadResponse(
                "member-position:" + memberUserId + ":" + request.sequenceNo(),
                0, deviationStatus, deviationDistance, captainDistance,
                0, 0, null, null, status, 0, "LOW", false, message, accepted);
    }

    private Long captainUserId(Trip trip) {
        return trip.getCaptainUserId() == null ? trip.getUserId() : trip.getCaptainUserId();
    }

    private Long ensureExecutionTrack(
            Trip trip, Long driverId, DriverTrackRecord record,
            int rawDistanceFromPrev, LocalDateTime now) {
        int plannedDistance = trip.getTotalDistanceMeters() != null
                ? trip.getTotalDistanceMeters()
                : trip.getRouteDistance() == null ? 0 : trip.getRouteDistance();
        Long captainUserId = captainUserId(trip);
        executionTrackMapper.ensureExecution(
                SnowflakeIdGenerator.nextId(), trip.getId(), captainUserId, plannedDistance, now);
        Long executionId = executionTrackMapper.findExecutionId(trip.getId());
        executionTrackMapper.ensureMember(
                SnowflakeIdGenerator.nextId(), executionId, trip.getId(), driverId,
                driverId.equals(captainUserId) ? "CAPTAIN" : "MEMBER", now);
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
        Trip trip = tripMapper.findById(tripId);
        Long captainUserId = captainUserId(trip);
        return new DriverTrackListResponse(
                String.valueOf(tripId),
                trackMapper.findByTripAndDriver(tripId, captainUserId, 5000)
                        .stream().map(this::toPointVO).toList()
        );
    }

    @Override
    public DriverDeviationResponse getDeviation(Long tripId) {
        Trip trip = requireReadableTrip(tripId);
        Long userId = currentUserContext.requireUserId();
        Long captainUserId = captainUserId(trip);
        if (userId.equals(captainUserId)) {
            return new DriverDeviationResponse(String.valueOf(tripId), 0, 0, "");
        }
        TripMemberLatestLocation member = memberLocationMapper.findOne(tripId, userId);
        DriverTrackRecord captain = trackMapper.findLastValid(tripId, captainUserId);
        if (member == null || captain == null) {
            return new DriverDeviationResponse(String.valueOf(tripId), 3, 0, "");
        }
        int distance = haversineMeters(
                member.getLatitude(), member.getLongitude(),
                captain.getLatitude(), captain.getLongitude());
        java.util.Map<String, Object> active = memberAlertMapper.findActive(tripId, userId);
        String level = active == null ? "" : String.valueOf(active.get("alertLevel"));
        int status = "SEVERE".equals(level) ? 2 : "FAR".equals(level) ? 1 : 0;
        if (member.getRecordTime() == null
                || member.getRecordTime().isBefore(LocalDateTime.now().minusHours(12))) {
            status = 3;
        }
        return new DriverDeviationResponse(
                String.valueOf(tripId), status, distance,
                member.getRecordTime() == null ? "" : formatTime(member.getRecordTime()));
    }

    @Override
    public DriverDistanceResponse getDistance(Long tripId) {
        Long requesterId = currentUserContext.requireUserId();
        Trip trip = requireReadableTrip(tripId);
        Long captainUserId = captainUserId(trip);
        int totalDistance = trackMapper.sumDistance(tripId, captainUserId);
        DriverTrackDistanceRecord latest = distanceMapper.findLatest(tripId, captainUserId);
        return new DriverDistanceResponse(
                String.valueOf(tripId),
                String.valueOf(requesterId),
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
            securityAuditService.record(tripId, userId, "UNAUTHORIZED_UPLOAD",
                    "非行程成员尝试上传定位点");
            throw new BusinessException(ResultCode.FORBIDDEN, "只有本次行程的有效成员可以上传位置");
        }
        if (!STATUS_RUNNING.equals(trip.getStatus())) {
            securityAuditService.record(tripId, userId, "UPLOAD_OUTSIDE_RUNNING_TRIP",
                    "行程状态为" + trip.getStatus() + "，拒绝轨迹上传");
            throw new BusinessException(ResultCode.BAD_REQUEST, "行程未开始或已经结束，不能上传位置");
        }
        return trip;
    }

    private FilterResult filterPoint(
            DriverTrackRecord previousRaw,
            DriverTrackRecord previousValid,
            DriverTrackPointRequest request) {
        LocalDateTime validationNow = LocalDateTime.now();
        if (request.recordTime().isAfter(
                validationNow.plusSeconds(trajectoryProperties.getMaxFutureLocationSeconds()))
                || request.clientSendTime() != null
                && request.clientSendTime().isBefore(request.recordTime())) {
            return FilterResult.rejected("TIME_ANOMALY", 0, 2,
                    "TIME_ANOMALY", "定位采集时间异常", false);
        }
        if (request.latitude().compareTo(BigDecimal.ZERO) == 0
                && request.longitude().compareTo(BigDecimal.ZERO) == 0) {
            return FilterResult.rejected("INVALID_COORDINATE", 0, 2,
                    "INVALID_COORDINATE", "经纬度不能同时为0,0", false);
        }
        if (previousRaw != null && !request.recordTime().isAfter(previousRaw.getRecordTime())) {
            return FilterResult.rejected("TIME_REVERSED", 0, 2,
                    "TIME_REVERSED", "定位时间倒退或数据重放", false);
        }
        if (previousRaw != null && request.sequenceNo() != null
                && previousRaw.getSequenceNo() != null
                && java.util.Objects.equals(request.deviceId(), previousRaw.getDeviceId())
                && request.sequenceNo() <= previousRaw.getSequenceNo()) {
            return FilterResult.rejected("DUPLICATE_POINT", 0, 2,
                    "DUPLICATE_POINT", "sequenceNo重复或倒退", false);
        }
        int accuracy = request.accuracy().intValue();
        if (accuracy > trajectoryProperties.getAcceptableAccuracyMeters()) {
            return FilterResult.rejected("LOW_ACCURACY", 0, 0,
                    "LOW_ACCURACY", "定位精度超过允许上限", false);
        }
        if (previousValid == null) {
            int mockRisk = Boolean.TRUE.equals(request.mockLocation()) ? 5 : 0;
            return new FilterResult(0, 0, "ACCEPTED", true, mockRisk,
                    mockRisk > 0 ? "MOCK_LOCATION" : "", null, false, false, 0d);
        }

        long deltaSeconds = java.time.Duration.between(
                previousValid.getRecordTime(), request.recordTime()).getSeconds();
        if (deltaSeconds < trajectoryProperties.getMinSegmentSeconds()) {
            return FilterResult.rejected("TOO_FREQUENT", 0, 0,
                    "TOO_FREQUENT", "两点采集间隔小于2秒", false);
        }
        int rawDistance = haversineMeters(
                previousValid.getLatitude(), previousValid.getLongitude(),
                request.latitude(), request.longitude());
        if (deltaSeconds > trajectoryProperties.getGapSegmentMaxSeconds()) {
            int risk = 1 + (Boolean.TRUE.equals(request.mockLocation()) ? 5 : 0);
            return new FilterResult(0, rawDistance, "LOCATION_GAP", true, risk,
                    joinFlags("LOCATION_GAP", Boolean.TRUE.equals(request.mockLocation()) ? "MOCK_LOCATION" : null),
                    "定位中断超过60秒，当前点仅作为新轨迹段起点", false, true, 0d);
        }

        int previousAccuracy = previousValid.getAccuracy() == null
                ? 0 : Math.max(0, previousValid.getAccuracy().intValue());
        int lowerBoundDistance = TrajectoryRuleEngine.lowerBoundDistance(
                rawDistance, previousAccuracy, accuracy);
        double calculatedSpeedKmh = TrajectoryRuleEngine.speedKmh(
                lowerBoundDistance, deltaSeconds);

        if (calculatedSpeedKmh > trajectoryProperties.getWarningSpeedMaxKmh()) {
            FilterResult recovery = recoverSegmentStart(previousRaw, request);
            if (recovery != null) return recovery;
            return FilterResult.rejected("IMPOSSIBLE_SPEED", rawDistance,
                    trajectoryProperties.getHighRiskScore(),
                    "IMPOSSIBLE_SPEED",
                    "相邻定位点按实际时间差计算速度超过300km/h，疑似瞬移或极端速度",
                    true, calculatedSpeedKmh);
        }

        int riskScore = 0;
        java.util.List<String> flags = new java.util.ArrayList<>();
        String status = "ACCEPTED";
        if (Boolean.TRUE.equals(request.mockLocation())) {
            riskScore += 5;
            flags.add("MOCK_LOCATION");
        }
        if (accuracy > trajectoryProperties.getNormalAccuracyMeters()) {
            flags.add("LOW_CONFIDENCE");
            status = "LOW_CONFIDENCE";
        }
        if (deltaSeconds > trajectoryProperties.getNormalSegmentMaxSeconds()) {
            boolean bearingConflict = previousValid.getDirection() != null
                    && request.direction() != null
                    && TrajectoryRuleEngine.angularDifferenceDegrees(
                    previousValid.getDirection().doubleValue(), request.direction().doubleValue())
                    > trajectoryProperties.getLowConfidenceMaxBearingChangeDegrees();
            if (accuracy > trajectoryProperties.getLowConfidenceAccuracyMeters()
                    || previousAccuracy > trajectoryProperties.getLowConfidenceAccuracyMeters()
                    || calculatedSpeedKmh > trajectoryProperties.getNormalSpeedMaxKmh()
                    || bearingConflict) {
                return FilterResult.rejected("LOW_CONFIDENCE_REJECTED", rawDistance, riskScore,
                        joinFlags(flags.toArray(String[]::new)),
                        "20至60秒低置信度路段不满足精度或速度要求", false, calculatedSpeedKmh);
            }
            flags.add("LONG_INTERVAL");
            status = "LOW_CONFIDENCE";
        }


        int acceptedDistance = TrajectoryRuleEngine.filterStationaryDrift(
                rawDistance,
                lowerBoundDistance,
                calculatedSpeedKmh,
                request.speed() == null ? Double.NaN : request.speed().doubleValue(),
                previousAccuracy,
                accuracy,
                trajectoryProperties.getStationaryDriftMeters(),
                trajectoryProperties.getStationaryDriftSpeedKmh(),
                trajectoryProperties.getStationaryMaxJumpMeters());
        if (acceptedDistance == 0 && rawDistance > 0) {
            status = "STATIONARY";
        }
        return new FilterResult(acceptedDistance, rawDistance, status, true, riskScore,
                String.join(",", flags), null, false, false, calculatedSpeedKmh);
    }

    private FilterResult detectRoundTripTeleport(
            DriverTrackRecord previousRaw,
            DriverTrackRecord previousValid,
            DriverTrackPointRequest current) {
        if (previousRaw == null || Integer.valueOf(1).equals(previousRaw.getValidPoint())
                || previousRaw.getRecordTime() == null
                || previousRaw.getAccuracy() == null
                || !java.util.Set.of("TELEPORT", "IMPOSSIBLE_SPEED", "FATAL_REJECTED")
                .contains(previousRaw.getPointStatus())) {
            return null;
        }
        long returnSeconds = java.time.Duration.between(
                previousRaw.getRecordTime(), current.recordTime()).getSeconds();
        if (returnSeconds < 0 || returnSeconds > trajectoryProperties.getRoundTripWindowSeconds()) {
            return null;
        }
        int farRawDistance = haversineMeters(
                previousValid.getLatitude(), previousValid.getLongitude(),
                previousRaw.getLatitude(), previousRaw.getLongitude());
        int farLowerBound = TrajectoryRuleEngine.lowerBoundDistance(
                farRawDistance,
                previousValid.getAccuracy() == null ? 0 : previousValid.getAccuracy().intValue(),
                previousRaw.getAccuracy().intValue());
        int returnDistance = haversineMeters(
                previousValid.getLatitude(), previousValid.getLongitude(),
                current.latitude(), current.longitude());
        if (farLowerBound < trajectoryProperties.getRoundTripFarDistanceMeters()
                || returnDistance > trajectoryProperties.getRoundTripReturnRadiusMeters()) {
            return null;
        }
        int mockRisk = Boolean.TRUE.equals(current.mockLocation()) ? 5 : 0;
        return new FilterResult(0, returnDistance, "ROUND_TRIP_RECOVERY", true,
                3 + mockRisk,
                joinFlags("ROUND_TRIP_TELEPORT", mockRisk > 0 ? "MOCK_LOCATION" : null),
                "短时间内出现A到远距离B再返回A的往返瞬移，当前点不补算里程",
                false, false, 0d);
    }

    /**
     * 异常点后若新点与上一原始点形成连续可信轨迹，则以当前点重新建立轨迹段。
     * 该点不补算与上一有效点之间的里程，也不会靠异常点直接触发节点抵达。
     */
    private FilterResult recoverSegmentStart(
            DriverTrackRecord previousRaw, DriverTrackPointRequest current) {
        if (previousRaw == null || Integer.valueOf(1).equals(previousRaw.getValidPoint())
                || previousRaw.getAccuracy() == null
                || previousRaw.getAccuracy().intValue() > trajectoryProperties.getAcceptableAccuracyMeters()) {
            return null;
        }
        long deltaSeconds = java.time.Duration.between(
                previousRaw.getRecordTime(), current.recordTime()).getSeconds();
        if (deltaSeconds < trajectoryProperties.getMinSegmentSeconds()
                || deltaSeconds > trajectoryProperties.getGapSegmentMaxSeconds()) {
            return null;
        }
        int rawDistance = haversineMeters(
                previousRaw.getLatitude(), previousRaw.getLongitude(),
                current.latitude(), current.longitude());
        int lowerBound = TrajectoryRuleEngine.lowerBoundDistance(
                rawDistance, previousRaw.getAccuracy().intValue(), current.accuracy().intValue());
        double speedKmh = TrajectoryRuleEngine.speedKmh(lowerBound, deltaSeconds);
        if (speedKmh > trajectoryProperties.getNormalSpeedMaxKmh()) {
            return null;
        }
        int mockRisk = Boolean.TRUE.equals(current.mockLocation()) ? 5 : 0;
        return new FilterResult(0, rawDistance, "RECOVERY_SEGMENT_START", true, mockRisk,
                joinFlags("TRACK_RECOVERY", mockRisk > 0 ? "MOCK_LOCATION" : null),
                "异常点后从连续可信位置重新建立轨迹段，不补算中间里程",
                false, false, speedKmh);
    }

    private void saveRiskResult(Trip trip, Long driverId, DriverTrackRecord previousValid,
                                DriverTrackRecord record, FilterResult filter, LocalDateTime now) {
        Long captainUserId = captainUserId(trip);
        boolean primaryTrack = driverId.equals(captainUserId);
        if (primaryTrack) {
            riskMapper.ensureSummary(SnowflakeIdGenerator.nextId(), trip.getId(), captainUserId, now);
            int minimumRisk = filter.fatal() ? trajectoryProperties.getHighRiskScore() : 0;
            int warningCount = filter.riskScore() > 0 && !filter.hardAnomaly() ? 1 : 0;
            int hardCount = filter.hardAnomaly() ? 1 : 0;
            String reviewReason = filter.fatal() ? "出现致命轨迹异常，禁止自动结算"
                    : filter.riskScore() >= trajectoryProperties.getMediumRiskScore()
                    ? "轨迹风险分达到人工审核阈值" : null;
            int riskScoreForSummary = filter.riskScore();
            if (filter.gap() && riskMapper.currentGapCount(trip.getId()) >= 3) {
                // 单趟定位中断风险分最多累计3分，模拟定位等其他风险仍照常累计。
                riskScoreForSummary = Math.max(0, riskScoreForSummary - 1);
            }
            riskMapper.appendPoint(
                    trip.getId(), filter.rawDistanceMeters(), filter.distanceMeters(),
                    filter.valid() ? 1 : 0, filter.valid() ? 0 : 1,
                    filter.gap() ? 1 : 0, warningCount, hardCount,
                    riskScoreForSummary, minimumRisk, reviewReason, now);
            riskMapper.refreshRiskLevel(
                    trip.getId(), trajectoryProperties.getMediumRiskScore(),
                    trajectoryProperties.getHighRiskScore(), now);
        }
        if (filter.riskScore() > 0 || !filter.valid() || filter.gap()) {
            String detailJson = "{\"status\":\"" + filter.status() + "\",\"speedKmh\":"
                    + String.format(java.util.Locale.ROOT, "%.2f", filter.calculatedSpeedKmh())
                    + ",\"rawDistanceMeters\":" + filter.rawDistanceMeters()
                    + ",\"primaryTrack\":" + primaryTrack
                    + ",\"reason\":\"" + jsonEscape(filter.rejectReason()) + "\"}";
            riskMapper.insertAnomaly(
                    SnowflakeIdGenerator.nextId(), trip.getId(), driverId,
                    previousValid == null ? null : previousValid.getId(), record.getId(),
                    primaryRiskFlag(filter), filter.riskScore(), detailJson,
                    record.getRecordTime(), now);
        }
        if (primaryTrack && trackMapper.countRecentHardAnomalies(
                trip.getId(), driverId, record.getRecordTime().minusSeconds(60)) >= 3) {
            riskMapper.markAtLeastMedium(
                    trip.getId(), trajectoryProperties.getMediumRiskScore(),
                    "60秒内连续出现3次明显轨迹异常", now);
        }
    }

    private String primaryRiskFlag(FilterResult filter) {
        if (filter.fatal()) return "FATAL_IMPOSSIBLE_SPEED";
        if (StringUtils.hasText(filter.riskFlags())) return filter.riskFlags().split(",")[0];
        return filter.status();
    }

    private String joinFlags(String... values) {
        return java.util.Arrays.stream(values)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
    }

    private String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) return "fused";
        String value = provider.trim().toLowerCase(java.util.Locale.ROOT);
        return java.util.Set.of("gps", "fused", "network").contains(value) ? value : "fused";
    }

    private String normalizeAppState(String appState) {
        if (!StringUtils.hasText(appState)) return "foreground";
        return "background".equalsIgnoreCase(appState) ? "background" : "foreground";
    }

    /**
     * 按成员与队长的实时直线距离进行车队脱队检测。
     * 50km 持续30分钟提醒成员，100km 持续60分钟通知队长；
     * 成员回到50km以内时解除告警，是否移出始终由队长决定。
     */
    private MemberDistanceReading saveMemberDistanceState(
            Trip trip, DriverTrackPointRequest request, Long memberUserId, LocalDateTime now) {
        Long captainUserId = captainUserId(trip);
        DriverTrackRecord captain = trackMapper.findLastValid(trip.getId(), captainUserId);
        if (captain == null || memberUserId.equals(captainUserId)) {
            return new MemberDistanceReading(3, 0);
        }
        Team team = teamMapper.findAnyActiveByTripId(trip.getId());
        int warningDistance = team == null || team.getDeviationWarningDistanceM() == null
                ? 50_000 : team.getDeviationWarningDistanceM();
        int warningMinutes = team == null || team.getDeviationWarningMinutes() == null
                ? 30 : team.getDeviationWarningMinutes();
        int severeDistance = team == null || team.getSevereDeviationDistanceM() == null
                ? 100_000 : team.getSevereDeviationDistanceM();
        int severeMinutes = team == null || team.getSevereDeviationMinutes() == null
                ? 60 : team.getSevereDeviationMinutes();

        int distance = haversineMeters(
                request.latitude(), request.longitude(),
                captain.getLatitude(), captain.getLongitude());
        java.util.Map<String, Object> active = memberAlertMapper.findActive(trip.getId(), memberUserId);
        if (active != null && "MISSING".equals(String.valueOf(active.get("alertLevel")))) {
            memberAlertMapper.recover(((Number) active.get("id")).longValue(), now);
            active = null;
        }
        if (distance < warningDistance) {
            if (active != null) {
                Long alertId = ((Number) active.get("id")).longValue();
                String previousLevel = String.valueOf(active.get("alertLevel"));
                memberAlertMapper.recover(alertId, now);
                if ("FAR".equals(previousLevel) || "SEVERE".equals(previousLevel)) {
                    appPushService.enqueue(memberUserId, "TEAM_MEMBER_RECOVERED", "已恢复车队范围",
                            "你已回到队长50公里范围内，脱队警告已解除",
                            "TRIP", String.valueOf(trip.getId()),
                            "member-distance-recovered:" + alertId);
                }
            }
            return new MemberDistanceReading(0, distance);
        }

        Long alertId;
        LocalDateTime startedAt;
        String currentLevel;
        if (active == null) {
            alertId = SnowflakeIdGenerator.nextId();
            startedAt = request.recordTime();
            currentLevel = "OBSERVING";
            memberAlertMapper.insert(alertId, trip.getId(), captainUserId,
                    memberUserId, currentLevel, distance, startedAt);
        } else {
            alertId = ((Number) active.get("id")).longValue();
            startedAt = (LocalDateTime) active.get("startedAt");
            currentLevel = String.valueOf(active.get("alertLevel"));
        }

        long elapsedMinutes = startedAt == null ? 0
                : Math.max(0, java.time.Duration.between(startedAt, request.recordTime()).toMinutes());
        String targetLevel = currentLevel;
        int status = 0;
        if (distance >= severeDistance && elapsedMinutes >= severeMinutes) {
            targetLevel = "SEVERE";
            status = 2;
        } else if (elapsedMinutes >= warningMinutes) {
            targetLevel = "FAR";
            status = 1;
        } else if ("SEVERE".equals(currentLevel)) {
            targetLevel = "FAR";
            status = 1;
        } else if ("FAR".equals(currentLevel)) {
            status = 1;
        }

        if (!targetLevel.equals(currentLevel) || !"OBSERVING".equals(targetLevel)) {
            boolean notify = !targetLevel.equals(currentLevel)
                    && ("FAR".equals(targetLevel) || "SEVERE".equals(targetLevel));
            memberAlertMapper.update(alertId, targetLevel, distance, notify ? 1 : 0, now);
            if (notify && "FAR".equals(targetLevel)) {
                appPushService.enqueue(memberUserId, "TEAM_MEMBER_FAR", "你已远离车队",
                        "你与队长距离约" + Math.max(1, distance / 1000)
                                + "公里，请尽快归队",
                        "TRIP", String.valueOf(trip.getId()),
                        "member-distance-alert:" + alertId + ":FAR");
            } else if (notify && "SEVERE".equals(targetLevel)) {
                appPushService.enqueue(captainUserId, "TEAM_MEMBER_SEVERE", "成员严重脱队",
                        "有成员与队长距离约" + Math.max(1, distance / 1000)
                                + "公里且已持续1小时，请选择提醒、忽略或移出队伍",
                        "TRIP", String.valueOf(trip.getId()),
                        "member-distance-alert:" + alertId + ":SEVERE");
            }
        }
        return new MemberDistanceReading(status, distance);
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
        if (accuracyMeters > trajectoryProperties.getLowConfidenceAccuracyMeters()) {
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
     * 节点在 100 米范围内保持至少 10 秒并形成至少 2 个有效点后记录到达事实。
     */
    private WaypointArrival settleReachedWaypoint(Long tripId, Long driverId,
                                                  BigDecimal latitude, BigDecimal longitude,
                                                  LocalDateTime currentRecordTime,
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
            if (distance > trajectoryProperties.getWaypointRadiusMeters()) {
                return null;
            }
            List<DriverTrackRecord> arrivalEvidence = trackMapper.findRecent(
                    tripId, driverId, currentRecordTime.minusSeconds(trajectoryProperties.getWaypointEvidenceWindowSeconds())).stream()
                    .filter(point -> Integer.valueOf(1).equals(point.getValidPoint()))
                    .filter(point -> !"LOCATION_GAP".equals(point.getPointStatus()))
                    .filter(point -> point.getAccuracy() == null
                            || point.getAccuracy().intValue()
                            <= trajectoryProperties.getLowConfidenceAccuracyMeters())
                    .filter(point -> haversineMeters(
                            point.getLatitude(), point.getLongitude(),
                            waypoint.getLat(), waypoint.getLng())
                            <= trajectoryProperties.getWaypointRadiusMeters())
                    .toList();
            if (arrivalEvidence.size() < trajectoryProperties.getWaypointMinPoints()
                    || arrivalEvidence.get(0).getRecordTime()
                    .isAfter(arrivalEvidence.get(arrivalEvidence.size() - 1)
                            .getRecordTime().minusSeconds(trajectoryProperties.getWaypointMinDurationSeconds()))) {
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

    private record MemberDistanceReading(int status, int distanceMeters) {
    }

    private record FilterResult(
            int distanceMeters,
            int rawDistanceMeters,
            String status,
            boolean valid,
            int riskScore,
            String riskFlags,
            String rejectReason,
            boolean fatal,
            boolean gap,
            double calculatedSpeedKmh
    ) {
        static FilterResult rejected(String status, int rawDistance, int riskScore,
                                     String riskFlags, String reason, boolean fatal) {
            return rejected(status, rawDistance, riskScore, riskFlags, reason, fatal, 0d);
        }

        static FilterResult rejected(String status, int rawDistance, int riskScore,
                                     String riskFlags, String reason, boolean fatal,
                                     double calculatedSpeedKmh) {
            return new FilterResult(0, rawDistance, status, false, riskScore,
                    riskFlags, reason, fatal, false, calculatedSpeedKmh);
        }

        boolean hardAnomaly() {
            return fatal || java.util.Set.of(
                    "IMPOSSIBLE_SPEED", "TELEPORT", "ROUND_TRIP_TELEPORT",
                    "ABNORMAL_ACCELERATION", "FATAL_REJECTED")
                    .stream().anyMatch(flag -> riskFlags != null && riskFlags.contains(flag));
        }
    }

    private record WaypointArrival(
            Long waypointId, String waypointName, Integer points,
            LocalDateTime firstInsideAt, int evidenceCount, int distanceMeters) {
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
