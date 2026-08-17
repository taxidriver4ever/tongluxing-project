package com.tongluxing.application.adapter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.match.service.MatchService;
import com.tongluxing.team.service.TeamService;
import com.tongluxing.trip.dto.CreateTripRequest;
import com.tongluxing.trip.dto.LocationRequest;
import com.tongluxing.trip.dto.WaypointLocationRequest;
import com.tongluxing.trip.service.TripDraftPublishPort;
import com.tongluxing.trip.service.TripService;
import com.tongluxing.trip.vo.TripResponse;
import com.tongluxing.user.vo.LocationVO;
import com.tongluxing.user.vo.TeamMatchVO;
import com.tongluxing.user.vo.TripDraftVO;
import com.tongluxing.vehicle.service.VehicleService;
import com.tongluxing.vehicle.vo.VehicleResponse;

import lombok.RequiredArgsConstructor;

/**
 * 行程草稿发布适配器。
 *
 * <p>该适配器把 user-module 的下一趟行程草稿发布动作，串联到车辆、行程、车队和匹配模块。</p>
 */
@Component
@RequiredArgsConstructor
public class TripDraftPublishAdapter implements TripDraftPublishPort {
    private final VehicleService vehicleService;
    private final TripService tripService;
    private final TeamService teamService;
    private final MatchService matchService;

    /**
     * 根据草稿发布行程；当发布类型不是单独行程时，同时创建车队。
     *
     * @param userId 当前用户 ID
     * @param draft 用户模块中的下一趟行程草稿
     * @param publishType 发布类型；TRIP 表示仅发布行程，其它值会继续创建车队
     * @param bizId 上游业务 ID，用于链路追踪或幂等扩展
     * @return 发布后的行程 ID 和车队 ID
     */
    @Override
    public PublishOutcome publish(Long userId, TripDraftVO draft, String publishType, String bizId) {
        // 仅允许使用当前用户已认证车辆发布，避免未认证车辆进入行程和车队主流程。
        VehicleResponse vehicle = vehicleService.getMyVehicles().vehicles().stream()
                .filter(v -> "APPROVED".equals(v.certificationStatus()))
                .min(Comparator.comparing(v -> !Boolean.TRUE.equals(v.isDefault())))
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "USER_CERTIFICATION_REQUIRED"));

        TripResponse trip = tripService.createTrip(new CreateTripRequest(
                vehicle.vehicleId(), draft.startLocation().name() + "到" + draft.endLocation().name(), draft.remark(),
                draft.peopleCount(), location(draft.startLocation()), location(draft.endLocation()),
                draft.startLocation().name() + " - " + draft.endLocation().name(), draft.departureTime().toString(),
                draft.durationDays(), 0, 0, "", Math.max(2, Math.min(20, draft.peopleCount())),
                "MIDDLE", true, List.of("不限"), null, draft.remark(), waypoints(draft.waypoints())));
        long tripId = Long.parseLong(trip.tripId());
        if ("TRIP".equals(publishType)) {
            return new PublishOutcome(tripId, tripId);
        }

        // 发布为组队场景时，基于刚创建的行程继续创建车队。
        long teamId = Long.parseLong(teamService.ensurePublishedTripTeam(
                tripId,
                userId,
                vehicle.vehicleId(),
                draft.startLocation().name() + "到" + draft.endLocation().name() + "车队",
                Math.max(2, Math.min(20, draft.peopleCount()))
        ).teamId());
        return new PublishOutcome(tripId, teamId);
    }

    /**
     * 基于草稿起点查询附近车队推荐，仅第一页返回推荐结果。
     *
     * @param userId 当前用户 ID
     * @param draft 用户模块中的下一趟行程草稿
     * @param page 页码，从 1 开始
     * @param size 每页推荐数量
     * @return 附近车队推荐列表
     */
    @Override
    public List<TeamMatchVO> recommendTeams(Long userId, TripDraftVO draft, int page, int size) {
        if (page > 1) {
            return List.of();
        }
        return matchService.getNearbyTeams(draft.startLocation().latitude().toPlainString(),
                        draft.startLocation().longitude().toPlainString(), 100_000, size).teams().stream()
                .map(team -> new TeamMatchVO(Long.parseLong(team.teamId()), team.teamName(),
                        java.math.BigDecimal.valueOf(team.overlapRate() == null ? 0 : team.overlapRate()).movePointLeft(2),
                        timeDifference(draft.departureTime(), team.departureTime())))
                .toList();
    }

    /**
     * 将用户模块的位置值对象转换为行程创建请求的位置对象。
     *
     * @param value 用户模块位置值对象
     * @return 行程模块位置请求对象
     */
    private LocationRequest location(LocationVO value) {
        return new LocationRequest(value.name(), value.address(), value.latitude(), value.longitude());
    }

    /**
     * 将草稿途经点转换为行程模块的途经点请求列表。
     *
     * @param values 用户模块途经点列表
     * @return 行程模块途经点请求列表
     */
    private List<WaypointLocationRequest> waypoints(List<LocationVO> values) {
        if (values == null) {
            return List.of();
        }
        return java.util.stream.IntStream.range(0, values.size())
                .mapToObj(i -> new WaypointLocationRequest(values.get(i).name(), values.get(i).address(),
                        values.get(i).latitude(), values.get(i).longitude(), i + 1))
                .toList();
    }

    /**
     * 计算草稿期望出发时间与推荐车队出发时间的分钟差。
     *
     * @param expected 草稿期望出发时间
     * @param actual 推荐车队出发时间字符串
     * @return 两个时间的分钟差；解析失败时返回 0
     */
    private long timeDifference(LocalDateTime expected, String actual) {
        try {
            return Math.abs(Duration.between(expected, LocalDateTime.parse(actual)).toMinutes());
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }
}
