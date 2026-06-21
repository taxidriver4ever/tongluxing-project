package com.tongdao.match.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.match.mapper.MatchRecommendLogMapper;
import com.tongdao.match.service.MatchService;
import com.tongdao.match.vo.MatchRecommendationListResponse;
import com.tongdao.match.vo.MatchTeamCardResponse;
import com.tongdao.match.vo.MatchTripCardResponse;
import com.tongdao.match.vo.NearbyTeamListResponse;
import com.tongdao.match.vo.NearbyTripListResponse;
import com.tongdao.team.entity.Team;
import com.tongdao.team.mapper.TeamMapper;
import com.tongdao.trip.entity.Trip;
import com.tongdao.trip.mapper.TripMapper;
import com.tongdao.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TripMapper tripMapper;
    private final TeamMapper teamMapper;
    private final MatchRecommendLogMapper recommendLogMapper;
    private final CurrentUserContext currentUserContext;

    @Override
    public MatchRecommendationListResponse getTripRecommendations(Long tripId, Integer limit) {
        Long userId = currentUserContext.requireUserId();
        Trip source = tripMapper.findById(tripId);
        if (source == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        int safeLimit = safeLimit(limit);
        List<MatchTripCardResponse> trips = tripMapper.findPublicTrips(100).stream()
                .filter(item -> !item.getId().equals(source.getId()))
                .filter(item -> !item.getUserId().equals(userId))
                .map(item -> toTripCard(source, item))
                .sorted(Comparator.comparing(MatchTripCardResponse::matchScore).reversed())
                .limit(safeLimit)
                .toList();
        List<MatchTeamCardResponse> teams = teamMapper.findPublicActive(100).stream()
                .filter(item -> !item.getOwnerUserId().equals(userId))
                .map(item -> toTeamCard(source, item))
                .sorted(Comparator.comparing(MatchTeamCardResponse::matchScore).reversed())
                .limit(safeLimit)
                .toList();
        log(userId, tripId, "TRIP_RECOMMENDATION", "EXPOSE");
        return new MatchRecommendationListResponse(String.valueOf(tripId), trips, teams);
    }

    @Override
    public NearbyTripListResponse getNearbyTrips(String latitude, String longitude, Integer radiusMeters, Integer limit) {
        parse(latitude, "纬度不能为空");
        parse(longitude, "经度不能为空");
        int safeLimit = safeLimit(limit);
        List<MatchTripCardResponse> trips = tripMapper.findPublicTrips(safeLimit).stream()
                .map(item -> toTripCard(null, item))
                .toList();
        return new NearbyTripListResponse(trips);
    }

    @Override
    public NearbyTeamListResponse getNearbyTeams(String latitude, String longitude, Integer radiusMeters, Integer limit) {
        parse(latitude, "纬度不能为空");
        parse(longitude, "经度不能为空");
        int safeLimit = safeLimit(limit);
        List<MatchTeamCardResponse> teams = teamMapper.findPublicActive(safeLimit).stream()
                .map(item -> toTeamCard(null, item))
                .toList();
        return new NearbyTeamListResponse(teams);
    }

    private MatchTripCardResponse toTripCard(Trip source, Trip target) {
        int departureGap = source == null ? 0 : (int) Math.abs(Duration.between(source.getDepartureTime(), target.getDepartureTime()).toMinutes());
        int score = source == null ? 80 : score(source.getStartName(), source.getEndName(), target.getStartName(), target.getEndName(), departureGap);
        return new MatchTripCardResponse(
                String.valueOf(target.getId()),
                String.valueOf(target.getUserId()),
                target.getStartName(),
                target.getEndName(),
                FORMATTER.format(target.getDepartureTime()),
                target.getTravelDepth(),
                score,
                score,
                departureGap,
                0
        );
    }

    private MatchTeamCardResponse toTeamCard(Trip source, Team team) {
        int score = source == null ? 80 : score(source.getStartName(), source.getEndName(), team.getStartName(), team.getEndName(), 0);
        return new MatchTeamCardResponse(
                String.valueOf(team.getId()),
                String.valueOf(team.getTripId()),
                team.getTeamName(),
                team.getStartName(),
                team.getEndName(),
                FORMATTER.format(team.getDepartureTime()),
                team.getCurrentMemberCount(),
                team.getMaxMemberCount(),
                score,
                score
        );
    }

    private int score(String sourceStart, String sourceEnd, String targetStart, String targetEnd, int departureGapMinutes) {
        int score = 60;
        if (StringUtils.hasText(sourceStart) && sourceStart.equals(targetStart)) {
            score += 20;
        }
        if (StringUtils.hasText(sourceEnd) && sourceEnd.equals(targetEnd)) {
            score += 20;
        }
        if (departureGapMinutes <= 60) {
            score += 10;
        } else if (departureGapMinutes > 24 * 60) {
            score -= 20;
        }
        return Math.max(1, Math.min(score, 99));
    }

    private int safeLimit(Integer limit) {
        return limit == null ? 20 : Math.max(1, Math.min(limit, 50));
    }

    private BigDecimal parse(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, message);
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "经纬度格式错误");
        }
    }

    private void log(Long userId, Long tripId, String scene, String actionType) {
        recommendLogMapper.insert(SnowflakeIdGenerator.nextId(), userId, tripId, null, null, scene, actionType, null, null);
    }
}
