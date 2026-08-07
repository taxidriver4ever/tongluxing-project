package com.tongluxing.p0.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.drivertrack.entity.DriverTrackRecord;
import com.tongluxing.drivertrack.entity.TripMemberLatestLocation;
import com.tongluxing.drivertrack.mapper.DriverMemberDistanceAlertMapper;
import com.tongluxing.drivertrack.mapper.DriverTrackRecordMapper;
import com.tongluxing.drivertrack.mapper.TripMemberLatestLocationMapper;
import com.tongluxing.notify.service.AppPushService;
import com.tongluxing.p0.dto.DepartureExceptionActionRequest;
import com.tongluxing.p0.dto.TeamAlertActionRequest;
import com.tongluxing.p0.vo.MapHomeStateResponse;
import com.tongluxing.p0.vo.MapMemberPositionResponse;
import com.tongluxing.team.dto.RemoveTeamMemberRequest;
import com.tongluxing.team.entity.Team;
import com.tongluxing.team.entity.TeamMember;
import com.tongluxing.team.mapper.TeamMapper;
import com.tongluxing.team.mapper.TeamMemberMapper;
import com.tongluxing.team.service.TeamService;
import com.tongluxing.team.vo.TeamResponse;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.mapper.TripDepartureExceptionMapper;
import com.tongluxing.trip.mapper.TripMapper;
import com.tongluxing.trip.service.TripService;
import com.tongluxing.trip.vo.ArrivalDecisionResponse;
import com.tongluxing.trip.vo.TripResponse;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * P0 首页地图与队长异常处理聚合服务。
 *
 * <p>该服务把行程、车队、轨迹和异常记录组合成 App 可以一次加载的状态，
 * 避免地图首页为了判断三种模式并发调用多个模块。</p>
 */
@Service
@RequiredArgsConstructor
public class P0TripStateService {

    private final CurrentUserContext currentUserContext;
    private final TripMapper tripMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final DriverTrackRecordMapper trackMapper;
    private final TripMemberLatestLocationMapper memberLocationMapper;
    private final DriverMemberDistanceAlertMapper alertMapper;
    private final TripDepartureExceptionMapper departureExceptionMapper;
    private final TripService tripService;
    private final TeamService teamService;
    private final AppPushService appPushService;

    /**
     * 返回首页地图状态机快照。
     *
     * <p>NORMAL 表示没有当前行程；PREVIEW 表示出发前预览；RUNNING 表示行程中。
     * 乘客自己发布的出行需求没有队长和车队，仍以 PREVIEW 模式展示需求卡片。</p>
     */
    public MapHomeStateResponse getMapHomeState() {
        Long userId = currentUserContext.requireUserId();
        TeamResponse team = resolveCurrentTeam(userId);
        TripResponse trip = resolveCurrentTrip(userId, team);
        if (trip == null) {
            return new MapHomeStateResponse(
                    "NORMAL", null, null, null, null,
                    List.of(), List.of(), List.of(), null);
        }

        boolean running = List.of("RUNNING", "ONGOING").contains(trip.status());
        String mode = running ? "RUNNING" : "PREVIEW";
        Long countdown = running ? 0L : departureCountdown(trip.departureTime());
        List<MapMemberPositionResponse> positions = running
                ? memberPositions(Long.valueOf(trip.tripId()), trip.captainUserId())
                : List.of();
        List<Map<String, Object>> alerts = running
                ? alertMapper.findPendingByTrip(Long.valueOf(trip.tripId()))
                : List.of();
        List<Map<String, Object>> departures = running
                ? List.of()
                : departureExceptionMapper.findPending(Long.valueOf(trip.tripId()));
        ArrivalDecisionResponse arrival = running
                ? tripService.getArrivalDecision(Long.valueOf(trip.tripId()))
                : null;

        return new MapHomeStateResponse(
                mode,
                trip,
                team,
                countdown,
                team == null ? null : team.chatConversationId(),
                positions,
                alerts,
                departures,
                arrival);
    }

    /** 查询指定行程尚未处理的出发异常，仅队长可读。 */
    public List<Map<String, Object>> getDepartureExceptions(Long tripId) {
        requireCaptain(tripId);
        return departureExceptionMapper.findPending(tripId);
    }

    /**
     * 队长处理自动出发异常。
     * WAIT 只记录等待；CONTINUE 会忽略异常并立即幂等启动行程。
     */
    @Transactional
    public TripResponse handleDepartureExceptions(Long tripId, DepartureExceptionActionRequest request) {
        Trip trip = requireCaptain(tripId);
        LocalDateTime now = LocalDateTime.now();
        departureExceptionMapper.handleAll(tripId, request.action(), now);
        if ("WAIT".equals(request.action())) {
            return tripService.getTrip(tripId);
        }

        // 直接出发仍保留现有有效成员，后续由脱队/失联规则继续监测并交给队长处理。
        Team team = teamMapper.findAnyActiveByTripId(tripId);
        List<Long> participantIds = team == null
                ? List.of(trip.getCaptainUserId())
                : teamMemberMapper.findActiveByTeamId(team.getId()).stream()
                        .map(TeamMember::getUserId)
                        .distinct()
                        .toList();
        return tripService.autoStartTrip(tripId, participantIds);
    }

    /** 查询行程中队长尚未处理的一级、严重脱队及失联预警。 */
    public List<Map<String, Object>> getTeamAlerts(Long tripId) {
        requireCaptain(tripId);
        return alertMapper.findPendingByTrip(tripId);
    }

    /** 队长忽略预警或移除异常成员，形成半自动脱队处理闭环。 */
    @Transactional
    public Map<String, Object> handleTeamAlert(Long alertId, TeamAlertActionRequest request) {
        Long operatorUserId = currentUserContext.requireUserId();
        Map<String, Object> alert = alertMapper.findById(alertId);
        if (alert == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "异常记录不存在");
        }
        Long tripId = longValue(alert.get("tripId"));
        Long captainUserId = longValue(alert.get("captainUserId"));
        Long memberUserId = longValue(alert.get("memberUserId"));
        if (!operatorUserId.equals(captainUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有当前行程队长可以处理成员异常");
        }
        if (alert.get("handledAt") != null) {
            return alert;
        }

        if ("REMIND".equals(request.action())) {
            appPushService.enqueue(memberUserId, "TEAM_MEMBER_CAPTAIN_REMIND", "队长提醒你尽快归队",
                    "你当前与队长距离较远，请确认路线并尽快归队",
                    "TRIP", String.valueOf(tripId),
                    "captain-remind-member:" + alertId + ":" + LocalDateTime.now().toLocalDate());
            // 提醒不会关闭异常，队长后续仍可选择忽略或移出成员。
            return alertMapper.findById(alertId);
        }
        // REMOVE 先执行正式成员移除，确保车队、行程和腾讯 IM 群成员同步成功。
        if ("REMOVE".equals(request.action())) {
            Team team = teamMapper.findAnyActiveByTripId(tripId);
            if (team == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "关联车队不存在");
            }
            teamService.removeMember(team.getId(), memberUserId,
                    new RemoveTeamMemberRequest("队长根据脱队或失联预警移除成员"));
        }
        if (alertMapper.handle(alertId, operatorUserId, request.action(), LocalDateTime.now()) == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "异常已处理，请刷新后重试");
        }
        return alertMapper.findById(alertId);
    }

    /**
     * 双重身份下优先展示用户正在参加的外部行程。
     *
     * <p>例如用户保留了一条自己未来发布的行程，同时已经作为队员出发，地图首页
     * 应展示正在行进的队伍，而不是被队长身份的未来行程覆盖。</p>
     */
    private TeamResponse resolveCurrentTeam(Long userId) {
        for (TeamMember membership : teamMemberMapper.findActiveListByUserId(userId)) {
            if ("OWNER".equals(membership.getMemberRole())) {
                continue;
            }
            Team candidate = teamMapper.findById(membership.getTeamId());
            if (candidate == null || !"ACTIVE".equals(candidate.getTeamStatus())) {
                continue;
            }
            Trip candidateTrip = tripMapper.findById(candidate.getTripId());
            if (candidateTrip != null && List.of("RUNNING", "ONGOING").contains(candidateTrip.getStatus())) {
                return teamService.getTeam(candidate.getId());
            }
        }
        return teamService.getMyCurrentTeam();
    }

    private TripResponse resolveCurrentTrip(Long userId, TeamResponse team) {
        if (team != null && team.tripId() != null) {
            return tripService.getTrip(Long.valueOf(team.tripId()));
        }
        // 没有车队时仍需展示用户自己发布的乘客出行需求或尚未建队的当前行程。
        return tripMapper.findActiveByUserId(userId).stream()
                .findFirst()
                .map(trip -> tripService.getTrip(trip.getId()))
                .orElse(null);
    }

    private List<MapMemberPositionResponse> memberPositions(Long tripId, String captainUserId) {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(5);
        List<MapMemberPositionResponse> result = new ArrayList<>();
        Long captainId = captainUserId == null || captainUserId.isBlank()
                ? null : Long.valueOf(captainUserId);
        if (captainId != null) {
            DriverTrackRecord captain = trackMapper.findLastValid(tripId, captainId);
            if (captain != null) {
                result.add(new MapMemberPositionResponse(
                        String.valueOf(captain.getDriverId()),
                        captain.getLatitude(), captain.getLongitude(), captain.getSpeed(),
                        captain.getDirection(),
                        captain.getRecordTime() == null ? null : captain.getRecordTime().toString(),
                        true,
                        captain.getRecordTime() == null || captain.getRecordTime().isBefore(staleBefore)));
            }
        }
        for (TripMemberLatestLocation point : memberLocationMapper.findByTripId(tripId)) {
            result.add(new MapMemberPositionResponse(
                    String.valueOf(point.getMemberUserId()),
                    point.getLatitude(), point.getLongitude(), point.getSpeed(), null,
                    point.getRecordTime() == null ? null : point.getRecordTime().toString(),
                    false,
                    point.getRecordTime() == null || point.getRecordTime().isBefore(staleBefore)));
        }
        return result;
    }

    private Trip requireCaptain(Long tripId) {
        Long userId = currentUserContext.requireUserId();
        Trip trip = tripMapper.findById(tripId);
        if (trip == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        if (trip.getCaptainUserId() == null || !trip.getCaptainUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有当前行程队长可以执行此操作");
        }
        return trip;
    }

    private Long departureCountdown(String departureTime) {
        if (departureTime == null || departureTime.isBlank()) return null;
        try {
            long seconds = Duration.between(LocalDateTime.now(), LocalDateTime.parse(departureTime)).getSeconds();
            return Math.max(seconds, 0L);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        return value == null ? null : Long.valueOf(value.toString());
    }
}
