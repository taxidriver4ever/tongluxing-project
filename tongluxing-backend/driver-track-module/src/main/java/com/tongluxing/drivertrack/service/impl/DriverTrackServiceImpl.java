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
import com.tongluxing.map.dto.LocationDto;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.entity.TripRoute;
import com.tongluxing.trip.mapper.TripMapper;
import com.tongluxing.trip.mapper.TripRouteMapper;
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

    private final CurrentUserContext currentUserContext;
    private final ObjectMapper objectMapper;
    private final TripMapper tripMapper;
    private final TripRouteMapper tripRouteMapper;
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
        mileageSettlementService.settleMileage(request.tripId(), driverId, totalDistance);

        return new DriverTrackUploadResponse(
                String.valueOf(record.getId()),
                distanceFromPrev,
                deviation.getDeviationStatus(),
                deviation.getDeviationDistance(),
                totalDistance
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
        int deviationDistance = calculateDeviationMeters(trip.getId(), request.latitude(), request.longitude());
        int status = deviationDistance >= SEVERE_DEVIATION_METERS ? 2 : deviationDistance >= MILD_DEVIATION_METERS ? 1 : 0;

        DriverDeviationRecord record = new DriverDeviationRecord();
        record.setId(SnowflakeIdGenerator.nextId());
        record.setTripId(trip.getId());
        record.setDriverId(driverId);
        record.setLongitude(request.longitude());
        record.setLatitude(request.latitude());
        record.setDeviationDistance(deviationDistance);
        record.setDeviationStatus(status);
        record.setRecordTime(request.recordTime());
        record.setCreatedAt(now);
        record.setDeleted(0);
        deviationMapper.insert(record);
        return record;
    }

    private int calculateDeviationMeters(Long tripId, BigDecimal latitude, BigDecimal longitude) {
        List<LocationDto> routePoints = readRoutePoints(tripId);
        if (routePoints.isEmpty()) {
            return 0;
        }
        int minDistance = Integer.MAX_VALUE;
        for (LocationDto point : routePoints) {
            minDistance = Math.min(minDistance, haversineMeters(latitude, longitude, point.latitude(), point.longitude()));
        }
        return minDistance == Integer.MAX_VALUE ? 0 : minDistance;
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

    private String formatTime(LocalDateTime value) {
        return value == null ? "" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
