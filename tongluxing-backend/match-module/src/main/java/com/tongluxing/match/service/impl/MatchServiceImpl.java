package com.tongluxing.match.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.match.entity.MatchResult;
import com.tongluxing.match.dto.TripApplicationRequest;
import com.tongluxing.match.dto.TripSearchRequest;
import com.tongluxing.match.integration.MatchTeamPort;
import com.tongluxing.match.integration.MatchTeamPort.MatchTeamDTO;
import com.tongluxing.match.integration.MatchTripPort;
import com.tongluxing.match.integration.MatchTripPort.MatchTripDTO;
import com.tongluxing.match.mapper.MatchRecommendLogMapper;
import com.tongluxing.match.mapper.MatchResultMapper;
import com.tongluxing.match.mapper.TripFavoriteMapper;
import com.tongluxing.match.mapper.TripConsultationMapper;
import com.tongluxing.match.service.MatchService;
import com.tongluxing.match.vo.*;
import com.tongluxing.user.support.CurrentUserContext;
import com.tongluxing.user.service.UserService;
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
    private final UserService userService;
    private final TripFavoriteMapper tripFavoriteMapper;
    private final TripConsultationMapper tripConsultationMapper;

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
        Long applicationId = teamPort.apply(team.teamId(), StringUtils.hasText(message) ? message.trim() : "通过发现同行申请加入",
                null, null);
        log(userId, result.getSourceTripId(), result.getTargetTripId(), team.teamId(), "APPLY", String.valueOf(matchId));
        return new MatchApplyResponse(String.valueOf(matchId), String.valueOf(result.getTargetTripId()),
                String.valueOf(team.teamId()), String.valueOf(applicationId), "PENDING");
    }

    @Override @Transactional
    public MatchApplyResponse applyNearbyTrip(Long tripId, String message) {
        Long userId = currentUserContext.requireUserId();
        MatchTripDTO target = requireTrip(tripId);
        if (target.userId().equals(userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能申请加入自己创建的行程");
        }
        if (!isRecruiting(target.status()) || !Integer.valueOf(1).equals(target.publicFlag())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该行程当前不在招募中");
        }
        MatchTeamDTO team = teamPort.findActiveTeamByTripId(tripId);
        if (team == null) throw new BusinessException(ResultCode.BUSINESS_ERROR, "该行程暂未创建可加入车队");
        Long applicationId = teamPort.apply(team.teamId(), StringUtils.hasText(message) ? message.trim() : "通过地图附近行程申请加入",
                null, null);
        // 附近场景没有“我的源行程”，用目标行程 ID 填充非空审计字段并通过 requestId 标识来源。
        log(userId, tripId, tripId, team.teamId(), "APPLY", "NEARBY:" + tripId);
        return new MatchApplyResponse(null, String.valueOf(tripId), String.valueOf(team.teamId()),
                String.valueOf(applicationId), "PENDING");
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
        double lat = parse(latitude, "纬度不能为空").doubleValue();
        double lng = parse(longitude, "经度不能为空").doubleValue();
        int radius = radiusMeters == null ? 5000 : Math.max(500, Math.min(radiusMeters, 100000));
        Long userId = currentUserContext.requireUserId();
        List<MatchTripCardResponse> trips = tripPort.listPublicTrips(300).stream()
                .filter(t -> isRecruiting(t.status()))
                .filter(t -> !userId.equals(t.userId()))
                .filter(t -> distanceKm(lat, lng, t.startLatitude(), t.startLongitude()) * 1000 <= radius)
                .sorted(java.util.Comparator.comparingDouble(t -> distanceKm(lat, lng, t.startLatitude(), t.startLongitude())))
                .limit(safeLimit(limit))
                .map(this::cardWithoutMatch)
                .toList();
        return new NearbyTripListResponse(trips);
    }

    @Override public NearbyTeamListResponse getNearbyTeams(String latitude, String longitude, Integer radiusMeters, Integer limit) {
        parse(latitude, "纬度不能为空"); parse(longitude, "经度不能为空");
        return new NearbyTeamListResponse(teamPort.listPublicActiveTeams(safeLimit(limit)).stream().map(t -> toTeamCard(null, t)).toList());
    }

    @Override
    public TripSearchPageResponse searchTrips(TripSearchRequest request) {
        validateSearch(request);
        Long userId = currentUserContext.requireUserId();
        int radius = request.radiusMeters() == null ? 50000 : request.radiusMeters();
        int minimumSeats = request.minimumRemainingSeats() == null ? 1 : request.minimumRemainingSeats();
        LocalDateTime center = request.departureStart().plusSeconds(
                Duration.between(request.departureStart(), request.departureEnd()).toSeconds() / 2);
        List<TripSearchCardResponse> candidates = new ArrayList<>();
        for (MatchTripDTO trip : tripPort.listPublicTrips(500)) {
            if (userId.equals(trip.userId()) || !isRecruiting(trip.status())
                    || !Integer.valueOf(1).equals(trip.publicFlag())
                    || trip.departureTime() == null || trip.departureTime().isBefore(LocalDateTime.now())
                    || trip.departureTime().isBefore(request.departureStart())
                    || trip.departureTime().isAfter(request.departureEnd())) {
                continue;
            }
            MatchTeamDTO team = teamPort.findActiveTeamByTripId(trip.tripId());
            if (team == null || teamPort.hasActiveMembershipOrPending(team.teamId(), userId)) {
                continue;
            }
            int current = team.currentMemberCount() == null ? 1 : team.currentMemberCount();
            int max = team.maxMemberCount() == null ? 1 : team.maxMemberCount();
            int remaining = Math.max(0, max - current);
            if (remaining < minimumSeats) {
                continue;
            }
            if (Boolean.TRUE.equals(request.carpoolAllowed()) && max <= 1) {
                continue;
            }
            int startDistance = meters(request.startLatitude().doubleValue(), request.startLongitude().doubleValue(),
                    trip.startLatitude(), trip.startLongitude());
            int endDistance = meters(request.endLatitude().doubleValue(), request.endLongitude().doubleValue(),
                    trip.endLatitude(), trip.endLongitude());
            if (startDistance > radius || endDistance > radius) {
                continue;
            }
            if (StringUtils.hasText(request.vehicleType())
                    && !request.vehicleType().trim().equalsIgnoreCase(valueOrEmpty(trip.vehicleType()))) {
                continue;
            }
            if (Boolean.TRUE.equals(request.driverVerified()) && !Boolean.TRUE.equals(trip.driverVerified())) {
                continue;
            }
            List<String> targetWaypoints = waypointNames(trip.waypointsJson());
            int waypointMatches = matchingWaypoints(request, targetWaypoints);
            if (request.waypoints() != null && !request.waypoints().isEmpty() && waypointMatches == 0) {
                continue;
            }
            long gapMinutes = Math.abs(Duration.between(center, trip.departureTime()).toMinutes());
            int timeWindow = Math.max(1, request.timeToleranceMinutes() == null
                    ? (int) Math.max(60, Duration.between(request.departureStart(), request.departureEnd()).toMinutes() / 2)
                    : request.timeToleranceMinutes());
            int score = score(startDistance, endDistance, radius, gapMinutes, timeWindow,
                    waypointMatches, request.waypoints() == null ? 0 : request.waypoints().size(), remaining);
            candidates.add(toSearchCard(trip, team, targetWaypoints, score, startDistance, endDistance));
        }
        Comparator<TripSearchCardResponse> comparator = switch (normalizedSort(request.sortBy())) {
            case "DEPARTURE_TIME" -> Comparator.comparing(TripSearchCardResponse::departureTime);
            case "DISTANCE" -> Comparator.comparingInt(
                    card -> card.startDistanceMeters() + card.endDistanceMeters());
            default -> Comparator.comparingInt(TripSearchCardResponse::matchScore).reversed()
                    .thenComparing(TripSearchCardResponse::departureTime);
        };
        candidates.sort(comparator);
        int page = request.page() == null ? 1 : request.page();
        int size = request.size() == null ? 20 : request.size();
        int from = Math.min(candidates.size(), (page - 1) * size);
        int to = Math.min(candidates.size(), from + size);
        return new TripSearchPageResponse(page, size, (long) candidates.size(), candidates.subList(from, to));
    }

    @Override
    public TripSearchDetailResponse getSearchTripDetail(Long tripId) {
        MatchTripDTO trip = requireRecruitingTrip(tripId);
        MatchTeamDTO team = requireJoinableTeam(tripId);
        List<String> waypoints = waypointNames(trip.waypointsJson());
        int current = team.currentMemberCount() == null ? 1 : team.currentMemberCount();
        int max = team.maxMemberCount() == null ? 1 : team.maxMemberCount();
        return new TripSearchDetailResponse(
                String.valueOf(trip.tripId()), String.valueOf(trip.userId()), trip.ownerNickname(),
                trip.ownerAvatarImageKey(), trip.title(), trip.description(), trip.startName(), trip.endName(),
                waypoints, format(trip.departureTime()), estimatedEnd(trip), trip.routeDistance(), trip.routeDuration(),
                String.valueOf(team.teamId()), team.teamName(), team.notice(), trip.vehicleSummary(),
                current, max, Math.max(0, max - current), team.teamDesc(), trip.status(), current < max);
    }

    @Override
    @Transactional
    public MatchApplyResponse applyToTrip(Long tripId, TripApplicationRequest request) {
        Long userId = currentUserContext.requireUserId();
        MatchTripDTO trip = requireRecruitingTrip(tripId);
        if (userId.equals(trip.userId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能申请自己创建的行程");
        }
        MatchTeamDTO team = requireJoinableTeam(tripId);
        int companions = request.companionCount() == null ? 1 : request.companionCount();
        int remaining = team.maxMemberCount() - team.currentMemberCount();
        if (companions > remaining) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "剩余名额不足");
        }
        if (Boolean.TRUE.equals(request.selfDrive()) && request.applicantVehicleId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "选择自驾时必须选择车辆");
        }
        String questions = json(Map.of(
                "selfDrive", Boolean.TRUE.equals(request.selfDrive()),
                "companionCount", companions,
                "source", "TRIP_SEARCH"));
        Long applicationId = teamPort.apply(team.teamId(),
                StringUtils.hasText(request.message()) ? request.message().trim() : "通过行程搜索申请加入",
                request.applicantVehicleId(), questions);
        log(userId, tripId, tripId, team.teamId(), "APPLY", "SEARCH:" + tripId);
        return new MatchApplyResponse(null, String.valueOf(tripId), String.valueOf(team.teamId()),
                String.valueOf(applicationId), "PENDING");
    }

    @Override
    public TripDiscoverPageResponse discoverTrips(
            String keyword, String sort, Double latitude, Double longitude, Long referenceTripId,
            String startCity, String destination, String departureDateFrom, String departureDateTo,
            String vehicleType, Integer minimumRemainingSeats, Integer page, Integer size) {
        Long userId = currentUserContext.requireUserId();
        int safePage = page == null ? 1 : Math.max(1, page);
        int safeSize = size == null ? 12 : Math.max(1, Math.min(size, 30));
        int requiredSeats = minimumRemainingSeats == null ? 1 : Math.max(1, minimumRemainingSeats);
        String normalizedKeyword = normalizeLocation(keyword);
        LocalDate from = parseDate(departureDateFrom);
        LocalDate to = parseDate(departureDateTo);
        MatchTripDTO reference = referenceTripId == null ? null : requireOwnedReference(referenceTripId, userId);
        List<TripDiscoverCardResponse> records = new ArrayList<>();
        for (MatchTripDTO trip : tripPort.listPublicTrips(1000)) {
            if (!isRecruiting(trip.status()) || !Integer.valueOf(1).equals(trip.publicFlag())
                    || trip.departureTime() == null || !trip.departureTime().isAfter(LocalDateTime.now())) {
                continue;
            }
            MatchTeamDTO team = teamPort.findActiveTeamByTripId(trip.tripId());
            if (team == null) continue;
            int current = team.currentMemberCount() == null ? 1 : team.currentMemberCount();
            int max = team.maxMemberCount() == null ? 1 : team.maxMemberCount();
            if (max - current < requiredSeats) continue;
            List<String> waypoints = waypointNames(trip.waypointsJson());
            if (StringUtils.hasText(normalizedKeyword) && !discoverText(trip, waypoints).contains(normalizedKeyword)) continue;
            if (StringUtils.hasText(startCity) && !normalizeLocation(trip.startName()).contains(normalizeLocation(startCity))) continue;
            if (StringUtils.hasText(destination) && !normalizeLocation(trip.endName()).contains(normalizeLocation(destination))) continue;
            if (from != null && trip.departureTime().toLocalDate().isBefore(from)) continue;
            if (to != null && trip.departureTime().toLocalDate().isAfter(to)) continue;
            if (StringUtils.hasText(vehicleType)
                    && !vehicleType.trim().equalsIgnoreCase(valueOrEmpty(trip.vehicleType()))) continue;
            int distance = latitude == null || longitude == null ? -1
                    : meters(latitude, longitude, trip.startLatitude(), trip.startLongitude());
            int score = discoveryScore(trip, team, reference, distance);
            records.add(toDiscoverCard(trip, team, waypoints, score, distance, userId));
        }
        String normalizedSort = StringUtils.hasText(sort) ? sort.trim().toUpperCase(Locale.ROOT) : "RECOMMENDED";
        Comparator<TripDiscoverCardResponse> comparator = switch (normalizedSort) {
            case "NEARBY" -> Comparator.comparingInt(card -> card.distanceMeters() == null || card.distanceMeters() < 0
                    ? Integer.MAX_VALUE : card.distanceMeters());
            case "DEPARTURE_TIME" -> Comparator.comparing(TripDiscoverCardResponse::departureTime);
            case "ROUTE_MATCH" -> Comparator.comparingInt(TripDiscoverCardResponse::matchScore).reversed();
            default -> Comparator.comparingInt(TripDiscoverCardResponse::matchScore).reversed();
        };
        records.sort(comparator.thenComparing(TripDiscoverCardResponse::departureTime)
                .thenComparing(TripDiscoverCardResponse::tripId));
        int fromIndex = Math.min(records.size(), (safePage - 1) * safeSize);
        int toIndex = Math.min(records.size(), fromIndex + safeSize);
        return new TripDiscoverPageResponse(safePage, safeSize, (long) records.size(),
                records.subList(fromIndex, toIndex));
    }

    @Override
    public TripPublicDetailResponse getPublicTripDetail(Long tripId) {
        Long userId = currentUserContext.requireUserId();
        MatchTripDTO trip = requireRecruitingTrip(tripId);
        MatchTeamDTO team = teamPort.findActiveTeamByTripId(tripId);
        if (team == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程车队不存在");
        }
        int current = team.currentMemberCount() == null ? 1 : team.currentMemberCount();
        int max = team.maxMemberCount() == null ? 1 : team.maxMemberCount();
        String relationship = teamPort.relationshipStatus(team.teamId(), userId);
        boolean ownerTrip = userId.equals(trip.userId());
        var follow = userService.getFollowStatus(trip.userId());
        var members = teamPort.listPublicMembers(team.teamId(), 50).stream()
                .map(member -> new TripPublicMemberResponse(String.valueOf(member.userId()), member.nickname(),
                        member.avatarImageKey(), member.role(), member.certificationStatus(),
                        member.totalTripCount(), member.totalDistanceMeters()))
                .toList();
        boolean joinable = current < max;
        boolean allowApply = !ownerTrip && joinable && List.of("NONE", "REJECTED").contains(relationship);
        boolean allowConsultation = !ownerTrip && (Boolean.TRUE.equals(follow.following())
                || Boolean.TRUE.equals(follow.mutual()) || "JOINED".equals(relationship));
        return new TripPublicDetailResponse(String.valueOf(trip.tripId()), trip.title(), trip.status(),
                trip.startName(), waypointNames(trip.waypointsJson()), trip.endName(), format(trip.departureTime()),
                trip.estimatedDays(), trip.description(), trip.routePolyline(), trip.routeDistance(),
                trip.routeDuration(), trip.joinedVehicleCount(), trip.maxVehicleCount(), current, max,
                Math.max(0, max - current), trip.vehicleSummary(), null, null, trip.startName(), trip.remark(),
                team.notice(), team.teamDesc(), discoverTags(trip), discoverOwner(trip, userId), members,
                relationship, ownerTrip, allowConsultation, allowApply, joinable,
                tripFavoriteMapper.exists(userId, tripId) > 0);
    }

    @Override
    @Transactional
    public Boolean favoriteTrip(Long tripId) {
        Long userId = currentUserContext.requireUserId();
        requireRecruitingTrip(tripId);
        tripFavoriteMapper.insert(SnowflakeIdGenerator.nextId(), userId, tripId);
        return true;
    }

    @Override
    @Transactional
    public Boolean unfavoriteTrip(Long tripId) {
        Long userId = currentUserContext.requireUserId();
        tripFavoriteMapper.delete(userId, tripId);
        return false;
    }

    @Override
    @Transactional
    public TripConsultationResponse consultTrip(Long tripId, String content) {
        Long userId = currentUserContext.requireUserId();
        MatchTripDTO trip = requireRecruitingTrip(tripId);
        if (userId.equals(trip.userId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能咨询自己发布的行程");
        }
        MatchTeamDTO team = teamPort.findActiveTeamByTripId(tripId);
        String relationship = team == null ? "NONE" : teamPort.relationshipStatus(team.teamId(), userId);
        var follow = userService.getFollowStatus(trip.userId());
        if ("JOINED".equals(relationship) || Boolean.TRUE.equals(follow.mutual())) {
            return new TripConsultationResponse(String.valueOf(tripId), trip.title(), String.valueOf(userId),
                    String.valueOf(trip.userId()), "DIRECT_ALLOWED");
        }
        if (!Boolean.TRUE.equals(follow.following())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "请先关注发起人，或直接提交加入申请");
        }
        if (tripConsultationMapper.pendingExists(tripId, userId) > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该行程的咨询已发送，请等待队长回复");
        }
        String safeContent = StringUtils.hasText(content) ? content.trim() : "你好，我想咨询这段行程";
        if (safeContent.length() > 500) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "咨询内容不能超过500字");
        }
        tripConsultationMapper.insert(SnowflakeIdGenerator.nextId(), tripId, trip.title(), userId, trip.userId(),
                safeContent);
        return new TripConsultationResponse(String.valueOf(tripId), trip.title(), String.valueOf(userId),
                String.valueOf(trip.userId()), "PENDING");
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

    private TripDiscoverCardResponse toDiscoverCard(MatchTripDTO trip, MatchTeamDTO team, List<String> waypoints,
                                                     int score, int distance, Long currentUserId) {
        int current = team.currentMemberCount() == null ? 1 : team.currentMemberCount();
        int max = team.maxMemberCount() == null ? 1 : team.maxMemberCount();
        return new TripDiscoverCardResponse(String.valueOf(trip.tripId()), trip.title(), trip.status(),
                trip.startName(), waypoints, trip.endName(), format(trip.departureTime()), trip.estimatedDays(),
                trip.description(), trip.joinedVehicleCount(), trip.maxVehicleCount(), current, max,
                Math.max(0, max - current), score, distance < 0 ? null : distance, discoverTags(trip), null,
                teamPort.relationshipStatus(team.teamId(), currentUserId), discoverOwner(trip, currentUserId));
    }

    private TripDiscoverOwnerResponse discoverOwner(MatchTripDTO trip, Long currentUserId) {
        boolean followed = !currentUserId.equals(trip.userId())
                && Boolean.TRUE.equals(userService.getFollowStatus(trip.userId()).following());
        return new TripDiscoverOwnerResponse(String.valueOf(trip.userId()), trip.ownerNickname(),
                trip.ownerAvatarImageKey(), trip.ownerLevelCode(),
                Boolean.TRUE.equals(trip.driverVerified()) ? "APPROVED" : "UNSUBMITTED",
                null, trip.ownerTotalTripCount(), trip.ownerTotalDistanceMeters(), trip.ownerLastActiveAt(),
                trip.ownerBadgeCount(), followed);
    }

    private List<String> discoverTags(MatchTripDTO trip) {
        List<String> tags = new ArrayList<>();
        if (StringUtils.hasText(trip.vehicleType())) tags.add(trip.vehicleType());
        if (StringUtils.hasText(trip.travelDepth())) tags.add(trip.travelDepth());
        if (Boolean.TRUE.equals(trip.driverVerified())) tags.add("驾驶员已认证");
        return tags;
    }

    private String discoverText(MatchTripDTO trip, List<String> waypoints) {
        return normalizeLocation(String.join(" ", valueOrEmpty(trip.title()), valueOrEmpty(trip.startName()),
                valueOrEmpty(trip.endName()), valueOrEmpty(trip.ownerNickname()), String.join(" ", waypoints)));
    }

    private int discoveryScore(MatchTripDTO trip, MatchTeamDTO team, MatchTripDTO reference, int distance) {
        if (reference != null) {
            int start = proximityScore(distanceKm(reference.startLatitude(), reference.startLongitude(),
                    trip.startLatitude(), trip.startLongitude()), 30, 120);
            int end = proximityScore(distanceKm(reference.endLatitude(), reference.endLongitude(),
                    trip.endLatitude(), trip.endLongitude()), 30, 160);
            int time = trip.departureTime() == null || reference.departureTime() == null ? 0
                    : Math.max(0, 20 - (int) Math.min(20,
                    Math.abs(Duration.between(reference.departureTime(), trip.departureTime()).toHours())));
            Set<String> target = waypointNames(trip.waypointsJson()).stream().map(this::normalizeLocation)
                    .collect(java.util.stream.Collectors.toSet());
            long overlap = waypointNames(reference.waypointsJson()).stream().map(this::normalizeLocation)
                    .filter(target::contains).count();
            return Math.min(100, start + end + time + (int) Math.min(10, overlap * 5) + 10);
        }
        int remaining = Math.max(0, team.maxMemberCount() - team.currentMemberCount());
        int score = 58 + Math.min(10, remaining * 2)
                + Math.min(10, Math.max(0, trip.ownerTotalTripCount()))
                + (Boolean.TRUE.equals(trip.driverVerified()) ? 7 : 0);
        if (distance >= 0) score += Math.max(0, 15 - distance / 10000);
        return Math.max(0, Math.min(100, score));
    }

    private MatchTripDTO requireOwnedReference(Long tripId, Long userId) {
        MatchTripDTO trip = requireTrip(tripId);
        if (!userId.equals(trip.userId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能使用自己的行程作为顺路匹配基准");
        }
        return trip;
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "日期格式应为 yyyy-MM-dd");
        }
    }

    private void validateSearch(TripSearchRequest request) {
        if (!request.departureEnd().isAfter(request.departureStart())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "出发时间范围不正确");
        }
        Set<String> locations = new HashSet<>();
        locations.add(normalizeLocation(request.startName()));
        if (!locations.add(normalizeLocation(request.endName()))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "起点、终点和经停点不能重复");
        }
        if (request.waypoints() != null) {
            for (TripSearchRequest.LocationCondition waypoint : request.waypoints()) {
                if (!locations.add(normalizeLocation(waypoint.name()))) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "起点、终点和经停点不能重复");
                }
            }
        }
    }

    private MatchTripDTO requireRecruitingTrip(Long tripId) {
        MatchTripDTO trip = requireTrip(tripId);
        if (!isRecruiting(trip.status()) || !Integer.valueOf(1).equals(trip.publicFlag())
                || trip.departureTime() == null || !trip.departureTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "行程已停止招募");
        }
        return trip;
    }

    private MatchTeamDTO requireJoinableTeam(Long tripId) {
        MatchTeamDTO team = teamPort.findActiveTeamByTripId(tripId);
        if (team == null || team.currentMemberCount() == null || team.maxMemberCount() == null
                || team.currentMemberCount() >= team.maxMemberCount()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "行程车队不存在或已满员");
        }
        return team;
    }

    private TripSearchCardResponse toSearchCard(MatchTripDTO trip, MatchTeamDTO team, List<String> waypoints,
            int score, int startDistance, int endDistance) {
        int current = team.currentMemberCount() == null ? 1 : team.currentMemberCount();
        int max = team.maxMemberCount() == null ? 1 : team.maxMemberCount();
        return new TripSearchCardResponse(
                String.valueOf(trip.tripId()), String.valueOf(trip.userId()), trip.ownerNickname(),
                trip.ownerAvatarImageKey(), trip.title(), trip.startName(), trip.endName(), waypoints,
                format(trip.departureTime()), estimatedEnd(trip), trip.vehicleSummary(), current, max,
                Math.max(0, max - current), score, startDistance, endDistance, trip.status(), current < max);
    }

    private int score(int startDistance, int endDistance, int radius, long gapMinutes, int timeWindow,
            int waypointMatches, int waypointCount, int remaining) {
        int startScore = (int) Math.round(30D * Math.max(0, 1D - (double) startDistance / radius));
        int endScore = (int) Math.round(30D * Math.max(0, 1D - (double) endDistance / radius));
        int timeScore = (int) Math.round(20D * Math.max(0, 1D - (double) gapMinutes / timeWindow));
        int waypointScore = waypointCount == 0 ? 10
                : (int) Math.round(10D * waypointMatches / waypointCount);
        int capacityScore = Math.min(10, 5 + remaining);
        return Math.max(0, Math.min(100, startScore + endScore + timeScore + waypointScore + capacityScore));
    }

    private int matchingWaypoints(TripSearchRequest request, List<String> targetWaypoints) {
        if (request.waypoints() == null || request.waypoints().isEmpty()) {
            return 0;
        }
        Set<String> target = targetWaypoints.stream().map(this::normalizeLocation)
                .collect(java.util.stream.Collectors.toSet());
        return (int) request.waypoints().stream()
                .map(TripSearchRequest.LocationCondition::name)
                .map(this::normalizeLocation)
                .filter(target::contains)
                .count();
    }

    private List<String> waypointNames(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            var root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            root.forEach(node -> {
                String name = node.path("name").asText("");
                if (!StringUtils.hasText(name)) name = node.path("locationName").asText("");
                if (StringUtils.hasText(name)) names.add(name.trim());
            });
            return names;
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private String estimatedEnd(MatchTripDTO trip) {
        if (trip.departureTime() == null) return null;
        if (trip.routeDuration() != null && trip.routeDuration() > 0) {
            return format(trip.departureTime().plusSeconds(trip.routeDuration()));
        }
        return format(trip.departureTime().plusDays(Math.max(1, trip.estimatedDays() == null ? 1 : trip.estimatedDays())));
    }

    private String format(LocalDateTime value) {
        return value == null ? null : FORMATTER.format(value);
    }

    private int meters(double startLat, double startLng, Double targetLat, Double targetLng) {
        return (int) Math.round(distanceKm(startLat, startLng, targetLat, targetLng) * 1000);
    }

    private String normalizedSort(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "MATCH_SCORE";
    }

    private String normalizeLocation(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private MatchTripDTO requireTrip(Long id) { MatchTripDTO trip = tripPort.getTrip(id); if (trip == null) throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在"); return trip; }
    private void requireMatchable(MatchTripDTO trip) { if (!isMatchable(trip)) throw new BusinessException(ResultCode.BAD_REQUEST, "只有公开且处于招募中或进行中的行程可以发现同行"); }
    private boolean isMatchable(MatchTripDTO trip) {
        return List.of("PUBLISHED", "RUNNING", "ONGOING").contains(trip.status())
                && Integer.valueOf(1).equals(trip.publicFlag());
    }
    private boolean isRecruiting(String status) {
        return List.of("PUBLISHED", "RECRUITING").contains(status);
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
