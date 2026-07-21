package com.tongluxing.match.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.match.entity.MatchResult;
import com.tongluxing.match.integration.MatchTeamPort;
import com.tongluxing.match.integration.MatchTeamPort.MatchTeamDTO;
import com.tongluxing.match.integration.MatchTripPort;
import com.tongluxing.match.integration.MatchTripPort.MatchTripDTO;
import com.tongluxing.match.mapper.MatchRecommendLogMapper;
import com.tongluxing.match.mapper.MatchResultMapper;
import com.tongluxing.match.service.MatchService;
import com.tongluxing.match.vo.*;
import com.tongluxing.user.support.CurrentUserContext;
import lombok.RequiredArgsConstructor;

/** 发现同行匹配服务：发布时预计算，浏览时读取持久化结果并记录漏斗事件。 */
@Service @RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_TIME_GAP_MINUTES = 24 * 60;
    private final MatchTripPort tripPort;
    private final MatchTeamPort teamPort;
    private final MatchResultMapper resultMapper;
    private final MatchRecommendLogMapper recommendLogMapper;
    private final CurrentUserContext currentUserContext;
    private final ObjectMapper objectMapper;

    @Override @Transactional
    public void generateTripRecommendations(Long tripId) {
        MatchTripDTO source = requireTrip(tripId);
        requireMatchable(source);
        LocalDateTime now = LocalDateTime.now();
        tripPort.listPublicTrips(300).stream()
                .filter(target -> !target.tripId().equals(source.tripId()) && !target.userId().equals(source.userId()))
                .map(target -> calculate(source, target, now))
                .filter(result -> result.getDepartureGapMinutes() <= MAX_TIME_GAP_MINUTES && result.getMatchScore() >= 50)
                .forEach(resultMapper::upsert);
    }

    @Override
    public MatchRecommendationListResponse getTripRecommendations(Long tripId, Integer limit) {
        Long userId = currentUserContext.requireUserId();
        MatchTripDTO source = requireTrip(tripId);
        if (!source.userId().equals(userId)) throw new BusinessException(ResultCode.FORBIDDEN, "只能查看自己行程的同行推荐");
        requireMatchable(source);
        List<MatchTripCardResponse> trips = resultMapper.findBySourceTrip(tripId, safeLimit(limit)).stream()
                .map(this::toTripCard).filter(java.util.Objects::nonNull).toList();
        trips.forEach(card -> log(userId, tripId, Long.valueOf(card.tripId()), value(card.teamId()), "IMPRESSION", card.matchId()));
        List<MatchTeamCardResponse> teams = trips.stream().filter(card -> Boolean.TRUE.equals(card.joinable()))
                .map(card -> toTeamCard(source, teamPort.findActiveTeamByTripId(Long.valueOf(card.tripId()))))
                .filter(java.util.Objects::nonNull).toList();
        return new MatchRecommendationListResponse(String.valueOf(tripId), trips, teams);
    }

    @Override
    public MatchTripCardResponse getRecommendation(Long matchId) {
        Long userId = currentUserContext.requireUserId();
        MatchResult result = requireResult(matchId);
        if (!result.getSourceUserId().equals(userId)) throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该推荐");
        MatchTripCardResponse card = toTripCard(result);
        if (card == null) throw new BusinessException(ResultCode.NOT_FOUND, "目标行程不存在");
        log(userId, result.getSourceTripId(), result.getTargetTripId(), value(card.teamId()), "CLICK", String.valueOf(matchId));
        return card;
    }

    @Override @Transactional
    public MatchApplyResponse apply(Long matchId, String message) {
        Long userId = currentUserContext.requireUserId();
        MatchResult result = requireResult(matchId);
        if (!result.getSourceUserId().equals(userId)) throw new BusinessException(ResultCode.FORBIDDEN, "无权使用该推荐");
        MatchTeamDTO team = teamPort.findActiveTeamByTripId(result.getTargetTripId());
        if (team == null) throw new BusinessException(ResultCode.BUSINESS_ERROR, "对方尚未创建可加入车队");
        Long applicationId = teamPort.apply(team.teamId(), StringUtils.hasText(message) ? message.trim() : "通过发现同行申请加入");
        log(userId, result.getSourceTripId(), result.getTargetTripId(), team.teamId(), "APPLY", String.valueOf(matchId));
        return new MatchApplyResponse(String.valueOf(matchId), String.valueOf(result.getTargetTripId()),
                String.valueOf(team.teamId()), String.valueOf(applicationId), "PENDING");
    }

    @Override public void recordTripLifecycle(Long tripId, String actionType) {
        if (!List.of("START", "FINISH").contains(actionType)) return;
        MatchTripDTO trip = tripPort.getTrip(tripId);
        if (trip != null) log(trip.userId(), tripId, tripId, null, actionType, null);
    }

    @Override public void recordTeamApplication(Long applicantUserId, Long targetTripId, Long teamId, String status) {
        MatchResult result = resultMapper.findLatestByApplicant(applicantUserId, targetTripId);
        if (result != null) log(applicantUserId, result.getSourceTripId(), targetTripId, teamId,
                "APPROVED".equals(status) ? "ACCEPT" : "REJECT", String.valueOf(result.getId()));
    }

    @Override public NearbyTripListResponse getNearbyTrips(String latitude, String longitude, Integer radiusMeters, Integer limit) {
        parse(latitude, "纬度不能为空"); parse(longitude, "经度不能为空");
        return new NearbyTripListResponse(tripPort.listPublicTrips(safeLimit(limit)).stream().map(this::cardWithoutMatch).toList());
    }

    @Override public NearbyTeamListResponse getNearbyTeams(String latitude, String longitude, Integer radiusMeters, Integer limit) {
        parse(latitude, "纬度不能为空"); parse(longitude, "经度不能为空");
        return new NearbyTeamListResponse(teamPort.listPublicActiveTeams(safeLimit(limit)).stream().map(t -> toTeamCard(null, t)).toList());
    }

    private MatchResult calculate(MatchTripDTO source, MatchTripDTO target, LocalDateTime now) {
        int gap = (int) Math.abs(Duration.between(source.departureTime(), target.departureTime()).toMinutes());
        double startKm = distanceKm(source.startLatitude(), source.startLongitude(), target.startLatitude(), target.startLongitude());
        double endKm = distanceKm(source.endLatitude(), source.endLongitude(), target.endLatitude(), target.endLongitude());
        int routeScore = proximityScore(startKm, 40, 120), destinationScore = proximityScore(endKm, 30, 160);
        int timeScore = gap > MAX_TIME_GAP_MINUTES ? 0 : Math.max(0, 20 - gap * 20 / MAX_TIME_GAP_MINUTES);
        int interestScore = StringUtils.hasText(source.travelDepth()) && source.travelDepth().equals(target.travelDepth()) ? 10 : 5;
        MatchResult result = new MatchResult();
        result.setId(SnowflakeIdGenerator.nextId()); result.setSourceTripId(source.tripId()); result.setTargetTripId(target.tripId());
        result.setSourceUserId(source.userId()); result.setTargetUserId(target.userId());
        result.setMatchScore(Math.min(100, routeScore + destinationScore + timeScore + interestScore));
        result.setOverlapRate(Math.min(100, routeScore * 2 + destinationScore));
        result.setDistanceGapMeters((int) Math.round((startKm + endKm) * 500)); result.setDepartureGapMinutes(gap);
        result.setScoreDetailJson(json(new ScoreDetail(routeScore, destinationScore, timeScore, interestScore)));
        result.setResultStatus("VALID"); result.setCalculatedAt(now); result.setCreatedAt(now); result.setUpdatedAt(now); result.setDeleted(0);
        return result;
    }

    private MatchTripCardResponse toTripCard(MatchResult result) {
        MatchTripDTO target = tripPort.getTrip(result.getTargetTripId()); if (target == null) return null;
        if (!isMatchable(target)) return null;
        MatchTeamDTO team = teamPort.findActiveTeamByTripId(target.tripId());
        return new MatchTripCardResponse(String.valueOf(result.getId()), String.valueOf(target.tripId()), String.valueOf(target.userId()),
                target.title(), target.startName(), target.endName(), FORMATTER.format(target.departureTime()), target.travelDepth(),
                target.status(),
                result.getMatchScore(), result.getOverlapRate(), result.getDepartureGapMinutes(), result.getDistanceGapMeters(),
                target.expectedPeople(), team == null ? null : String.valueOf(team.teamId()),
                team == null ? null : team.currentMemberCount(), team == null ? null : team.maxMemberCount(), team != null);
    }

    private MatchTripCardResponse cardWithoutMatch(MatchTripDTO target) {
        MatchTeamDTO team = teamPort.findActiveTeamByTripId(target.tripId());
        return new MatchTripCardResponse(null, String.valueOf(target.tripId()), String.valueOf(target.userId()), target.title(),
                target.startName(), target.endName(), FORMATTER.format(target.departureTime()), target.travelDepth(), target.status(),
                80, 80, 0, 0,
                target.expectedPeople(), team == null ? null : String.valueOf(team.teamId()),
                team == null ? null : team.currentMemberCount(), team == null ? null : team.maxMemberCount(), team != null);
    }

    private MatchTeamCardResponse toTeamCard(MatchTripDTO source, MatchTeamDTO team) {
        if (team == null) return null; int score = source == null ? 80 : (source.endName().equals(team.endName()) ? 90 : 65);
        return new MatchTeamCardResponse(String.valueOf(team.teamId()), String.valueOf(team.tripId()), team.teamName(), team.startName(),
                team.endName(), FORMATTER.format(team.departureTime()), team.currentMemberCount(), team.maxMemberCount(), score, score);
    }

    private MatchTripDTO requireTrip(Long id) { MatchTripDTO trip = tripPort.getTrip(id); if (trip == null) throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在"); return trip; }
    private void requireMatchable(MatchTripDTO trip) { if (!isMatchable(trip)) throw new BusinessException(ResultCode.BAD_REQUEST, "只有公开且处于招募中或进行中的行程可以发现同行"); }
    private boolean isMatchable(MatchTripDTO trip) {
        return List.of("PUBLISHED", "RUNNING", "ONGOING").contains(trip.status())
                && Integer.valueOf(1).equals(trip.publicFlag());
    }
    private MatchResult requireResult(Long id) { MatchResult r = resultMapper.findById(id); if (r == null || !"VALID".equals(r.getResultStatus())) throw new BusinessException(ResultCode.NOT_FOUND, "推荐结果不存在"); return r; }
    private int proximityScore(double km, int max, double range) { return (int) Math.round(max * Math.max(0, 1 - km / range)); }
    private double distanceKm(Double a, Double b, Double c, Double d) { if (a == null || b == null || c == null || d == null) return 999; double p1=Math.toRadians(a),p2=Math.toRadians(c),x=p2-p1,y=Math.toRadians(d-b);double h=Math.sin(x/2)*Math.sin(x/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(y/2)*Math.sin(y/2);return 6371*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h)); }
    private int safeLimit(Integer limit) { return limit == null ? 20 : Math.max(1, Math.min(limit, 50)); }
    private BigDecimal parse(String value, String message) { if (!StringUtils.hasText(value)) throw new BusinessException(ResultCode.BAD_REQUEST,message); try{return new BigDecimal(value);}catch(NumberFormatException e){throw new BusinessException(ResultCode.BAD_REQUEST,"经纬度格式错误");} }
    private void log(Long userId, Long tripId, Long targetTripId, Long targetTeamId, String action, String requestId) { recommendLogMapper.insert(SnowflakeIdGenerator.nextId(),userId,tripId,targetTripId,targetTeamId,"DISCOVER_COMPANION",action,requestId,null); }
    private Long value(String id) { return StringUtils.hasText(id) ? Long.valueOf(id) : null; }
    private String json(Object value) { try{return objectMapper.writeValueAsString(value);}catch(JsonProcessingException e){return "{}";} }
    private record ScoreDetail(int routeScore,int destinationScore,int timeScore,int interestScore) { }
}
