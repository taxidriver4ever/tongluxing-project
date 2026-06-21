package com.tongdao;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tongdao.auth.entity.AuthAccount;
import com.tongdao.auth.mapper.AuthAccountMapper;
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.match.service.MatchService;
import com.tongdao.team.dto.CreateTeamRequest;
import com.tongdao.team.service.TeamService;
import com.tongdao.trip.dto.CreateTripRequest;
import com.tongdao.trip.dto.LocationRequest;
import com.tongdao.trip.dto.WaypointLocationRequest;
import com.tongdao.trip.service.TripService;
import com.tongdao.trip.vo.TripResponse;
import com.tongdao.user.integration.UserModuleFacade;
import com.tongdao.user.model.UserModels.LocationVO;
import com.tongdao.user.model.UserModels.TeamMatchVO;
import com.tongdao.user.model.UserModels.TripDraftVO;
import com.tongdao.vehicle.service.VehicleService;
import com.tongdao.vehicle.vo.VehicleResponse;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserModuleFacadeAdapter implements UserModuleFacade {

    private final VehicleService vehicleService;
    private final TripService tripService;
    private final TeamService teamService;
    private final MatchService matchService;
    private final AuthAccountMapper authAccountMapper;

    @Override
    public PublishOutcome publishDraft(Long userId, TripDraftVO draft, String publishType, String bizId) {
        VehicleResponse vehicle = vehicleService.getMyVehicles().vehicles().stream()
                .filter(v -> "APPROVED".equals(v.certificationStatus()))
                .min(Comparator.comparing(v -> !Boolean.TRUE.equals(v.isDefault())))
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "USER_CERTIFICATION_REQUIRED"));

        TripResponse trip = tripService.createTrip(new CreateTripRequest(
                vehicle.vehicleId(), location(draft.startLocation()), location(draft.endLocation()),
                draft.startLocation().name() + " - " + draft.endLocation().name(), draft.departureTime().toString(),
                draft.durationDays(), 0, 0, "", Math.max(2, Math.min(20, draft.peopleCount())),
                "RELAXED", true, draft.remark(), waypoints(draft.waypoints())
        ));
        long tripId = Long.parseLong(trip.tripId());
        if ("TRIP".equals(publishType)) return new PublishOutcome(tripId, tripId);

        long teamId = Long.parseLong(teamService.createTeam(new CreateTeamRequest(
                tripId, vehicle.vehicleId(), draft.startLocation().name() + "到" + draft.endLocation().name() + "车队",
                draft.remark(), Math.max(2, Math.min(20, draft.peopleCount())), "APPLICATION", true, ""
        )).teamId());
        return new PublishOutcome(tripId, teamId);
    }

    @Override
    public List<TeamMatchVO> recommendTeams(Long userId, TripDraftVO draft, int page, int size) {
        if (page > 1) return List.of();
        return matchService.getNearbyTeams(draft.startLocation().latitude().toPlainString(),
                        draft.startLocation().longitude().toPlainString(), 100_000, size).teams().stream()
                .map(team -> new TeamMatchVO(Long.parseLong(team.teamId()), team.teamName(),
                        java.math.BigDecimal.valueOf(team.overlapRate() == null ? 0 : team.overlapRate()).movePointLeft(2),
                        timeDifference(draft.departureTime(), team.departureTime())))
                .toList();
    }

    @Override
    public boolean isInviteBindingEligible(Long userId) {
        AuthAccount account = authAccountMapper.findByUserId(userId);
        return account != null && account.getCreatedAt() != null
                && account.getCreatedAt().isAfter(LocalDateTime.now().minusHours(24));
    }

    private LocationRequest location(LocationVO value) {
        return new LocationRequest(value.name(), value.address(), value.latitude(), value.longitude());
    }

    private List<WaypointLocationRequest> waypoints(List<LocationVO> values) {
        if (values == null) return List.of();
        return java.util.stream.IntStream.range(0, values.size())
                .mapToObj(i -> new WaypointLocationRequest(values.get(i).name(), values.get(i).address(),
                        values.get(i).latitude(), values.get(i).longitude(), i + 1))
                .toList();
    }

    private long timeDifference(LocalDateTime expected, String actual) {
        try { return Math.abs(Duration.between(expected, LocalDateTime.parse(actual)).toMinutes()); }
        catch (RuntimeException ignored) { return 0L; }
    }
}
