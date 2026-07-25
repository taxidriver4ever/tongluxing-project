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
    private static final int MAX_RELIABLE_ACCURACY_METERS = 150;
    private static final int WAYPOINT_ARRIVAL_METERS = 200;

    private final CurrentUserContext currentUserContext;
    private final ObjectMapper objectMapper;
    private final TripMapper tripMapper;
    private final TripRouteMapper tripRouteMapper;
    private final TripWaypointMapper tripWaypointMapper;
    private final DriverTrackRecordMapper trackMapper;
    private final DriverDeviationRecordMapper deviationMapper;
    private final DriverTrackDistanceRecordMapper distanceMapper;
    private final MileageSettlementService mileageSettlementService;

    @Override
    @Transactional
    public DriverTrackUploadResponse uploadPoint(DriverTrackPointRequest request) {
        Long driverId = currentUserContext.requireUserId();
        Trip trip = requireOngoingOwnerTrip(request.tripId(), driverId);
        DriverTrackRecord previous = trackMapper.findLast(request.tripId(), driverId);
        int distanceFromPrev = previous == null ? 0 : haversineMeters(
                previous.getLatitude(), previous.getLongitude(), request.latitude(), request.longitude());

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
        record.setRecordTime(request.recordTime());
        record.setCreatedAt(now);
        record.setDeleted(0);
        trackMapper.insert(record);

        int totalDistance = trackMapper.sumDistance(request.tripId(), driverId);
        DriverDeviationRecord deviation = saveDeviation(trip, request, driverId, now);
        MileageSettlementResponse mileageResult = mileageSettlementService
                .settleMileage(request.tripId(), driverId, totalDistance);
        WaypointArrival waypointArrival = settleReachedWaypoint(
                request.tripId(), driverId, request.latitude(), request.longitude(), totalDistance);

        return new DriverTrackUploadResponse(
                String.valueOf(record.getId()),
                distanceFromPrev,
                deviation.getDeviationStatus(),
                deviation.getDeviationDistance(),
                totalDistance,
                mileageResult.settledStages() + (waypointArrival == null ? 0 : 1),
                mileageResult.grantedPoints() + (waypointArrival == null ? 0 : waypointArrival.points()),
                waypointArrival == null ? null : String.valueOf(waypointArrival.waypointId()),
                waypointArrival == null ? null : waypointArrival.waypointName()
        );
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
        Long driverId = currentUserContext.requireUserId();
        requireReadableTrip(tripId);
        DriverDeviationRecord record = deviationMapper.findLatest(tripId, driverId);
        if (record == null) {
            return new DriverDeviationResponse(String.valueOf(tripId), 0, 0, "");
        }
        return new DriverDeviationResponse(
                String.valueOf(tripId),
                record.getDeviationStatus(),
                record.getDeviationDistance(),
                formatTime(record.getRecordTime())
        );
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
        Trip trip = requireOngoingOwnerTrip(tripId, driverId);
        int status = request.deviationStatus();
        int distance = status == 2 ? 680 : status == 1 ? 180 : 0;
        LocalDateTime now = LocalDateTime.now();
        DriverDeviationRecord record = new DriverDeviationRecord();
        record.setId(SnowflakeIdGenerator.nextId());
        record.setTripId(tripId);
        record.setDriverId(driverId);
        record.setLongitude(trip.getStartLongitude() != null ? trip.getStartLongitude() : trip.getStartLng());
        record.setLatitude(trip.getStartLatitude() != null ? trip.getStartLatitude() : trip.getStartLat());
        record.setDeviationDistance(distance);
        record.setDeviationStatus(status);
        record.setRecordTime(now);
        record.setCreatedAt(now);
        record.setDeleted(0);
        deviationMapper.insert(record);
        return new DriverDeviationResponse(String.valueOf(tripId), status, distance, formatTime(now));
    }

    private Trip requireOngoingOwnerTrip(Long tripId, Long userId) {
        Trip trip = tripMapper.findById(tripId);
        if (trip == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        if (!trip.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有司机可以上传轨迹");
        }
        if (!STATUS_RUNNING.equals(trip.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "行程未开始，不能上传轨迹");
        }
        return trip;
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

    /** 到达任一尚未结算的途经点时立即发放一次成长值。 */
    private WaypointArrival settleReachedWaypoint(Long tripId, Long driverId,
                                                  BigDecimal latitude, BigDecimal longitude,
                                                  int totalDistance) {
        for (TripWaypoint waypoint : tripWaypointMapper.findByTripId(tripId)) {
            if (waypoint.getLat() == null || waypoint.getLng() == null) {
                continue;
            }
            int distance = haversineMeters(latitude, longitude, waypoint.getLat(), waypoint.getLng());
            if (distance > WAYPOINT_ARRIVAL_METERS) {
                continue;
            }
            MileageSettlementResponse result = mileageSettlementService.settleWaypoint(
                    tripId, driverId, waypoint.getId(), waypoint.getPlaceName(), totalDistance);
            if (!Boolean.TRUE.equals(result.duplicate())) {
                return new WaypointArrival(waypoint.getId(), waypoint.getPlaceName(), result.grantedPoints());
            }
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

    private record WaypointArrival(Long waypointId, String waypointName, Integer points) {
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
