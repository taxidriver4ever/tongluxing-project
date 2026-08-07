package com.tongluxing.p0.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.drivertrack.entity.DriverTrackRecord;
import com.tongluxing.drivertrack.entity.TripMemberLatestLocation;
import com.tongluxing.drivertrack.mapper.DriverMemberDistanceAlertMapper;
import com.tongluxing.drivertrack.mapper.DriverTrackRecordMapper;
import com.tongluxing.drivertrack.mapper.TripMemberLatestLocationMapper;
import com.tongluxing.notify.service.AppPushService;
import com.tongluxing.team.entity.Team;
import com.tongluxing.team.entity.TeamMember;
import com.tongluxing.team.mapper.TeamMapper;
import com.tongluxing.team.mapper.TeamMemberMapper;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.mapper.TripArrivalStateMapper;
import com.tongluxing.trip.mapper.TripDepartureExceptionMapper;
import com.tongluxing.trip.mapper.TripMapper;
import com.tongluxing.trip.service.TripService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P0 行程生命周期协调器。
 *
 * <p>该服务只负责跨模块状态编排：位置事实来自 driver-track，队伍配置来自 team，
 * 行程状态修改仍通过 trip 服务完成，关键提醒进入 APP 系统推送。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class P0TripLifecycleCoordinator {

    private static final int ARRIVAL_RADIUS_METERS = 1_000;
    private static final Duration ARRIVAL_DWELL = Duration.ofSeconds(10);
    private static final Duration ARRIVAL_DECISION_TIMEOUT = Duration.ofHours(24);
    /** 自动出发必须使用近期定位，避免用几小时前的旧位置误判成员仍在队伍附近。 */
    private static final Duration DEPARTURE_LOCATION_FRESHNESS = Duration.ofMinutes(30);

    private final TripMapper tripMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final DriverTrackRecordMapper trackMapper;
    private final TripMemberLatestLocationMapper memberLocationMapper;
    private final DriverMemberDistanceAlertMapper alertMapper;
    private final TripDepartureExceptionMapper departureExceptionMapper;
    private final TripArrivalStateMapper arrivalStateMapper;
    private final TripService tripService;
    private final AppPushService appPushService;
    private final TransactionTemplate transactionTemplate;

    /** 每分钟执行一次，所有更新语句都带状态条件，重复扫描不会重复推进状态。 */
    @Scheduled(fixedDelayString = "${p0.trip.lifecycle-scan-ms:60000}")
    public void scan() {
        LocalDateTime now = LocalDateTime.now();
        scanAutoDeparture(now);
        scanRunningTrips(now);
    }

    private void scanAutoDeparture(LocalDateTime now) {
        for (Trip trip : tripMapper.findDueAutoStartTrips(now, 100)) {
            try {
                // 定时任务方法内部调用不会触发 @Transactional 代理，因此显式开启独立事务。
                transactionTemplate.executeWithoutResult(status -> evaluateAutoDeparture(trip, now));
            } catch (RuntimeException ex) {
                log.warn("P0 自动出发扫描失败，tripId={}", trip.getId(), ex);
            }
        }
    }

    private void evaluateAutoDeparture(Trip trip, LocalDateTime now) {
        Team team = teamMapper.findAnyActiveByTripId(trip.getId());
        if (team == null) {
            return;
        }
        List<TeamMember> members = teamMemberMapper.findActiveByTeamId(team.getId());
        Long captainId = trip.getCaptainUserId();
        DriverTrackRecord captain = trackMapper.findLastValid(trip.getId(), captainId);
        if (captain == null || captain.getRecordTime() == null
                || captain.getRecordTime().isBefore(now.minus(DEPARTURE_LOCATION_FRESHNESS))) {
            appPushService.enqueue(captainId, "TRIP_AUTO_START_BLOCKED", "自动出发需要定位",
                    "已到出发时间，请打开同路行 App 上传当前位置", "TRIP", String.valueOf(trip.getId()),
                    "auto-start-captain-location:" + trip.getId());
            return;
        }

        Map<Long, DriverTrackRecord> latest = latestTrackByUser(trip.getId());
        int range = team.getJoinRadiusM() == null ? 100_000 : team.getJoinRadiusM();
        boolean blocked = false;
        for (TeamMember member : members) {
            if (captainId.equals(member.getUserId())) continue;
            DriverTrackRecord point = latest.get(member.getUserId());
            Integer distance = point == null ? null : haversineMeters(
                    captain.getLatitude(), captain.getLongitude(), point.getLatitude(), point.getLongitude());
            boolean stale = point == null || point.getRecordTime() == null
                    || point.getRecordTime().isBefore(now.minus(DEPARTURE_LOCATION_FRESHNESS));
            if (stale || distance > range) {
                blocked = true;
                departureExceptionMapper.upsertPending(
                        SnowflakeIdGenerator.nextId(), trip.getId(), member.getUserId(), distance,
                        stale ? "LOCATION_MISSING" : "OUT_OF_RANGE", now);
            }
        }
        if (blocked) {
            appPushService.enqueue(captainId, "TRIP_DEPARTURE_EXCEPTION", "出发成员范围异常",
                    "部分成员不在约定范围内，请选择等待或直接出发", "TRIP", String.valueOf(trip.getId()),
                    "departure-exception:" + trip.getId() + ":" + now.toLocalDate());
            return;
        }
        departureExceptionMapper.resolveAll(trip.getId(), now);
        List<Long> participantIds = members.stream().map(TeamMember::getUserId).distinct().toList();
        tripService.autoStartTrip(trip.getId(), participantIds);
        for (Long userId : participantIds) {
            appPushService.enqueue(userId, "TRIP_AUTO_STARTED", "行程已自动出发",
                    trip.getTitle() + "已进入行进中", "TRIP", String.valueOf(trip.getId()),
                    "trip-auto-started:" + trip.getId() + ":" + userId);
        }
    }

    private void scanRunningTrips(LocalDateTime now) {
        for (Trip trip : tripMapper.findRunningTrips(200)) {
            try {
                // 每条行程独立提交，单条脏数据不会导致整批扫描回滚。
                transactionTemplate.executeWithoutResult(status -> {
                    evaluateArrival(trip, now);
                    evaluateMissingLocations(trip, now);
                });
            } catch (RuntimeException ex) {
                log.warn("P0 行进中状态扫描失败，tripId={}", trip.getId(), ex);
            }
        }
    }

    private void evaluateArrival(Trip trip, LocalDateTime now) {
        DriverTrackRecord captain = trackMapper.findLastValid(trip.getId(), trip.getCaptainUserId());
        if (captain == null || trip.getEndLatitude() == null || trip.getEndLongitude() == null) return;
        int distance = haversineMeters(captain.getLatitude(), captain.getLongitude(),
                trip.getEndLatitude(), trip.getEndLongitude());
        String state = trip.getArrivalStatus() == null ? "NOT_ARRIVED" : trip.getArrivalStatus();
        if (distance <= ARRIVAL_RADIUS_METERS) {
            if (List.of("NOT_ARRIVED", "CONTINUING").contains(state)) {
                tripMapper.markArrivalDwelling(trip.getId(), now);
                arrivalStateMapper.upsert(SnowflakeIdGenerator.nextId(), trip.getId(), trip.getCaptainUserId(),
                        "DWELLING", now, null, null, distance, null, null, now);
                return;
            }
            if ("DWELLING".equals(state) && trip.getArrivalEnteredAt() != null
                    && !trip.getArrivalEnteredAt().isAfter(now.minus(ARRIVAL_DWELL))) {
                LocalDateTime deadline = now.plus(ARRIVAL_DECISION_TIMEOUT);
                tripMapper.markArrivalPending(trip.getId(), trip.getArrivalEnteredAt(), deadline);
                arrivalStateMapper.upsert(SnowflakeIdGenerator.nextId(), trip.getId(), trip.getCaptainUserId(),
                        "AWAITING_DECISION", trip.getArrivalEnteredAt(), now, deadline, distance, null, null, now);
                appPushService.enqueue(trip.getCaptainUserId(), "TRIP_ARRIVED", "已到达终点",
                        "请选择结束行程或设置新终点继续行程", "TRIP", String.valueOf(trip.getId()),
                        "trip-arrived:" + trip.getId() + ":" + trip.getContinueCount());
            }
        } else if ("DWELLING".equals(state)) {
            tripMapper.resetArrivalDwelling(trip.getId(), now);
            arrivalStateMapper.reset(trip.getId(), distance, now);
        }
        if ("AWAITING_DECISION".equals(state) && trip.getArrivalDecisionDeadline() != null
                && !trip.getArrivalDecisionDeadline().isAfter(now)) {
            tripService.autoFinishArrival(trip.getId());
            arrivalStateMapper.upsert(SnowflakeIdGenerator.nextId(), trip.getId(), trip.getCaptainUserId(),
                    "ENDED", trip.getArrivalEnteredAt(), null, trip.getArrivalDecisionDeadline(), distance,
                    "AUTO_END", now, now);
            appPushService.enqueue(trip.getCaptainUserId(), "TRIP_AUTO_ENDED", "行程已自动结束",
                    "到达后超过 24 小时未处理，系统已自动结束行程", "TRIP", String.valueOf(trip.getId()),
                    "trip-auto-ended:" + trip.getId());
        }
    }

    private void evaluateMissingLocations(Trip trip, LocalDateTime now) {
        Team team = teamMapper.findAnyActiveByTripId(trip.getId());
        if (team == null) return;
        int missingMinutes = team.getMissingLocationMinutes() == null ? 720 : team.getMissingLocationMinutes();
        Map<Long, TripMemberLatestLocation> latest = latestMemberLocations(trip.getId());
        for (TeamMember member : teamMemberMapper.findActiveByTeamId(team.getId())) {
            if (trip.getCaptainUserId().equals(member.getUserId())) continue;
            TripMemberLatestLocation point = latest.get(member.getUserId());
            LocalDateTime lastSeenAt = point == null ? trip.getActualStartTime() : point.getRecordTime();
            if (lastSeenAt != null && lastSeenAt.isBefore(now.minusMinutes(missingMinutes))) {
                Map<String, Object> active = alertMapper.findActive(trip.getId(), member.getUserId());
                Long alertId;
                if (active == null) {
                    alertId = SnowflakeIdGenerator.nextId();
                    alertMapper.insert(alertId, trip.getId(), trip.getCaptainUserId(),
                            member.getUserId(), "MISSING", 0, now);
                } else {
                    alertId = ((Number) active.get("id")).longValue();
                    if (!"MISSING".equals(String.valueOf(active.get("alertLevel")))) {
                        // 成员从“脱队”进一步变为“失联”时升级同一条未处理异常。
                        alertMapper.update(alertId, "MISSING", 0, 1, now);
                    }
                }
                appPushService.enqueue(trip.getCaptainUserId(), "TEAM_MEMBER_MISSING", "成员长时间失联",
                        "有成员超过 " + missingMinutes + " 分钟未上传位置，请进入队长管理页处理",
                        "TRIP", String.valueOf(trip.getId()),
                        "member-missing:" + trip.getId() + ":" + member.getUserId()
                                + ":" + now.toLocalDate());
            }
        }
    }

    private Map<Long, DriverTrackRecord> latestTrackByUser(Long tripId) {
        Map<Long, DriverTrackRecord> result = new HashMap<>();
        for (DriverTrackRecord point : trackMapper.findLatestValidByTripId(tripId)) {
            result.put(point.getDriverId(), point);
        }
        return result;
    }

    private Map<Long, TripMemberLatestLocation> latestMemberLocations(Long tripId) {
        Map<Long, TripMemberLatestLocation> result = new HashMap<>();
        for (TripMemberLatestLocation point : memberLocationMapper.findByTripId(tripId)) {
            result.put(point.getMemberUserId(), point);
        }
        return result;
    }

    private int haversineMeters(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double earth = 6_371_000D;
        double p1 = Math.toRadians(lat1.doubleValue());
        double p2 = Math.toRadians(lat2.doubleValue());
        double deltaLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double deltaLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        return (int) Math.round(earth * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }
}
