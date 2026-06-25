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
import com.tongdao.match.integration.MatchTeamPort;
import com.tongdao.match.integration.MatchTeamPort.MatchTeamDTO;
import com.tongdao.match.integration.MatchTripPort;
import com.tongdao.match.integration.MatchTripPort.MatchTripDTO;
import com.tongdao.match.mapper.MatchRecommendLogMapper;
import com.tongdao.match.service.MatchService;
import com.tongdao.match.vo.MatchRecommendationListResponse;
import com.tongdao.match.vo.MatchTeamCardResponse;
import com.tongdao.match.vo.MatchTripCardResponse;
import com.tongdao.match.vo.NearbyTeamListResponse;
import com.tongdao.match.vo.NearbyTripListResponse;
import com.tongdao.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MatchTripPort tripPort;
    private final MatchTeamPort teamPort;
    private final MatchRecommendLogMapper recommendLogMapper;
    private final CurrentUserContext currentUserContext;

    @Override
    public MatchRecommendationListResponse getTripRecommendations(Long tripId, Integer limit) {
        Long userId = currentUserContext.requireUserId();
        MatchTripDTO source = tripPort.getTrip(tripId);
        if (source == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        int safeLimit = safeLimit(limit);
        List<MatchTripCardResponse> trips = tripPort.listPublicTrips(100).stream()
                .filter(item -> !item.tripId().equals(source.tripId()))
                .filter(item -> !item.userId().equals(userId))
                .map(item -> toTripCard(source, item))
                .sorted(Comparator.comparing(MatchTripCardResponse::matchScore).reversed())
                .limit(safeLimit)
                .toList();
        List<MatchTeamCardResponse> teams = teamPort.listPublicActiveTeams(100).stream()
                .filter(item -> !item.ownerUserId().equals(userId))
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
        List<MatchTripCardResponse> trips = tripPort.listPublicTrips(safeLimit).stream()
                .map(item -> toTripCard(null, item))
                .toList();
        return new NearbyTripListResponse(trips);
    }

    @Override
    public NearbyTeamListResponse getNearbyTeams(String latitude, String longitude, Integer radiusMeters, Integer limit) {
        parse(latitude, "纬度不能为空");
        parse(longitude, "经度不能为空");
        int safeLimit = safeLimit(limit);
        List<MatchTeamCardResponse> teams = teamPort.listPublicActiveTeams(safeLimit).stream()
                .map(item -> toTeamCard(null, item))
                .toList();
        return new NearbyTeamListResponse(teams);
    }

    private MatchTripCardResponse toTripCard(MatchTripDTO source, MatchTripDTO target) {
        int departureGap = source == null ? 0 : (int) Math.abs(Duration.between(source.departureTime(), target.departureTime()).toMinutes());
        int score = source == null ? 80 : score(source.startName(), source.endName(), target.startName(), target.endName(), departureGap);
        return new MatchTripCardResponse(
                String.valueOf(target.tripId()),
                String.valueOf(target.userId()),
                target.startName(),
                target.endName(),
                FORMATTER.format(target.departureTime()),
                target.travelDepth(),
                score,
                score,
                departureGap,
                0
        );
    }

    private MatchTeamCardResponse toTeamCard(MatchTripDTO source, MatchTeamDTO team) {
        int score = source == null ? 80 : score(source.startName(), source.endName(), team.startName(), team.endName(), 0);
        return new MatchTeamCardResponse(
                String.valueOf(team.teamId()),
                String.valueOf(team.tripId()),
                team.teamName(),
                team.startName(),
                team.endName(),
                FORMATTER.format(team.departureTime()),
                team.currentMemberCount(),
                team.maxMemberCount(),
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
