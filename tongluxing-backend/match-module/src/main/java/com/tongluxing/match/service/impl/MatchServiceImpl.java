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
import com.tongluxing.match.integration.MatchTripPort.MatchCandidateQuery;
import com.tongluxing.match.integration.MatchTripPort.MatchRecommendationCandidateDTO;
import com.tongluxing.match.mapper.MatchRecommendLogMapper;
import com.tongluxing.match.mapper.MatchResultMapper;
import com.tongluxing.match.mapper.TripFavoriteMapper;
import com.tongluxing.match.mapper.TripConsultationMapper;
import com.tongluxing.match.mapper.TripRecommendationMetricsMapper;
import com.tongluxing.match.mapper.TripSearchHistoryMapper;
import com.tongluxing.match.mapper.TripRecommendationMetricsMapper.TripRecommendationMetricRow;
import com.tongluxing.match.service.MatchService;
import com.tongluxing.match.service.RecommendImpressionService;
import com.tongluxing.match.service.RecommendImpressionService.ImpressionCommand;
import com.tongluxing.match.service.RecommendationPool;
import com.tongluxing.match.service.RecommendationPool.RecommendationPoolItem;
import com.tongluxing.match.service.RecommendationPoolService;
import com.tongluxing.match.service.RecommendationPoolService.PoolLoadResult;
import com.tongluxing.match.service.RecommendationPoolService.PoolSlice;
import com.tongluxing.match.config.RecommendationPoolProperties;
import com.tongluxing.match.vo.*;
import com.tongluxing.user.support.CurrentUserContext;
import com.tongluxing.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * “发现同行”匹配业务实现。
 *
 * <p>模块同时提供两类推荐：发布行程后计算 source→target 的持久化推荐；没有源行程
 * 时，根据公开行程、当前位置和筛选条件实时生成发现列表。所有候选在返回前都要再次
 * 检查公开性、招募状态、出发时间、容量和当前用户关系，避免展示过期结果。</p>
 *
 * <p>评分使用可解释的确定性规则，不把分数当作授权依据。访问推荐、收藏、咨询和
 * 申请仍分别执行资源归属、关注关系和车队容量校验，并写入推荐漏斗日志。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchServiceImpl implements MatchService {
    /** 接口统一日期时间格式；record 中使用字符串是为了稳定跨端展示协议。 */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 预计算推荐允许的最大出发时间差：24 小时。 */
    private static final int MAX_TIME_GAP_MINUTES = 24 * 60;
    /** 推荐页统一否决条件：起点距离不能超过 100km。 */
    private static final int RECOMMEND_MAX_DISTANCE_METERS = 100_000;
    /** 推荐页统一否决条件：出发时间差不能超过 3 天。 */
    private static final long RECOMMEND_MAX_TIME_GAP_MINUTES = 3L * 24 * 60;
    /** 有基准行程时，综合顺路率低于 20% 的候选直接淘汰。 */
    private static final int RECOMMEND_MIN_MATCH_RATE = 20;
    /** 发现页最多对粗过滤后的前 200 条候选加载完整路线精算，防止大候选池再次拖垮数据库。 */
    private static final int ROUTE_FINE_CANDIDATE_LIMIT = 200;
    /** 行程领域只读端口。 */
    private final MatchTripPort tripPort;
    /** 车队领域查询与申请端口。 */
    private final MatchTeamPort teamPort;
    /** 持久化有方向的行程推荐结果。 */
    private final MatchResultMapper resultMapper;
    /** 记录推荐曝光、点击、申请和后续转化事件。 */
    private final MatchRecommendLogMapper recommendLogMapper;
    /** 从 Spring Security 获取可信的当前用户 ID。 */
    private final CurrentUserContext currentUserContext;
    /** 解析途经点 JSON、序列化评分明细和申请补充信息。 */
    private final ObjectMapper objectMapper;
    /** 读取公开用户资料和关注关系。 */
    private final UserService userService;
    /** 行程收藏关系数据访问。 */
    private final TripFavoriteMapper tripFavoriteMapper;
    /** 受控咨询请求数据访问。 */
    private final TripConsultationMapper tripConsultationMapper;
    /** 批量读取报名、收藏、评分和好评率，供推荐过滤与热度计算。 */
    private final TripRecommendationMetricsMapper recommendationMetricsMapper;
    /** 行程搜索历史数据访问。 */
    private final TripSearchHistoryMapper tripSearchHistoryMapper;
    /** 轻量推荐池、原子游标、防击穿锁、stale 和最近曝光。 */
    private final RecommendationPoolService recommendationPoolService;
    private final RecommendationPoolProperties recommendationPoolProperties;
    /** 曝光日志异步批量提交器。 */
    private final RecommendImpressionService recommendImpressionService;

    /**
     * 为一条公开可匹配行程重新生成推荐。
     *
     * <p>最多从 300 条公开行程构建候选池，排除自身行程与同一发起人的行程，仅保存
     * 时间差不超过 24 小时且综合分至少 50 的结果。Mapper 的 upsert 让规则重算幂等。</p>
     */
    @Override
    @Transactional
    public void generateTripRecommendations(Long tripId) {
        // 源行程必须真实存在，并且当前状态允许参与公开匹配。
        MatchTripDTO source = requireTrip(tripId);
        requireMatchable(source);
        // 所有本轮结果共用一个 calculatedAt，便于识别同一批次。
        LocalDateTime now = LocalDateTime.now();
        List<MatchTripDTO> targets = tripPort.listPublicTrips(new MatchCandidateQuery(
                source.userId(), null, null, null, null, null, 300)).stream()
                .filter(target -> !target.tripId().equals(source.tripId()))
                .toList();
        List<Long> routeIds = new ArrayList<>();
        routeIds.add(source.tripId());
        routeIds.addAll(targets.stream().map(MatchTripDTO::tripId).toList());
        Map<Long, String> matchPolylines = tripPort.getMatchPolylines(routeIds);
        String sourcePolyline = matchPolylines.get(source.tripId());
        targets.stream()
                // calculate 只对轻量候选过滤后的集合使用一次批量读取的 RDP 匹配路线。
                .map(target -> calculate(source, target, now, sourcePolyline, matchPolylines.get(target.tripId())))
                .filter(result -> result.getDepartureGapMinutes() <= MAX_TIME_GAP_MINUTES && result.getMatchScore() >= 50)
                .forEach(resultMapper::upsert);
    }

    @Override
    public MatchRecommendationListResponse getTripRecommendations(Long tripId, Integer limit) {
        // 推荐结果包含用户源行程信息，只允许行程所有者查看。
        Long userId = currentUserContext.requireUserId();
        MatchTripDTO source = requireTrip(tripId);
        if (!source.userId().equals(userId)) throw new BusinessException(ResultCode.FORBIDDEN, "只能查看自己行程的同行推荐");
        requireMatchable(source);
        // 数据库先按分数排序；转换时再次过滤已删除、已私有或已结束的目标行程。
        List<MatchTripCardResponse> trips = resultMapper.findBySourceTrip(tripId, safeLimit(limit)).stream()
                .map(this::toTripCard).filter(java.util.Objects::nonNull).toList();
        // 只有实际返回给用户的卡片才计入曝光，避免把无效候选写入漏斗。
        trips.forEach(card -> log(userId, tripId, Long.valueOf(card.tripId()), value(card.teamId()), "IMPRESSION", card.matchId()));
        // 车队卡片只从存在活跃车队的可申请行程派生。
        List<MatchTeamCardResponse> teams = trips.stream().filter(card -> Boolean.TRUE.equals(card.joinable()))
                .map(card -> toTeamCard(source, teamPort.findActiveTeamByTripId(Long.valueOf(card.tripId()))))
                .filter(java.util.Objects::nonNull).toList();
        return new MatchRecommendationListResponse(String.valueOf(tripId), trips, teams);
    }

    @Override
    public MatchTripCardResponse getRecommendation(Long matchId) {
        Long userId = currentUserContext.requireUserId();
        MatchResult result = requireResult(matchId);
        // sourceUserId 固化了推荐归属，防止用户遍历 matchId 查看他人推荐。
        if (!result.getSourceUserId().equals(userId)) throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该推荐");
        // 持久化结果可能比目标行程生命周期更久，因此详情时必须实时重查目标。
        MatchTripCardResponse card = toTripCard(result);
        if (card == null) throw new BusinessException(ResultCode.NOT_FOUND, "目标行程不存在");
        // 权限与目标有效性检查通过后才记录 CLICK。
        log(userId, result.getSourceTripId(), result.getTargetTripId(), value(card.teamId()), "CLICK", String.valueOf(matchId));
        return card;
    }

    /** 通过当前用户拥有的推荐结果提交入队申请并记录 APPLY 转化。 */
    @Override
    @Transactional
    public MatchApplyResponse apply(Long matchId, String message) {
        Long userId = currentUserContext.requireUserId();
        MatchResult result = requireResult(matchId);
        if (!result.getSourceUserId().equals(userId)) throw new BusinessException(ResultCode.FORBIDDEN, "无权使用该推荐");
        // 推荐目标不一定已经建队；没有活跃车队时不能伪造申请。
        MatchTeamDTO team = teamPort.findActiveTeamByTripId(result.getTargetTripId());
        if (team == null) throw new BusinessException(ResultCode.BUSINESS_ERROR, "对方尚未创建可加入车队");
        // 空说明使用明确来源文案，便于队长理解申请入口。
        Long applicationId = teamPort.apply(team.teamId(), StringUtils.hasText(message) ? message.trim() : "通过发现同行申请加入",
                null, null);
        log(userId, result.getSourceTripId(), result.getTargetTripId(), team.teamId(), "APPLY", String.valueOf(matchId));
        return new MatchApplyResponse(String.valueOf(matchId), String.valueOf(result.getTargetTripId()),
                String.valueOf(team.teamId()), String.valueOf(applicationId), "PENDING");
    }

    /** 从地图附近卡片直接申请；该场景没有持久化 matchId。 */
    @Override
    @Transactional
    public MatchApplyResponse applyNearbyTrip(Long tripId, String message) {
        Long userId = currentUserContext.requireUserId();
        MatchTripDTO target = requireTrip(tripId);
        // 不允许通过附近入口加入自己创建的行程。
        if (target.userId().equals(userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能申请加入自己创建的行程");
        }
        // 地图列表可能已缓存，申请时重新检查公开性和招募状态。
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

    @Override
    public List<TripSearchHistoryResponse> getTripSearchHistory(Integer limit) {
        Long userId = currentUserContext.requireUserId();
        int safeLimit = limit == null ? 12 : Math.max(1, Math.min(limit, 30));
        return tripSearchHistoryMapper.findRecent(userId, safeLimit).stream()
                .map(row -> new TripSearchHistoryResponse(
                        String.valueOf(row.id()), row.keyword(), row.searchType(),
                        row.updatedAt() == null ? "" : row.updatedAt().format(FORMATTER)))
                .toList();
    }

    @Override
    @Transactional
    public TripSearchHistoryResponse recordTripSearchHistory(String keyword, String searchType) {
        Long userId = currentUserContext.requireUserId();
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (!StringUtils.hasText(normalizedKeyword)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "搜索关键词不能为空");
        }
        if (normalizedKeyword.length() > 80) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "搜索关键词不能超过80个字符");
        }
        String normalizedType = normalizeSearchHistoryType(searchType);
        LocalDateTime now = LocalDateTime.now();
        Long id = SnowflakeIdGenerator.nextId();
        tripSearchHistoryMapper.upsert(id, userId, normalizedKeyword, normalizedType, now);
        TripSearchHistoryMapper.HistoryRow row = tripSearchHistoryMapper.findRecent(userId, 30).stream()
                .filter(item -> item.keyword().equals(normalizedKeyword) && item.searchType().equals(normalizedType))
                .findFirst()
                .orElse(new TripSearchHistoryMapper.HistoryRow(id, normalizedKeyword, normalizedType, now));
        return new TripSearchHistoryResponse(String.valueOf(row.id()), row.keyword(), row.searchType(),
                row.updatedAt().format(FORMATTER));
    }

    @Override
    @Transactional
    public Boolean deleteTripSearchHistory(Long historyId) {
        if (historyId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "搜索历史ID不能为空");
        }
        return tripSearchHistoryMapper.deleteOne(currentUserContext.requireUserId(), historyId) > 0;
    }

    @Override
    @Transactional
    public Integer clearTripSearchHistory() {
        return tripSearchHistoryMapper.deleteAll(currentUserContext.requireUserId());
    }

    private String normalizeSearchHistoryType(String searchType) {
        String value = StringUtils.hasText(searchType)
                ? searchType.trim().toUpperCase(Locale.ROOT)
                : "DESTINATION";
        return Set.of("DESTINATION", "ORIGIN", "ROUTE", "USER", "TRIP_NUMBER").contains(value)
                ? value : "DESTINATION";
    }

    /** 记录行程开始/结束漏斗事件；未知动作静默忽略，避免污染统计枚举。 */
    @Override public void recordTripLifecycle(Long tripId, String actionType) {
        if (!"START".equals(actionType) && !"FINISH".equals(actionType)) return;
        MatchTripDTO trip = tripPort.getTrip(tripId);
        if (trip != null) log(trip.userId(), tripId, tripId, null, actionType, null);
    }

    /** 把车队审核结果关联到申请人最近一次命中目标行程的推荐。 */
    @Override public void recordTeamApplication(Long applicantUserId, Long targetTripId, Long teamId, String status) {
        // 只有源自推荐的申请才有 MatchResult；直接搜索/附近申请不会强行制造关联。
        MatchResult result = resultMapper.findLatestByApplicant(applicantUserId, targetTripId);
        if (result != null) log(applicantUserId, result.getSourceTripId(), targetTripId, teamId,
                "APPROVED".equals(status) ? "ACCEPT" : "REJECT", String.valueOf(result.getId()));
    }

    /** 根据用户位置筛选并按起点距离排序附近公开招募行程。 */
    @Override public NearbyTripListResponse getNearbyTrips(String latitude, String longitude, Integer radiusMeters, Integer limit) {
        // BigDecimal 解析先拒绝空值和非法数字，再转换为距离算法所需 double。
        double lat = parse(latitude, "纬度不能为空").doubleValue();
        double lng = parse(longitude, "经度不能为空").doubleValue();
        // 半径限制在 500 米~100 公里，防止异常参数退化为全量扫描。
        int radius = radiusMeters == null ? 5000 : Math.max(500, Math.min(radiusMeters, 100000));
        Long userId = currentUserContext.requireUserId();
        List<MatchTripDTO> nearby = tripPort.listPublicTrips(new MatchCandidateQuery(
                userId, null, LocalDateTime.now(), null, null, null, 300)).stream()
                .filter(t -> isRecruiting(t.status()))
                .filter(t -> distanceKm(lat, lng, t.startLatitude(), t.startLongitude()) * 1000 <= radius)
                .sorted(java.util.Comparator.comparingDouble(t -> distanceKm(lat, lng, t.startLatitude(), t.startLongitude())))
                .limit(safeLimit(limit))
                .toList();
        Map<Long, MatchTeamDTO> teamByTripId = teamPort.findActiveTeamsByTripIds(
                nearby.stream().map(MatchTripDTO::tripId).toList());
        List<MatchTripCardResponse> trips = nearby.stream()
                .map(target -> cardWithoutMatch(target, teamByTripId.get(target.tripId())))
                .toList();
        return new NearbyTripListResponse(trips);
    }

    /** 查询公开活跃车队；当前版本保留坐标校验，距离筛选由端口候选能力演进。 */
    @Override public NearbyTeamListResponse getNearbyTeams(String latitude, String longitude, Integer radiusMeters, Integer limit) {
        // 即使当前端口暂未使用坐标，也拒绝无效请求，保持接口契约稳定。
        parse(latitude, "纬度不能为空"); parse(longitude, "经度不能为空");
        return new NearbyTeamListResponse(teamPort.listPublicActiveTeams(safeLimit(limit)).stream().map(t -> toTeamCard(null, t)).toList());
    }

    @Override
    public TripSearchPageResponse searchTrips(TripSearchRequest request) {
        // 执行时间顺序与地点唯一性等无法由单字段注解表达的校验。
        validateSearch(request);
        Long userId = currentUserContext.requireUserId();
        // 默认半径 50 公里，默认至少保留一个名额。
        int radius = request.radiusMeters() == null ? 50000 : request.radiusMeters();
        int minimumSeats = request.minimumRemainingSeats() == null ? 1 : request.minimumRemainingSeats();
        // 时间得分围绕请求窗口中心计算，而不是只靠起始时间。
        LocalDateTime center = request.departureStart().plusSeconds(
                Duration.between(request.departureStart(), request.departureEnd()).toSeconds() / 2);
        List<MatchTripDTO> searchPool = tripPort.listPublicTrips(new MatchCandidateQuery(
                userId, null, request.departureStart(), request.departureEnd(), null, null, 500));
        Map<Long, MatchTeamDTO> teamByTripId = teamPort.findActiveTeamsByTripIds(
                searchPool.stream().map(MatchTripDTO::tripId).toList());
        Set<Long> blockedTeamIds = teamPort.findBlockedTeamIds(
                teamByTripId.values().stream().map(MatchTeamDTO::teamId).toList(), userId);
        List<TripSearchCardResponse> candidates = new ArrayList<>();
        for (MatchTripDTO trip : searchPool) {
            // SQL 已提前按所有者与时间窗口收窄，这里保留状态/公开性防御校验。
            if (userId.equals(trip.userId()) || !isRecruiting(trip.status())
                    || !Integer.valueOf(1).equals(trip.publicFlag())
                    || trip.departureTime() == null || trip.departureTime().isBefore(LocalDateTime.now())) {
                continue;
            }
            // 一次批量读取活跃车队，避免搜索候选逐条查询 team。
            MatchTeamDTO team = teamByTripId.get(trip.tripId());
            if (team == null) {
                continue;
            }
            // 容量按车队实时人数计算，并防御空字段。
            int current = team.currentMemberCount() == null ? 1 : team.currentMemberCount();
            int max = team.maxMemberCount() == null ? 1 : team.maxMemberCount();
            int remaining = Math.max(0, max - current);
            if (remaining < minimumSeats) {
                continue;
            }
            if (Boolean.TRUE.equals(request.carpoolAllowed()) && max <= 1) {
                continue;
            }
            // 起点和终点都必须在允许半径内，避免只顺路一端的低质量结果。
            int startDistance = meters(request.startLatitude().doubleValue(), request.startLongitude().doubleValue(),
                    trip.startLatitude(), trip.startLongitude());
            int endDistance = meters(request.endLatitude().doubleValue(), request.endLongitude().doubleValue(),
                    trip.endLatitude(), trip.endLongitude());
            if (startDistance > radius || endDistance > radius) {
                continue;
            }
            if (StringUtils.hasText(request.vehicleType())
                    && !matchesVehicleRequirement(trip.vehicleRequirements(), request.vehicleType())) {
                continue;
            }
            if (Boolean.TRUE.equals(request.driverVerified()) && !Boolean.TRUE.equals(trip.driverVerified())) {
                continue;
            }
            // 如果用户明确给出途经点，候选至少需要匹配其中一个名称。
            List<String> targetWaypoints = waypointNames(trip.waypointsJson());
            int waypointMatches = matchingWaypoints(request, targetWaypoints);
            if (request.waypoints() != null && !request.waypoints().isEmpty() && waypointMatches == 0) {
                continue;
            }
            // 成员/待审申请已一次批量加载，不再为每个候选访问数据库。
            if (blockedTeamIds.contains(team.teamId())) continue;
            long gapMinutes = Math.abs(Duration.between(center, trip.departureTime()).toMinutes());
            int timeWindow = Math.max(1, request.timeToleranceMinutes() == null
                    ? (int) Math.max(60, Duration.between(request.departureStart(), request.departureEnd()).toMinutes() / 2)
                    : request.timeToleranceMinutes());
            // 综合起点、终点、时间、途经点与容量五个维度形成 0~100 分。
            int score = score(startDistance, endDistance, radius, gapMinutes, timeWindow,
                    waypointMatches, request.waypoints() == null ? 0 : request.waypoints().size(), remaining);
            candidates.add(toSearchCard(trip, team, targetWaypoints, score, startDistance, endDistance));
        }
        // 排序枚举不合法时退回综合匹配分，保证接口具有稳定默认行为。
        Comparator<TripSearchCardResponse> comparator = switch (normalizedSort(request.sortBy())) {
            case "DEPARTURE_TIME" -> Comparator.comparing(TripSearchCardResponse::departureTime);
            case "DISTANCE" -> Comparator.comparingInt(
                    card -> card.startDistanceMeters() + card.endDistanceMeters());
            default -> Comparator.comparingInt(TripSearchCardResponse::matchScore).reversed()
                    .thenComparing(TripSearchCardResponse::departureTime);
        };
        candidates.sort(comparator);
        // 先在内存完成全部业务过滤和排序，再安全截取当前页。
        int page = request.page() == null ? 1 : request.page();
        int size = request.size() == null ? 20 : request.size();
        int from = Math.min(candidates.size(), (page - 1) * size);
        int to = Math.min(candidates.size(), from + size);
        return new TripSearchPageResponse(page, size, (long) candidates.size(), candidates.subList(from, to));
    }

    @Override
    public TripSearchDetailResponse getSearchTripDetail(Long tripId) {
        // 详情读取时重新检查未来出发、公开招募和车队容量，不能只信列表时状态。
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
        // 申请瞬间再次读取车队并检查至少一个剩余名额，应对列表后的容量变化。
        MatchTeamDTO team = requireJoinableTeam(tripId);
        int remaining = team.maxMemberCount() - team.currentMemberCount();
        if (remaining < 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "剩余名额不足");
        }
        // 自驾信息作为结构化 JSON 交给 team-module，方便队长审核而非拼入留言。
        String questions = json(Map.of(
                "selfDrive", Boolean.TRUE.equals(request.selfDrive()),
                "source", "TRIP_SEARCH"));
        Long applicationId = teamPort.apply(team.teamId(),
                StringUtils.hasText(request.message()) ? request.message().trim() : "通过行程搜索申请加入",
                request.applicantVehicleId(), questions);
        log(userId, tripId, tripId, team.teamId(), "APPLY", "SEARCH:" + tripId);
        return new MatchApplyResponse(null, String.valueOf(tripId), String.valueOf(team.teamId()),
                String.valueOf(applicationId), "PENDING");
    }

    @Override
    public TripRecommendPageResponse recommendTrips(
            String sortBy, Boolean requestedUserHasTrip, Double latitude, Double longitude,
            Integer page, Integer pageSize) {
        long totalStarted = System.nanoTime();
        Long userId = currentUserContext.requireUserId();
        int safePage = page == null ? 1 : Math.max(1, page);
        int safePageSize = pageSize == null ? 10 : Math.max(1, Math.min(pageSize, 30));

        MatchTripDTO reference = tripPort.findRecommendationReferenceTrip(userId);
        boolean userHasTrip = reference != null;
        if (requestedUserHasTrip != null && requestedUserHasTrip != userHasTrip) {
            log.debug("recommend_user_trip_state_mismatch userId={} requested={} actual={}",
                    userId, requestedUserHasTrip, userHasTrip);
        }
        if (!userHasTrip && (latitude == null || longitude == null)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先开启定位，再查看附近推荐行程");
        }

        String effectiveSort = normalizeRecommendSort(sortBy, userHasTrip);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeBase = userHasTrip ? reference.departureTime() : now;
        if (timeBase == null) {
            TripRecommendPageResponse empty = new TripRecommendPageResponse(0L, List.of(), userHasTrip, effectiveSort,
                    reference == null ? null : String.valueOf(reference.tripId()));
            logRecommendTiming(reference, true, false, false, 0, new RecommendPerformanceMetrics(),
                    empty, 0, 0, 0, totalStarted);
            return empty;
        }
        String poolKey = recommendationPoolKey(userId, reference, effectiveSort, latitude, longitude);
        RecommendPerformanceMetrics metrics = new RecommendPerformanceMetrics();
        PoolLoadResult load = null;
        PoolSlice slice = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            load = recommendationPoolService.getOrBuild(poolKey,
                    () -> buildRecommendationPool(userId, reference, userHasTrip, effectiveSort,
                            latitude, longitude, timeBase, now, metrics));
            if (load.pool() == null) break;
            if (load.pool().items().isEmpty()) {
                slice = new PoolSlice(List.of(), 0, false);
                break;
            }
            slice = recommendationPoolService.claim(poolKey, load.sourceKey(), load.pool(), safePageSize);
            if (!slice.exhausted()) break;
            // stale 已经消费完时等待正在构建的新池，当前请求快速返回空批次，不反复旧池。
            if (load.stale()) break;
            recommendationPoolService.invalidateIfCurrent(poolKey, load.pool());
        }

        if (load == null || load.pool() == null || slice == null || slice.exhausted()) {
            TripRecommendPageResponse empty = new TripRecommendPageResponse(0L, List.of(), userHasTrip,
                    effectiveSort, reference == null ? null : String.valueOf(reference.tripId()));
            logRecommendTiming(reference, true, load != null && load.stale(), false, 0, metrics, empty,
                    load == null ? 0 : load.cacheReadMs(), load == null ? 0 : load.cacheWriteMs(),
                    0, totalStarted);
            return empty;
        }

        TripRecommendPageResponse response = hydrateRecommendationBatch(
                userId, load.pool(), slice, safePage, safePageSize, metrics);
        recommendationPoolService.markSeen(userId,
                response.list().stream().map(card -> Long.valueOf(card.tripId())).toList());

        long impressionStarted = System.nanoTime();
        submitRecommendImpressions(response, userId, reference,
                "RECOMMEND_POOL:" + load.pool().generationId() + ':' + effectiveSort);
        long impressionSubmitMs = millis(impressionStarted, System.nanoTime());
        logRecommendTiming(reference, load.cacheHit(), load.stale(), load.built(), slice.start(), metrics,
                response, load.cacheReadMs(), load.cacheWriteMs(), impressionSubmitMs, totalStarted);
        return response;
    }

    private RecommendationPool buildRecommendationPool(
            Long userId, MatchTripDTO reference, boolean userHasTrip, String effectiveSort,
            Double latitude, Double longitude,
            LocalDateTime timeBase, LocalDateTime now, RecommendPerformanceMetrics timing) {
        LocalDateTime departureFrom = timeBase.minusMinutes(RECOMMEND_MAX_TIME_GAP_MINUTES);
        if (departureFrom.isBefore(now)) departureFrom = now;
        LocalDateTime departureTo = timeBase.plusMinutes(RECOMMEND_MAX_TIME_GAP_MINUTES);

        long phaseStarted = System.nanoTime();
        List<MatchRecommendationCandidateDTO> candidates = tripPort.listRecommendationCandidates(
                new MatchCandidateQuery(userId, null, departureFrom, departureTo, null, null, 1000));
        timing.candidateDatabaseMs = millis(phaseStarted, System.nanoTime());
        timing.candidateCount = candidates.size();
        if (candidates.isEmpty()) {
            return emptyPool(userHasTrip, effectiveSort, reference);
        }

        List<Long> candidateIds = candidates.stream().map(MatchRecommendationCandidateDTO::tripId).toList();
        phaseStarted = System.nanoTime();
        Map<Long, MatchTeamDTO> teamByTripId = teamPort.findActiveTeamsByTripIds(candidateIds);
        Map<Long, TripRecommendationMetricRow> metrics = recommendationMetricsMapper.findByTripIds(candidateIds).stream()
                .collect(java.util.stream.Collectors.toMap(TripRecommendationMetricRow::tripId, row -> row));
        timing.teamAndMetricsMs = millis(phaseStarted, System.nanoTime());

        phaseStarted = System.nanoTime();
        List<RecommendationCandidate> coarseAccepted = new ArrayList<>();
        for (MatchRecommendationCandidateDTO trip : candidates) {
            if (!isRecruiting(trip.status()) || trip.departureTime() == null) continue;
            MatchTeamDTO team = teamByTripId.get(trip.tripId());
            if (team == null) continue;
            int currentVehicles = Math.max(0, trip.joinedVehicleCount() == null ? 0 : trip.joinedVehicleCount());
            int vehicleLimit = Math.max(0, trip.maxVehicleCount() == null ? 0 : trip.maxVehicleCount());
            boolean vehicleFull = vehicleLimit <= 0 || currentVehicles >= vehicleLimit;
            boolean memberFull = team.currentMemberCount() != null && team.maxMemberCount() != null
                    && team.currentMemberCount() >= team.maxMemberCount();
            if (vehicleFull || memberFull) continue;

            TripRecommendationMetricRow metric = metrics.getOrDefault(trip.tripId(),
                    new TripRecommendationMetricRow(trip.tripId(), 0, 0, 0.0D, 0.0D, 0));
            int ratingCount = Math.max(0, metric.ratingCount() == null ? 0 : metric.ratingCount());
            double leaderRating = ratingCount == 0 ? 0D
                    : Math.max(0D, Math.min(5D, metric.leaderRating() == null ? 0D : metric.leaderRating()));
            double baseLatitude = userHasTrip ? value(reference.startLatitude(), Double.NaN) : latitude;
            double baseLongitude = userHasTrip ? value(reference.startLongitude(), Double.NaN) : longitude;
            if (!Double.isFinite(baseLatitude) || !Double.isFinite(baseLongitude)
                    || trip.startLatitude() == null || trip.startLongitude() == null) continue;
            int distanceMeters = meters(baseLatitude, baseLongitude, trip.startLatitude(), trip.startLongitude());
            if (distanceMeters > RECOMMEND_MAX_DISTANCE_METERS) continue;
            long timeGapMinutes = Math.abs(Duration.between(timeBase, trip.departureTime()).toMinutes());
            if (timeGapMinutes > RECOMMEND_MAX_TIME_GAP_MINUTES) continue;
            int applications = Math.max(0, metric.applicationCount() == null ? 0 : metric.applicationCount());
            int favorites = Math.max(0, metric.favoriteCount() == null ? 0 : metric.favoriteCount());
            double positiveRate = ratingCount == 0 ? 0D : Math.max(0D, Math.min(1D,
                    metric.positiveRate() == null ? 0D : metric.positiveRate()));
            int heat = applications * 40 + favorites * 30 + (int) Math.round(positiveRate * 30D);
            coarseAccepted.add(new RecommendationCandidate(trip, team, null, heat, distanceMeters,
                    timeGapMinutes, leaderRating));
        }

        List<RecommendationCandidate> accepted = coarseAccepted;
        if (userHasTrip && !coarseAccepted.isEmpty()) {
            List<Long> routeIds = new ArrayList<>();
            routeIds.add(reference.tripId());
            routeIds.addAll(coarseAccepted.stream().map(value -> value.trip().tripId()).toList());
            long routeStarted = System.nanoTime();
            Map<Long, String> matchPolylines = tripPort.getMatchPolylines(routeIds);
            timing.routeDatabaseMs = millis(routeStarted, System.nanoTime());
            String referencePolyline = matchPolylines.get(reference.tripId());
            accepted = coarseAccepted.stream().map(candidate -> {
                int matchRate = recommendationMatchRate(reference, candidate.trip(), referencePolyline,
                        matchPolylines.get(candidate.trip().tripId()));
                return new RecommendationCandidate(candidate.trip(), candidate.team(), matchRate, candidate.heat(),
                        candidate.distanceMeters(), candidate.timeGapMinutes(), candidate.leaderRating());
            }).filter(candidate -> candidate.matchRate() >= RECOMMEND_MIN_MATCH_RATE).toList();
        }

        List<RecommendationCandidate> sorted = new ArrayList<>(accepted);
        Comparator<RecommendationCandidate> comparator = switch (effectiveSort) {
            case "DISTANCE" -> Comparator.comparingInt(RecommendationCandidate::distanceMeters);
            case "TIME" -> Comparator.comparingLong(RecommendationCandidate::timeGapMinutes);
            case "HEAT" -> Comparator.comparingInt(RecommendationCandidate::heat).reversed();
            default -> Comparator.comparingInt((RecommendationCandidate value) ->
                    value.matchRate() == null ? 0 : value.matchRate()).reversed();
        };
        sorted.sort(comparator.thenComparing(candidate -> candidate.trip().departureTime())
                .thenComparing(candidate -> candidate.trip().tripId()));
        timing.matchCalculationMs = millis(phaseStarted, System.nanoTime()) - timing.routeDatabaseMs;
        timing.acceptedCount = sorted.size();

        Set<Long> seen = recommendationPoolService.seenTripIds(userId);
        List<RecommendationCandidate> prioritized = new ArrayList<>(sorted.size());
        sorted.stream().filter(candidate -> !seen.contains(candidate.trip().tripId())).forEach(prioritized::add);
        // 候选不足时允许把近期看过的候选补在末尾，避免推荐结果被过滤为空。
        sorted.stream().filter(candidate -> seen.contains(candidate.trip().tripId())).forEach(prioritized::add);
        List<RecommendationPoolItem> items = prioritized.stream()
                .limit(recommendationPoolProperties.getMaxSize())
                .map(candidate -> new RecommendationPoolItem(candidate.trip().tripId(), candidate.matchRate(),
                        candidate.heat(), candidate.distanceMeters(), candidate.timeGapMinutes(),
                        candidate.leaderRating()))
                .toList();
        return new RecommendationPool(java.util.UUID.randomUUID().toString(), userHasTrip, effectiveSort,
                reference == null ? null : String.valueOf(reference.tripId()), items);
    }

    private TripRecommendPageResponse hydrateRecommendationBatch(
            Long userId, RecommendationPool pool, PoolSlice slice, int page, int pageSize,
            RecommendPerformanceMetrics timing) {
        List<Long> tripIds = slice.items().stream().map(RecommendationPoolItem::tripId).toList();
        long phaseStarted = System.nanoTime();
        Map<Long, MatchTeamDTO> teamByTripId = teamPort.findActiveTeamsByTripIds(tripIds);
        List<MatchTeamDTO> pageTeams = tripIds.stream().map(teamByTripId::get)
                .filter(java.util.Objects::nonNull).toList();
        Map<Long, String> relationships = teamPort.relationshipStatuses(pageTeams, userId);
        timing.relationshipDatabaseMs = millis(phaseStarted, System.nanoTime());

        phaseStarted = System.nanoTime();
        Map<Long, MatchTripDTO> details = tripPort.getTripDetails(tripIds);
        timing.detailDatabaseMs = millis(phaseStarted, System.nanoTime());
        List<TripRecommendCardResponse> list = slice.items().stream().map(item -> {
            MatchTripDTO detail = details.get(item.tripId());
            MatchTeamDTO team = teamByTripId.get(item.tripId());
            if (detail == null || team == null) return null;
            String relationship = relationships.getOrDefault(team.teamId(), "NONE");
            return toRecommendCard(item, team, detail, relationship, canSubmitApplication(relationship));
        }).filter(java.util.Objects::nonNull).toList();
        int sessionStart = Math.max(0, slice.start() - (Math.max(1, page) - 1) * pageSize);
        long sessionTotal = Math.max(0, pool.items().size() - sessionStart);
        return new TripRecommendPageResponse(sessionTotal, list, pool.userHasTrip(), pool.effectiveSort(),
                pool.referenceTripId());
    }

    private RecommendationPool emptyPool(boolean userHasTrip, String effectiveSort, MatchTripDTO reference) {
        return new RecommendationPool(java.util.UUID.randomUUID().toString(), userHasTrip, effectiveSort,
                reference == null ? null : String.valueOf(reference.tripId()), List.of());
    }

    private void submitRecommendImpressions(TripRecommendPageResponse response, Long userId,
                                            MatchTripDTO reference, String requestId) {
        if (response == null || response.list() == null || response.list().isEmpty()) return;
        recommendImpressionService.submit(response.list().stream().map(card -> new ImpressionCommand(
                userId, reference == null ? Long.valueOf(card.tripId()) : reference.tripId(),
                Long.valueOf(card.tripId()), value(card.teamId()), requestId)).toList());
    }

    private void logRecommendTiming(MatchTripDTO reference, boolean cacheHit, boolean stalePool,
                                    boolean poolBuilt, int cursorStart, RecommendPerformanceMetrics timing,
                                    TripRecommendPageResponse response,
                                    long cacheReadMs, long cacheWriteMs, long impressionSubmitMs,
                                    long totalStarted) {
        log.info("trip_recommend_timing cacheHit={} stalePool={} poolBuilt={} cursorStart={} "
                        + "referenceTripId={} candidateCount={} finalResultCount={} "
                        + "cacheReadMs={} candidateDatabaseMs={} teamAndMetricsMs={} routeDatabaseMs={} "
                        + "matchCalculationMs={} relationshipDatabaseMs={} detailDatabaseMs={} "
                        + "impressionSubmitMs={} cacheWriteMs={} totalMs={}",
                cacheHit, stalePool, poolBuilt, cursorStart,
                reference == null ? null : reference.tripId(), timing.candidateCount,
                response == null || response.list() == null ? 0 : response.list().size(),
                cacheReadMs, timing.candidateDatabaseMs, timing.teamAndMetricsMs, timing.routeDatabaseMs,
                timing.matchCalculationMs, timing.relationshipDatabaseMs, timing.detailDatabaseMs,
                impressionSubmitMs, cacheWriteMs, millis(totalStarted, System.nanoTime()));
    }

    @Override
    public TripDiscoverPageResponse discoverTrips(
            String keyword, String searchType, String sort, Double latitude, Double longitude, Long referenceTripId,
            String startCity, String destination, String departureDateFrom, String departureDateTo,
            String vehicleType, Integer minimumRemainingSeats, Integer page, Integer size, Long refreshSeed) {
        long totalStarted = System.nanoTime();
        Long userId = currentUserContext.requireUserId();
        int safePage = page == null ? 1 : Math.max(1, page);
        int safeSize = size == null ? 12 : Math.max(1, Math.min(size, 30));
        int requiredSeats = minimumRemainingSeats == null ? 0 : Math.max(0, minimumRemainingSeats);
        String normalizedKeyword = normalizeLocation(keyword);
        String normalizedSearchType = normalizeSearchType(searchType);
        LocalDate from = parseDate(departureDateFrom);
        LocalDate to = parseDate(departureDateTo);
        MatchTripDTO reference = referenceTripId == null
                ? tripPort.findRecommendationReferenceTrip(userId)
                : requireOwnedReference(referenceTripId, userId);
        MatchTripDTO exactNumberTrip = "TRIP_NUMBER".equals(normalizedSearchType) && StringUtils.hasText(keyword)
                ? tripPort.getTripByNumber(keyword.trim().toUpperCase(Locale.ROOT)) : null;

        LocalDateTime departureFrom = from == null ? null : from.atStartOfDay();
        LocalDateTime departureTo = to == null ? null : to.plusDays(1).atStartOfDay().minusNanos(1);
        List<MatchTripDTO> candidates = "TRIP_NUMBER".equals(normalizedSearchType)
                ? (exactNumberTrip == null ? List.of() : List.of(exactNumberTrip))
                : tripPort.listPublicTrips(new MatchCandidateQuery(
                        userId, null, departureFrom, departureTo, startCity, destination, 1000));
        long lightweightDatabaseFinished = System.nanoTime();

        List<DiscoverCandidate> filtered = new ArrayList<>();
        Map<Long, Integer> scoreByTripId = new java.util.HashMap<>();
        for (MatchTripDTO trip : candidates) {
            if ("TRIP_NUMBER".equals(normalizedSearchType)
                    && (exactNumberTrip == null || !exactNumberTrip.tripId().equals(trip.tripId()))) continue;
            if (!isPubliclyVisible(trip) || trip.departureTime() == null) continue;
            int current = discoverCurrentCount(trip, null);
            int max = discoverMaxCount(trip, null, current);
            if (requiredSeats > 0 && max - current < requiredSeats) continue;
            List<String> waypoints = waypointNames(trip.waypointsJson());
            if (StringUtils.hasText(normalizedKeyword)
                    && !matchesKeyword(trip, waypoints, normalizedKeyword, normalizedSearchType)) continue;
            if (StringUtils.hasText(vehicleType)
                    && !matchesVehicleRequirement(trip.vehicleRequirements(), vehicleType)) continue;
            int distance = latitude == null || longitude == null ? -1
                    : meters(latitude, longitude, trip.startLatitude(), trip.startLongitude());
            int coarseScore = reference == null ? discoveryScore(trip, null, null, distance)
                    : routeMatchCoarseScore(reference, trip);
            scoreByTripId.put(trip.tripId(), coarseScore);
            filtered.add(new DiscoverCandidate(trip, waypoints, distance));
        }

        String normalizedSort = StringUtils.hasText(sort) ? sort.trim().toUpperCase(Locale.ROOT) : "RECOMMENDED";
        long routeDatabaseFinished = lightweightDatabaseFinished;
        // 有基准行程时先用轻量端点/时间/里程粗分缩小集合，再批量加载最多 200 条 RDP 匹配路线精算。
        if (reference != null && !filtered.isEmpty()) {
            List<DiscoverCandidate> coarseOrder = new ArrayList<>(filtered);
            coarseOrder.sort(Comparator.comparingInt((DiscoverCandidate value) ->
                    scoreByTripId.getOrDefault(value.trip().tripId(), 0)).reversed());
            int requiredFine = Math.max(ROUTE_FINE_CANDIDATE_LIMIT, safePage * safeSize * 3);
            int fineCount = Math.min(coarseOrder.size(), Math.min(1000, requiredFine));
            List<DiscoverCandidate> fineCandidates = coarseOrder.subList(0, fineCount);
            List<Long> routeIds = new ArrayList<>();
            routeIds.add(reference.tripId());
            routeIds.addAll(fineCandidates.stream().map(value -> value.trip().tripId()).toList());
            Map<Long, String> matchPolylines = tripPort.getMatchPolylines(routeIds);
            routeDatabaseFinished = System.nanoTime();
            String referencePolyline = matchPolylines.get(reference.tripId());
            for (DiscoverCandidate candidate : fineCandidates) {
                int score = routeMatchScore(reference, candidate.trip(), referencePolyline,
                        matchPolylines.get(candidate.trip().tripId())).total();
                scoreByTripId.put(candidate.trip().tripId(), score);
            }
        }

        Comparator<DiscoverCandidate> comparator = switch (normalizedSort) {
            case "NEARBY" -> Comparator.comparingInt(value -> value.distance() < 0 ? Integer.MAX_VALUE : value.distance());
            case "DEPARTURE_TIME" -> Comparator.comparing(value -> value.trip().departureTime());
            case "ROUTE_MATCH" -> Comparator.comparingInt((DiscoverCandidate value) ->
                    scoreByTripId.getOrDefault(value.trip().tripId(), 0)).reversed();
            default -> Comparator.comparingInt((DiscoverCandidate value) ->
                    scoreByTripId.getOrDefault(value.trip().tripId(), 0)).reversed();
        };
        filtered.sort(comparator.thenComparing(value -> value.trip().departureTime())
                .thenComparing(value -> value.trip().tripId()));

        int fromIndex = Math.min(filtered.size(), (safePage - 1) * safeSize);
        int toIndex = Math.min(filtered.size(), fromIndex + safeSize);
        List<DiscoverCandidate> pageCandidates = filtered.subList(fromIndex, toIndex);
        Map<Long, MatchTeamDTO> teamByTripId = teamPort.findActiveTeamsByTripIds(
                pageCandidates.stream().map(value -> value.trip().tripId()).toList());
        List<TripDiscoverCardResponse> pageRecords = pageCandidates.stream().map(candidate ->
                toDiscoverCard(candidate.trip(), teamByTripId.get(candidate.trip().tripId()), candidate.waypoints(),
                        scoreByTripId.getOrDefault(candidate.trip().tripId(), 0), candidate.distance(), userId,
                        reference != null, true)).toList();
        TripDiscoverPageResponse response = new TripDiscoverPageResponse(safePage, safeSize, (long) filtered.size(), pageRecords);
        long finished = System.nanoTime();
        log.info("trip_discover_timing referenceTripId={} candidates={} filtered={} returned={} lightweightDatabaseMs={} routeDatabaseMs={} calculationAndAssemblyMs={} totalMs={}",
                reference == null ? null : reference.tripId(), candidates.size(), filtered.size(), response.records().size(),
                millis(totalStarted, lightweightDatabaseFinished), millis(lightweightDatabaseFinished, routeDatabaseFinished),
                millis(routeDatabaseFinished, finished), millis(totalStarted, finished));
        return response;
    }

    @Override
    public TripDiscoverPageResponse getPublicTripsByUser(Long ownerUserId, Integer page, Integer size) {
        Long currentUserId = currentUserContext.requireUserId();
        // 查看他人行程时校验公开主页；本人可正常预览自己的公开行程。
        if (!currentUserId.equals(ownerUserId)) {
            userService.getPublicProfile(ownerUserId);
        }
        int safePage = page == null ? 1 : Math.max(1, page);
        int safeSize = size == null ? 10 : Math.max(1, Math.min(size, 30));
        List<MatchTripDTO> ownerTrips = new ArrayList<>(tripPort.listPublicTrips(new MatchCandidateQuery(
                null, ownerUserId, null, null, null, null, 1000)).stream()
                .filter(trip -> isPubliclyVisible(trip) && trip.departureTime() != null)
                .toList());
        ownerTrips.sort(Comparator.comparing(MatchTripDTO::departureTime).thenComparing(MatchTripDTO::tripId));
        int fromIndex = Math.min(ownerTrips.size(), (safePage - 1) * safeSize);
        int toIndex = Math.min(ownerTrips.size(), fromIndex + safeSize);
        List<MatchTripDTO> pageTrips = ownerTrips.subList(fromIndex, toIndex);
        Map<Long, MatchTeamDTO> teamByTripId = teamPort.findActiveTeamsByTripIds(
                pageTrips.stream().map(MatchTripDTO::tripId).toList());
        // 只有最终一页才补车队/关注关系，避免公开主页候选池出现 N+1。
        List<TripDiscoverCardResponse> records = pageTrips.stream().map(trip -> {
            MatchTeamDTO team = teamByTripId.get(trip.tripId());
            return toDiscoverCard(trip, team, waypointNames(trip.waypointsJson()),
                    discoveryScore(trip, team, null, -1), -1, currentUserId, false, true);
        }).toList();
        return new TripDiscoverPageResponse(safePage, safeSize, (long) ownerTrips.size(), records);
    }

    @Override
    public TripPublicDetailResponse getPublicTripDetail(Long tripId) {
        // 详情需要当前用户 ID 来派生关注、收藏、车队关系和按钮权限。
        Long userId = currentUserContext.requireUserId();
        MatchTripDTO trip = requirePublicTrip(tripId);
        MatchTeamDTO team = teamPort.findActiveTeamByTripId(tripId);
        int current = discoverCurrentCount(trip, team);
        int max = discoverMaxCount(trip, team, current);
        // 没有车队时关系只能是 OWNER/NONE；有车队时以 team-module 为准。
        String relationship = team == null
                ? (userId.equals(trip.userId()) ? "OWNER" : "NONE")
                : teamPort.relationshipStatus(team.teamId(), userId);
        boolean ownerTrip = userId.equals(trip.userId());
        // 本人查看自己的行程时无需查询关注关系；查看他人时只查询一次，并同时复用
        // following/mutual 两个结果，避免详情组装阶段重复访问关注表。
        var follow = ownerTrip ? null : userService.getFollowStatus(trip.userId());
        boolean followed = follow != null && Boolean.TRUE.equals(follow.following());
        // 成员列表由端口提供已脱敏摘要，最多读取 50 人。
        var members = team == null
                ? List.<TripPublicMemberResponse>of()
                : teamPort.listPublicMembers(team.teamId(), 50).stream()
                    .map(member -> new TripPublicMemberResponse(String.valueOf(member.userId()), member.nickname(),
                            member.avatarImageKey(), member.role(), member.certificationStatus(),
                            member.totalTripCount(), member.totalDistanceMeters(),
                            member.vehicleId() == null ? null : String.valueOf(member.vehicleId()),
                            member.vehicleSummary(), member.plateMask()))
                    .toList();
        boolean joinable = current < max && isJoinable(trip);
        // 申请要求非本人、有活跃车队、有容量且当前关系允许重新申请。
        boolean allowApply = team != null && !ownerTrip && joinable
                && canSubmitApplication(relationship);
        // 咨询只开放给关注者、互关用户或已入队成员，防止陌生人骚扰。
        boolean allowConsultation = !ownerTrip && (followed
                || Boolean.TRUE.equals(follow.mutual()) || "JOINED".equals(relationship));
        return new TripPublicDetailResponse(String.valueOf(trip.tripId()), trip.title(), trip.status(),
                trip.startName(), waypointNames(trip.waypointsJson()), trip.endName(), format(trip.departureTime()),
                trip.estimatedDays(), trip.description(),
                trip.startLatitude(), trip.startLongitude(), trip.endLatitude(), trip.endLongitude(),
                "", trip.routeDistance(),
                trip.routeDuration(), trip.joinedVehicleCount(), trip.maxVehicleCount(), current, max,
                 Math.max(0, max - current), null,
                 vehicleRequirements(trip.vehicleRequirements()), trip.budgetDescription(), trip.remark(),
                 team == null ? null : team.notice(), team == null ? null : team.teamDesc(),
                 discoverTags(trip), discoverOwner(trip, followed), members,
                relationship, ownerTrip, allowConsultation, allowApply, joinable,
                tripFavoriteMapper.exists(userId, tripId) > 0,
                tripType(trip), publisherRole(trip), passengerDemand(trip), hasCaptain(trip));
    }

    @Override
    public TripDiscoverPageResponse getFavoriteTrips(Integer page, Integer size) {
        Long userId = currentUserContext.requireUserId();
        int safePage = page == null ? 1 : Math.max(1, page);
        int safeSize = size == null ? 20 : Math.max(1, Math.min(size, 30));
        int offset = (safePage - 1) * safeSize;
        List<TripDiscoverCardResponse> records = tripFavoriteMapper
                .findTripIds(userId, offset, safeSize).stream()
                .map(tripPort::getTrip)
                .filter(java.util.Objects::nonNull)
                .map(trip -> {
                    MatchTeamDTO team = teamPort.findActiveTeamByTripId(trip.tripId());
                    return toDiscoverCard(trip, team, waypointNames(trip.waypointsJson()),
                            0, -1, userId, false, true);
                })
                .toList();
        return new TripDiscoverPageResponse(safePage, safeSize,
                tripFavoriteMapper.countByUserId(userId), records);
    }

    @Override
    @Transactional
    public Boolean favoriteTrip(Long tripId) {
        Long userId = currentUserContext.requireUserId();
        // 只能收藏当前仍可公开访问的招募行程。
        requireRecruitingTrip(tripId);
        // INSERT IGNORE 让重复收藏和并发请求保持幂等。
        tripFavoriteMapper.insert(SnowflakeIdGenerator.nextId(), userId, tripId);
        return true;
    }

    @Override
    @Transactional
    public Boolean unfavoriteTrip(Long tripId) {
        Long userId = currentUserContext.requireUserId();
        // 删除不存在的关系影响 0 行，最终状态仍是未收藏。
        tripFavoriteMapper.delete(userId, tripId);
        return false;
    }

    @Override
    @Transactional
    public TripConsultationResponse greetTrip(Long tripId) {
        Long userId = currentUserContext.requireUserId();
        MatchTripDTO trip = requireRecruitingTrip(tripId);
        if (userId.equals(trip.userId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能向自己发布的行程打招呼");
        }
        if (tripConsultationMapper.pendingExists(tripId, userId) > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "已经打过招呼，请等待队长回复");
        }
        // 推荐页问候使用固定、克制的文案，避免开放自由文本形成骚扰入口。
        tripConsultationMapper.insert(SnowflakeIdGenerator.nextId(), tripId, trip.title(), userId, trip.userId(),
                "你好，我对你的行程很感兴趣，想一起出发。");
        return new TripConsultationResponse(String.valueOf(tripId), trip.title(), String.valueOf(userId),
                String.valueOf(trip.userId()), "PENDING");
    }

    @Override
    @Transactional
    public TripConsultationResponse consultTrip(Long tripId, String content) {
        Long userId = currentUserContext.requireUserId();
        MatchTripDTO trip = requireRecruitingTrip(tripId);
        // 自己是接收者，没有创建咨询请求的业务意义。
        if (userId.equals(trip.userId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能咨询自己发布的行程");
        }
        MatchTeamDTO team = teamPort.findActiveTeamByTripId(tripId);
        String relationship = team == null ? "NONE" : teamPort.relationshipStatus(team.teamId(), userId);
        var follow = userService.getFollowStatus(trip.userId());
        // 已入队或互关表示双方已有直接沟通基础，无需再创建待审咨询。
        if ("JOINED".equals(relationship) || Boolean.TRUE.equals(follow.mutual())) {
            return new TripConsultationResponse(String.valueOf(tripId), trip.title(), String.valueOf(userId),
                    String.valueOf(trip.userId()), "DIRECT_ALLOWED");
        }
        // 对陌生人不开放咨询；仍可走正式入队申请由队长审核。
        if (!Boolean.TRUE.equals(follow.following())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "请先关注发起人，或直接提交加入申请");
        }
        // 数据库唯一键和前置查询共同阻止同一行程反复发送 PENDING 咨询。
        if (tripConsultationMapper.pendingExists(tripId, userId) > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该行程的咨询已发送，请等待队长回复");
        }
        // 空内容使用礼貌默认文案，非空内容去掉首尾空白并限制 500 字。
        String safeContent = StringUtils.hasText(content) ? content.trim() : "你好，我想咨询这段行程";
        if (safeContent.length() > 500) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "咨询内容不能超过500字");
        }
        tripConsultationMapper.insert(SnowflakeIdGenerator.nextId(), tripId, trip.title(), userId, trip.userId(),
                safeContent);
        return new TripConsultationResponse(String.valueOf(tripId), trip.title(), String.valueOf(userId),
                String.valueOf(trip.userId()), "PENDING");
    }

    private MatchResult calculate(MatchTripDTO source, MatchTripDTO target, LocalDateTime now,
                                  String sourcePolyline, String targetPolyline) {
        RouteMatchScore score = routeMatchScore(source, target, sourcePolyline, targetPolyline);
        // 雪花 ID 仅用于首次 insert；重复 pair 由 Mapper 保留原 ID 并刷新分数。
        MatchResult result = new MatchResult();
        result.setId(SnowflakeIdGenerator.nextId()); result.setSourceTripId(source.tripId()); result.setTargetTripId(target.tripId());
        result.setSourceUserId(source.userId()); result.setTargetUserId(target.userId());
        result.setMatchScore(score.total());
        result.setOverlapRate(score.overlapRate());
        result.setDistanceGapMeters(score.detourMeters());
        result.setDepartureGapMinutes(score.departureGapMinutes());
        // 保存各维度明细，运营可解释分数并在规则调整后比较重算结果。
        result.setScoreDetailJson(json(new ScoreDetail(score.endpointScore(), score.overlapScore(),
                score.timeScore(), score.detourScore())));
        result.setResultStatus("VALID"); result.setCalculatedAt(now); result.setCreatedAt(now); result.setUpdatedAt(now); result.setDeleted(0);
        return result;
    }

    private MatchTripCardResponse toTripCard(MatchResult result) {
        // 推荐结果可能引用后来删除的行程；转换失败以 null 交给列表过滤。
        MatchTripDTO target = tripPort.getTrip(result.getTargetTripId()); if (target == null) return null;
        // 目标变为私有或结束后，即使 result 仍为 VALID 也不得继续展示。
        if (!isMatchable(target)) return null;
        MatchTeamDTO team = teamPort.findActiveTeamByTripId(target.tripId());
        return new MatchTripCardResponse(String.valueOf(result.getId()), String.valueOf(target.tripId()), String.valueOf(target.userId()),
                target.title(), target.startName(), target.endName(), FORMATTER.format(target.departureTime()), target.travelDepth(),
                target.status(),
                result.getMatchScore(), result.getOverlapRate(), result.getDepartureGapMinutes(), result.getDistanceGapMeters(),
                target.expectedPeople(), team == null ? null : String.valueOf(team.teamId()),
                team == null ? null : team.currentMemberCount(), team == null ? null : team.maxMemberCount(), team != null);
    }

    private MatchTripCardResponse cardWithoutMatch(MatchTripDTO target, MatchTeamDTO team) {
        // 附近场景没有预计算推荐，使用统一中性分数；车队已由列表批量读取。
        return new MatchTripCardResponse(null, String.valueOf(target.tripId()), String.valueOf(target.userId()), target.title(),
                target.startName(), target.endName(), FORMATTER.format(target.departureTime()), target.travelDepth(), target.status(),
                80, 80, 0, 0,
                target.expectedPeople(), team == null ? null : String.valueOf(team.teamId()),
                team == null ? null : team.currentMemberCount(), team == null ? null : team.maxMemberCount(), team != null);
    }

    private MatchTeamCardResponse toTeamCard(MatchTripDTO source, MatchTeamDTO team) {
        // 无车队不能生成车队卡片；附近场景无 source 时使用中性 80 分。
        if (team == null) return null; int score = source == null ? 80 : (source.endName().equals(team.endName()) ? 90 : 65);
        return new MatchTeamCardResponse(String.valueOf(team.teamId()), String.valueOf(team.tripId()), team.teamName(), team.startName(),
                team.endName(), FORMATTER.format(team.departureTime()), team.currentMemberCount(), team.maxMemberCount(), score, score);
    }

    private TripDiscoverCardResponse toDiscoverCard(MatchTripDTO trip, MatchTeamDTO team, List<String> waypoints,
                                                     int score, int distance, Long currentUserId,
                                                     boolean exposeRouteMatch, boolean enrichRelationship) {
        // 容量同时兼容已建车队和只有行程车辆数量的情况。
        int current = discoverCurrentCount(trip, team);
        int max = discoverMaxCount(trip, team, current);
        // relationshipStatus 必须以当前用户为观察者，不可缓存为所有用户共享字段。
        String relationship = !enrichRelationship ? "NONE" : team == null
                ? (currentUserId.equals(trip.userId()) ? "OWNER" : "NONE")
                : teamPort.relationshipStatus(team.teamId(), currentUserId);
        return new TripDiscoverCardResponse(String.valueOf(trip.tripId()), trip.title(), trip.status(),
                trip.startName(), waypoints, trip.endName(), format(trip.departureTime()), trip.estimatedDays(),
                trip.description(), trip.joinedVehicleCount(), trip.maxVehicleCount(), current, max,
                Math.max(0, max - current), exposeRouteMatch ? score : null,
                distance < 0 ? null : distance,
                discoverTags(trip),
                relationship, discoverOwner(trip, currentUserId, enrichRelationship),
                tripType(trip), publisherRole(trip), passengerDemand(trip), hasCaptain(trip));
    }

    /** 兼容历史行程空字段，保证发现页拿到稳定的 P0 行程类型。 */
    private String tripType(MatchTripDTO trip) {
        if (StringUtils.hasText(trip.tripType())) {
            return trip.tripType();
        }
        return trip.vehicleId() == null ? "PASSENGER_DEMAND" : "DRIVER_TRIP";
    }

    /** 发布身份与行程类型保持一致。 */
    private String publisherRole(MatchTripDTO trip) {
        if (StringUtils.hasText(trip.publisherRole())) {
            return trip.publisherRole();
        }
        return passengerDemand(trip) ? "PASSENGER" : "DRIVER";
    }

    private boolean passengerDemand(MatchTripDTO trip) {
        return "PASSENGER_DEMAND".equals(tripType(trip));
    }

    private boolean hasCaptain(MatchTripDTO trip) {
        return trip.captainUserId() != null;
    }

    /** 优先读取车队实时人数；未建队时回退到行程已加入车辆数，且至少包含发起人。 */
    private int discoverCurrentCount(MatchTripDTO trip, MatchTeamDTO team) {
        if (team != null && team.currentMemberCount() != null) {
            return Math.max(1, team.currentMemberCount());
        }
        return Math.max(1, trip.joinedVehicleCount() == null ? 1 : trip.joinedVehicleCount());
    }

    /** 选择车队或行程配置的容量，并保证最大值不会小于当前人数。 */
    private int discoverMaxCount(MatchTripDTO trip, MatchTeamDTO team, int current) {
        Integer configured = team != null ? team.maxMemberCount() : trip.maxVehicleCount();
        return Math.max(current, configured == null ? current : configured);
    }

    /** 构造发起人公开摘要，并实时计算当前用户是否已关注。 */
    private TripDiscoverOwnerResponse discoverOwner(MatchTripDTO trip, Long currentUserId,
                                                     boolean queryFollowStatus) {
        // 自己无需查询关注关系，固定为未关注；他人关系由 user-module 统一判断。
        boolean followed = queryFollowStatus && !currentUserId.equals(trip.userId())
                && Boolean.TRUE.equals(userService.getFollowStatus(trip.userId()).following());
        return discoverOwner(trip, followed);
    }

    /** 使用已经取得的关注状态组装发起人摘要，供详情接口避免重复查询。 */
    private TripDiscoverOwnerResponse discoverOwner(MatchTripDTO trip, boolean followed) {
        // driverVerified 映射为公开认证状态，不暴露证件详情或审核原因。
        return new TripDiscoverOwnerResponse(String.valueOf(trip.userId()), trip.ownerNickname(),
                trip.ownerAvatarImageKey(), trip.ownerLevelCode(),
                Boolean.TRUE.equals(trip.driverVerified()) ? "APPROVED" : "UNSUBMITTED",
                null, trip.ownerTotalTripCount(), trip.ownerTotalDistanceMeters(), trip.ownerLastActiveAt(),
                trip.ownerBadgeCount(), followed);
    }

    /** 从车辆要求与驾驶认证状态生成简短公开标签。 */
    private List<String> discoverTags(MatchTripDTO trip) {
        List<String> tags = new ArrayList<>();
        // “不限”是筛选默认值，不作为有信息量的展示标签。
        tags.addAll(vehicleRequirements(trip.vehicleRequirements()).stream()
                .filter(value -> !"不限".equals(value)).toList());
        if (Boolean.TRUE.equals(trip.driverVerified())) tags.add("驾驶员已认证");
        return tags;
    }


    private boolean matchesKeyword(MatchTripDTO trip, List<String> waypoints,
                                   String keyword, String searchType) {
        // 不同搜索类型限制匹配字段；ALL 会覆盖标题、路线、发起人和途经点。
        return switch (searchType) {
            case "TRIP_NUMBER" -> trip.tripNumber() != null
                    && trip.tripNumber().equalsIgnoreCase(keyword);
            case "DESTINATION" -> normalizeLocation(trip.endName()).contains(keyword);
            case "ORIGIN" -> normalizeLocation(trip.startName()).contains(keyword);
            case "ROUTE" -> normalizeLocation(String.join(" ", valueOrEmpty(trip.title()),
                    valueOrEmpty(trip.startName()), valueOrEmpty(trip.endName()),
                    String.join(" ", waypoints))).contains(keyword);
            default -> discoverText(trip, waypoints).contains(keyword);
        };
    }

    /** 将搜索类型归一为受支持枚举，未知值按 ALL 处理。 */
    private String normalizeSearchType(String value) {
        if (!StringUtils.hasText(value)) return "ALL";
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return List.of("DESTINATION", "ORIGIN", "ROUTE", "TRIP_NUMBER").contains(normalized)
                ? normalized : "ALL";
    }
    /** 拼接发现页可搜索文本并统一去空白、小写。 */
    private String discoverText(MatchTripDTO trip, List<String> waypoints) {
        return normalizeLocation(String.join(" ", valueOrEmpty(trip.title()), valueOrEmpty(trip.startName()),
                valueOrEmpty(trip.endName()), valueOrEmpty(trip.ownerNickname()), String.join(" ", waypoints)));
    }

    /** 将推荐候选转换为前端卡片。 */
    private TripRecommendCardResponse toRecommendCard(RecommendationPoolItem item, MatchTeamDTO team,
                                                      MatchTripDTO trip,
                                                      String relationshipStatus, boolean allowApply) {
        int currentVehicles = Math.max(0, trip.joinedVehicleCount() == null ? 0 : trip.joinedVehicleCount());
        int vehicleLimit = Math.max(currentVehicles, trip.maxVehicleCount() == null ? currentVehicles : trip.maxVehicleCount());
        return new TripRecommendCardResponse(
                String.valueOf(trip.tripId()), String.valueOf(team.teamId()),
                StringUtils.hasText(team.teamName()) ? team.teamName() : trip.title(),
                trip.startName(), trip.endName(), format(trip.departureTime()),
                currentVehicles, vehicleLimit, item.matchRate(), item.heat(),
                item.distanceMeters(), Math.round(item.distanceMeters() / 100D) / 10D,
                item.timeGapMinutes(), Math.round(item.leaderRating() * 10D) / 10D,
                String.valueOf(trip.userId()), trip.ownerNickname(), trip.ownerAvatarImageKey(),
                relationshipStatus, true, allowApply, trip.status());
    }

    /**
     * 严格按需求权重计算综合顺路率：空间 50%、时间 25%、节奏 15%、站点 10%。
     */
    private int recommendationMatchRate(MatchTripDTO source, MatchRecommendationCandidateDTO target,
                                        String sourcePolyline, String targetPolyline) {
        int spatial = spatialMatchScore(source, target, sourcePolyline, targetPolyline);
        int time = timeMatchScore(source.departureTime(), target.departureTime());
        int pace = paceMatchScore(source, target);
        int stops = stationMatchScore(waypointNames(source.waypointsJson()), waypointNames(target.waypointsJson()));
        return Math.max(0, Math.min(100, (int) Math.round(
                spatial * 0.50D + time * 0.25D + pace * 0.15D + stops * 0.10D)));
    }

    /** 空间匹配综合真实路线重合和起终点接近度，结果统一为 0~100。 */
    private int spatialMatchScore(MatchTripDTO source, MatchRecommendationCandidateDTO target,
                                  String sourcePolyline, String targetPolyline) {
        int routeOverlap = routeOverlapRate(sourcePolyline, targetPolyline);
        int start = proximityScore(distanceKm(source.startLatitude(), source.startLongitude(),
                target.startLatitude(), target.startLongitude()), 100, 100);
        int end = proximityScore(distanceKm(source.endLatitude(), source.endLongitude(),
                target.endLatitude(), target.endLongitude()), 100, 150);
        // 有真实路线时以路线重合为主；路线缺失时由起终点接近度安全补足。
        if (routeOverlap <= 0) return (start + end) / 2;
        return (int) Math.round(routeOverlap * 0.60D + start * 0.20D + end * 0.20D);
    }

    /** 3 天时间窗口内线性衰减，时间完全一致为 100。 */
    private int timeMatchScore(LocalDateTime source, LocalDateTime target) {
        if (source == null || target == null) return 0;
        long gap = Math.abs(Duration.between(source, target).toMinutes());
        return (int) Math.round(100D * Math.max(0D,
                1D - (double) gap / RECOMMEND_MAX_TIME_GAP_MINUTES));
    }

    /**
     * 行程节奏由旅行深度标签与日均里程共同判断。两者各占该维度的一半，避免只比较
     * 一个文本标签或只比较路线长度。
     */
    private int paceMatchScore(MatchTripDTO source, MatchRecommendationCandidateDTO target) {
        int depthScore;
        if (!StringUtils.hasText(source.travelDepth()) || !StringUtils.hasText(target.travelDepth())) {
            depthScore = 70;
        } else {
            depthScore = source.travelDepth().equalsIgnoreCase(target.travelDepth()) ? 100 : 40;
        }
        double sourceDaily = dailyDistance(source);
        double targetDaily = dailyDistance(target.routeDistance(), target.estimatedDays());
        int distanceScore = sourceDaily <= 0 || targetDaily <= 0 ? 70
                : (int) Math.round(Math.min(sourceDaily, targetDaily) / Math.max(sourceDaily, targetDaily) * 100D);
        return (depthScore + distanceScore) / 2;
    }

    private double dailyDistance(MatchTripDTO trip) {
        return dailyDistance(trip.routeDistance(), trip.estimatedDays());
    }

    private double dailyDistance(Integer routeDistance, Integer estimatedDays) {
        if (routeDistance == null || routeDistance <= 0) return 0D;
        return (double) routeDistance / Math.max(1, estimatedDays == null ? 1 : estimatedDays);
    }

    /** 途经站点使用 Jaccard 相似度；两边都无途经点视为同样的直达节奏。 */
    private int stationMatchScore(List<String> source, List<String> target) {
        Set<String> left = source.stream().map(this::normalizeLocation).filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> right = target.stream().map(this::normalizeLocation).filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        if (left.isEmpty() && right.isEmpty()) return 100;
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        return union.isEmpty() ? 0 : (int) Math.round(intersection.size() * 100D / union.size());
    }

    /** 规范化推荐排序，并在无行程时把 match_rate 自动切换为 heat。 */
    private String normalizeRecommendSort(String sortBy, boolean userHasTrip) {
        String normalized = StringUtils.hasText(sortBy)
                ? sortBy.trim().toLowerCase(Locale.ROOT) : "match_rate";
        return switch (normalized) {
            case "distance" -> "DISTANCE";
            case "time" -> "TIME";
            case "match_rate", "heat" -> userHasTrip ? "MATCH_RATE" : "HEAT";
            default -> userHasTrip ? "MATCH_RATE" : "HEAT";
        };
    }

    /** 为可空 Double 提供原始类型默认值。 */
    private double value(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    /** 推荐页内部候选，先统一过滤再排序分页。 */
    private record RecommendationCandidate(
            MatchRecommendationCandidateDTO trip, MatchTeamDTO team, Integer matchRate, int heat, int distanceMeters,
            long timeGapMinutes, double leaderRating
    ) { }

    private static final class RecommendPerformanceMetrics {
        private int candidateCount = -1;
        private int acceptedCount;
        private long candidateDatabaseMs;
        private long teamAndMetricsMs;
        private long routeDatabaseMs;
        private long matchCalculationMs;
        private long relationshipDatabaseMs;
        private long detailDatabaseMs;
    }

    /** 发现页轻量候选；完整路线不进入该对象。 */
    private record DiscoverCandidate(MatchTripDTO trip, List<String> waypoints, int distance) { }

    /**
     * 计算发现信息流排序分。
     *
     * <p>有基准行程时偏重起终点接近度、时间和途经点重合；无基准时偏重剩余容量、
     * 发起人历史行程、驾驶认证和当前位置距离。两种结果都限制在 0~100。</p>
     */
    private int discoveryScore(MatchTripDTO trip, MatchTeamDTO team, MatchTripDTO reference, int distance) {
        if (reference != null) {
            return routeMatchCoarseScore(reference, trip);
        }
        // 通用发现基础分 58，再按容量、活跃度、认证和距离增加。
        int current = discoverCurrentCount(trip, team);
        int remaining = Math.max(0, discoverMaxCount(trip, team, current) - current);
        int score = 58 + Math.min(10, remaining * 2)
                + Math.min(10, Math.max(0, trip.ownerTotalTripCount()))
                + (Boolean.TRUE.equals(trip.driverVerified()) ? 7 : 0);
        if (distance >= 0) score += Math.max(0, 15 - distance / 10000);
        return Math.max(0, Math.min(100, score));
    }

    /** 不读取 polyline 的粗排分，只用于在大候选池中选择值得进行路线精算的子集。 */
    private int routeMatchCoarseScore(MatchTripDTO source, MatchTripDTO target) {
        int start = proximityScore(distanceKm(source.startLatitude(), source.startLongitude(),
                target.startLatitude(), target.startLongitude()), 25, 80);
        int end = proximityScore(distanceKm(source.endLatitude(), source.endLongitude(),
                target.endLatitude(), target.endLongitude()), 25, 120);
        int gap = source.departureTime() == null || target.departureTime() == null ? MAX_TIME_GAP_MINUTES
                : (int) Math.min(Integer.MAX_VALUE,
                Math.abs(Duration.between(source.departureTime(), target.departureTime()).toMinutes()));
        int time = gap >= MAX_TIME_GAP_MINUTES ? 0 : 25 - gap * 25 / MAX_TIME_GAP_MINUTES;
        int sourceDistance = source.routeDistance() == null ? 0 : source.routeDistance();
        int targetDistance = target.routeDistance() == null ? 0 : target.routeDistance();
        int detour = sourceDistance <= 0 ? 0 : Math.max(0, 25 - (int) Math.round(25D
                * Math.abs(targetDistance - sourceDistance) / sourceDistance));
        return Math.max(0, Math.min(100, start + end + time + detour));
    }

    /**
     * 推荐列表与发现列表共用同一套顺路评分：起终点 40、真实路线重合 35、
     * 出发时间 15、预计绕行 10。输入是按配置完成 RDP 与点数上限处理的关键点。
     */
    private RouteMatchScore routeMatchScore(MatchTripDTO source, MatchTripDTO target,
                                            String sourcePolyline, String targetPolyline) {
        int start = proximityScore(distanceKm(source.startLatitude(), source.startLongitude(),
                target.startLatitude(), target.startLongitude()), 20, 80);
        int end = proximityScore(distanceKm(source.endLatitude(), source.endLongitude(),
                target.endLatitude(), target.endLongitude()), 20, 120);
        int overlapRate = routeOverlapRate(sourcePolyline, targetPolyline);
        int overlap = Math.round(overlapRate * 35F / 100F);
        int gap = source.departureTime() == null || target.departureTime() == null ? MAX_TIME_GAP_MINUTES
                : (int) Math.min(Integer.MAX_VALUE,
                Math.abs(Duration.between(source.departureTime(), target.departureTime()).toMinutes()));
        int time = gap >= MAX_TIME_GAP_MINUTES ? 0 : 15 - gap * 15 / MAX_TIME_GAP_MINUTES;
        int sourceDistance = source.routeDistance() == null ? 0 : source.routeDistance();
        int targetDistance = target.routeDistance() == null ? 0 : target.routeDistance();
        int detourMeters = Math.abs(targetDistance - sourceDistance);
        int detour = sourceDistance <= 0 ? 0
                : Math.max(0, 10 - (int) Math.round(10D * detourMeters / sourceDistance));
        return new RouteMatchScore(Math.min(100, start + end + overlap + time + detour),
                overlapRate, gap, detourMeters, start + end, overlap, time, detour);
    }

    private int routeOverlapRate(String sourcePolyline, String targetPolyline) {
        List<double[]> source = routePoints(sourcePolyline);
        List<double[]> target = routePoints(targetPolyline);
        if (source.size() < 2 || target.size() < 2) return 0;

        int matched = 0;
        for (double[] point : source) {
            if (distanceToPolylineKm(point, target) <= 5D) matched++;
        }
        return (int) Math.round(matched * 100D / source.size());
    }

    private List<double[]> routePoints(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            var root = objectMapper.readTree(json);
            if (!root.isArray() || root.isEmpty()) return List.of();
            List<double[]> points = new ArrayList<>(root.size());
            for (var node : root) {
                double latitude = node.has("latitude") ? node.path("latitude").asDouble(Double.NaN)
                        : node.path("lat").asDouble(Double.NaN);
                double longitude = node.has("longitude") ? node.path("longitude").asDouble(Double.NaN)
                        : node.path("lng").asDouble(Double.NaN);
                if (Double.isFinite(latitude) && Double.isFinite(longitude)) {
                    points.add(new double[]{latitude, longitude});
                }
            }
            return points;
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private double distanceToPolylineKm(double[] point, List<double[]> polyline) {
        double min = Double.POSITIVE_INFINITY;
        for (int i = 1; i < polyline.size(); i++) {
            min = Math.min(min, distanceToSegmentKm(point, polyline.get(i - 1), polyline.get(i)));
        }
        return min;
    }

    /** 局部等距投影计算点到折线段距离，匹配阈值为公里级时精度足够。 */
    private double distanceToSegmentKm(double[] point, double[] start, double[] end) {
        double referenceLatitude = Math.toRadians((point[0] + start[0] + end[0]) / 3D);
        double longitudeScale = 111.320D * Math.cos(referenceLatitude);
        double latitudeScale = 110.540D;
        double sx = start[1] * longitudeScale;
        double sy = start[0] * latitudeScale;
        double ex = end[1] * longitudeScale;
        double ey = end[0] * latitudeScale;
        double px = point[1] * longitudeScale;
        double py = point[0] * latitudeScale;
        double dx = ex - sx;
        double dy = ey - sy;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 1e-12D) return Math.hypot(px - sx, py - sy);
        double ratio = ((px - sx) * dx + (py - sy) * dy) / lengthSquared;
        ratio = Math.max(0D, Math.min(1D, ratio));
        return Math.hypot(px - (sx + ratio * dx), py - (sy + ratio * dy));
    }

    /** 获取并校验基准行程归当前用户所有，防止使用他人行程作为推荐上下文。 */
    private MatchTripDTO requireOwnedReference(Long tripId, Long userId) {
        MatchTripDTO trip = requireTrip(tripId);
        if (!userId.equals(trip.userId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能使用自己的行程作为顺路匹配基准");
        }
        return trip;
    }

    /** 解析 yyyy-MM-dd 日期；空文本表示不设置日期过滤。 */
    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "日期格式应为 yyyy-MM-dd");
        }
    }

    /**
     * 校验高级搜索的跨字段规则。
     *
     * <p>结束时间必须晚于开始时间；起点、终点和所有途经点在去空白、忽略大小写后
     * 不能重名，避免构造无意义路线。</p>
     */
    private void validateSearch(TripSearchRequest request) {
        if (!request.departureEnd().isAfter(request.departureStart())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "出发时间范围不正确");
        }
        // Set.add 返回 false 表示规范化后的地点已经存在。
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

    /** 查询一条当前仍公开、招募且尚未出发的行程。 */
    private MatchTripDTO requireRecruitingTrip(Long tripId) {
        MatchTripDTO trip = requireTrip(tripId);
        if (!isRecruiting(trip.status()) || !Integer.valueOf(1).equals(trip.publicFlag())
                || trip.departureTime() == null || !trip.departureTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "行程已停止招募");
        }
        return trip;
    }

    /** 公开详情只校验可见性，报名等写操作仍继续使用 requireRecruitingTrip。 */
    private MatchTripDTO requirePublicTrip(Long tripId) {
        MatchTripDTO trip = requireTrip(tripId);
        if (!isPubliclyVisible(trip)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "公开行程不存在");
        }
        return trip;
    }

    private boolean isPubliclyVisible(MatchTripDTO trip) {
        return Integer.valueOf(1).equals(trip.publicFlag())
                && ("PUBLISHED".equals(trip.status()) || "RECRUITING".equals(trip.status())
                || "RUNNING".equals(trip.status()) || "ONGOING".equals(trip.status()));
    }

    private boolean isJoinable(MatchTripDTO trip) {
        return isRecruiting(trip.status()) && trip.departureTime() != null
                && trip.departureTime().isAfter(LocalDateTime.now());
    }

    /** 查询目标行程的活跃车队并保证仍有容量。 */
    private MatchTeamDTO requireJoinableTeam(Long tripId) {
        MatchTeamDTO team = teamPort.findActiveTeamByTripId(tripId);
        if (team == null || team.currentMemberCount() == null || team.maxMemberCount() == null
                || team.currentMemberCount() >= team.maxMemberCount()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "行程车队不存在或已满员");
        }
        return team;
    }

    /** 把候选行程、实时车队容量和本次搜索评分转换为公开卡片。 */
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

    /**
     * 计算高级搜索综合分：起点 30、终点 30、时间 20、途经点 10、容量 10。
     */
    private int score(int startDistance, int endDistance, int radius, long gapMinutes, int timeWindow,
            int waypointMatches, int waypointCount, int remaining) {
        // 各距离/时间维度随偏差线性衰减，到达容忍边界后为 0。
        int startScore = (int) Math.round(30D * Math.max(0, 1D - (double) startDistance / radius));
        int endScore = (int) Math.round(30D * Math.max(0, 1D - (double) endDistance / radius));
        int timeScore = (int) Math.round(20D * Math.max(0, 1D - (double) gapMinutes / timeWindow));
        // 未指定途经点时给满基础分；指定后按命中比例计分。
        int waypointScore = waypointCount == 0 ? 10
                : (int) Math.round(10D * waypointMatches / waypointCount);
        int capacityScore = Math.min(10, 5 + remaining);
        return Math.max(0, Math.min(100, startScore + endScore + timeScore + waypointScore + capacityScore));
    }

    /** 统计请求途经点名称与候选途经点名称的规范化交集数量。 */
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

    /**
     * 从行程途经点 JSON 中提取展示名称。
     *
     * <p>兼容 name 与历史 locationName 字段；空值、非数组或损坏 JSON 都降级为空列表，
     * 不因一条历史脏数据中断整页发现结果。</p>
     */
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
            // 首选当前字段 name，缺失时兼容旧字段 locationName。
            root.forEach(node -> {
                String name = node.path("name").asText("");
                if (!StringUtils.hasText(name)) name = node.path("locationName").asText("");
                if (StringUtils.hasText(name)) names.add(name.trim());
            });
            return names;
        } catch (JsonProcessingException ignored) {
            // 途经点只是辅助展示/评分字段，解析失败时安全降级而非返回 500。
            return List.of();
        }
    }

    /** 根据路线秒数优先推算结束时间，否则按预计天数回退。 */
    private String estimatedEnd(MatchTripDTO trip) {
        if (trip.departureTime() == null) return null;
        if (trip.routeDuration() != null && trip.routeDuration() > 0) {
            return format(trip.departureTime().plusSeconds(trip.routeDuration()));
        }
        return format(trip.departureTime().plusDays(Math.max(1, trip.estimatedDays() == null ? 1 : trip.estimatedDays())));
    }

    /** 将可空时间统一转换为接口约定格式。 */
    private String format(LocalDateTime value) {
        return value == null ? null : FORMATTER.format(value);
    }

    /** 计算两点球面距离并从公里换算为整数米。 */
    private int meters(double startLat, double startLng, Double targetLat, Double targetLng) {
        return (int) Math.round(distanceKm(startLat, startLng, targetLat, targetLng) * 1000);
    }

    /** 归一化高级搜索排序字段，空值使用 MATCH_SCORE。 */
    private String normalizedSort(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "MATCH_SCORE";
    }

    /** 去除全部空白并转小写，用于地点、路线和关键词的宽松文本比较。 */
    private String normalizeLocation(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /** 判断用户选择车型是否满足候选行程要求；“不限”匹配任意车型。 */
    private boolean matchesVehicleRequirement(String requirements, String selected) {
        if (!StringUtils.hasText(selected)) return true;
        List<String> values = vehicleRequirements(requirements);
        return values.contains("不限")
                || values.stream().anyMatch(value -> value.equalsIgnoreCase(selected.trim()));
    }

    /** 把逗号分隔车辆要求清洗为去重列表；未配置时返回“ 不限 ”语义。 */
    private List<String> vehicleRequirements(String requirements) {
        if (!StringUtils.hasText(requirements)) return List.of("不限");
        return java.util.Arrays.stream(requirements.split(","))
                .map(String::trim).filter(StringUtils::hasText).distinct().toList();
    }

    /**
     * 判断当前关系是否允许再次提交入队申请。
     *
     * <p>主动取消后当前已不存在有效申请，因此 CANCELLED/CANCELED 与 NONE 一样
     * 必须恢复申请入口。这里保留取消状态兼容旧接口和历史数据。</p>
     */
    private boolean canSubmitApplication(String relationshipStatus) {
        if (!StringUtils.hasText(relationshipStatus)) return true;
        return switch (relationshipStatus.trim().toUpperCase(Locale.ROOT)) {
            case "NONE", "REJECTED", "CANCELLED", "CANCELED" -> true;
            default -> false;
        };
    }

    /** 为 String.join 提供空安全文本。 */
    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 查询必需行程，不存在时统一转换为业务 NOT_FOUND。 */
    private MatchTripDTO requireTrip(Long id) { MatchTripDTO trip = tripPort.getTrip(id); if (trip == null) throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在"); return trip; }
    /** 拒绝私有或生命周期不支持匹配的源行程。 */
    private void requireMatchable(MatchTripDTO trip) { if (!isMatchable(trip)) throw new BusinessException(ResultCode.BAD_REQUEST, "只有公开且处于招募中或进行中的行程可以发现同行"); }
    /** 判断行程是否公开且处于可参与预计算推荐的状态。 */
    private boolean isMatchable(MatchTripDTO trip) {
        return ("PUBLISHED".equals(trip.status()) || "RUNNING".equals(trip.status())
                || "ONGOING".equals(trip.status()))
                && Integer.valueOf(1).equals(trip.publicFlag());
    }
    /** 判断行程是否仍接受新成员；兼容 PUBLISHED 与 RECRUITING 两种上游状态。 */
    private boolean isRecruiting(String status) {
        return "PUBLISHED".equals(status) || "RECRUITING".equals(status);
    }
    /** 查询仍为 VALID 的推荐结果。 */
    private MatchResult requireResult(Long id) { MatchResult r = resultMapper.findById(id); if (r == null || !"VALID".equals(r.getResultStatus())) throw new BusinessException(ResultCode.NOT_FOUND, "推荐结果不存在"); return r; }
    /** 按距离在给定 range 内线性衰减，返回 0~max 的接近度分数。 */
    private int proximityScore(double km, int max, double range) { return (int) Math.round(max * Math.max(0, 1 - km / range)); }
    /** 使用 Haversine 公式计算两组经纬度的地表距离（公里）；坐标缺失返回哨兵值 999。 */
    private double distanceKm(Double a, Double b, Double c, Double d) { if (a == null || b == null || c == null || d == null) return 999; double p1=Math.toRadians(a),p2=Math.toRadians(c),x=p2-p1,y=Math.toRadians(d-b);double h=Math.sin(x/2)*Math.sin(x/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(y/2)*Math.sin(y/2);return 6371*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h)); }
    /** 把返回数量限制在 1~50，空值默认 20。 */
    private int safeLimit(Integer limit) { return limit == null ? 20 : Math.max(1, Math.min(limit, 50)); }
    private String recommendationPoolKey(Long userId, MatchTripDTO reference, String sort, Double latitude,
                                         Double longitude) {
        String ref = reference == null ? "none" : String.valueOf(reference.tripId());
        // 有基准行程时距离计算只使用基准行程坐标，客户端当前位置不会影响结果，不进入 key。
        String lat = reference != null ? "reference"
                : latitude == null ? "none" : String.format(Locale.ROOT, "%.3f", latitude);
        String lng = reference != null ? "reference"
                : longitude == null ? "none" : String.format(Locale.ROOT, "%.3f", longitude);
        return recommendationPoolService.poolKey(userId, ref, sort, lat + ':' + lng);
    }

    private long millis(long start, long end) { return (end - start) / 1_000_000L; }
    /** 解析经纬度文本；空值和非数字分别返回清晰的 BAD_REQUEST。 */
    private BigDecimal parse(String value, String message) { if (!StringUtils.hasText(value)) throw new BusinessException(ResultCode.BAD_REQUEST,message); try{return new BigDecimal(value);}catch(NumberFormatException e){throw new BusinessException(ResultCode.BAD_REQUEST,"经纬度格式错误");} }
    /** 以统一 DISCOVER_COMPANION 场景追加一条推荐漏斗日志。 */
    private void log(Long userId, Long tripId, Long targetTripId, Long targetTeamId, String action, String requestId) { recommendLogMapper.insert(SnowflakeIdGenerator.nextId(),userId,tripId,targetTripId,targetTeamId,"DISCOVER_COMPANION",action,requestId,null); }
    /** 把 VO 中可空字符串 ID 转回 Mapper 所需 Long。 */
    private Long value(String id) { return StringUtils.hasText(id) ? Long.valueOf(id) : null; }
    /** 序列化辅助 JSON；失败时降级为空对象，避免可选审计明细阻断主业务。 */
    private String json(Object value) { try{return objectMapper.writeValueAsString(value);}catch(JsonProcessingException e){return "{}";} }
    /**
     * 可持久化的预计算评分明细。
     *
     * @param routeScore 起点接近分
     * @param destinationScore 终点接近分
     * @param timeScore 出发时间接近分
     * @param interestScore 旅行深度相似分
     */
    private record ScoreDetail(int routeScore,int destinationScore,int timeScore,int interestScore) { }
    private record RouteMatchScore(int total, int overlapRate, int departureGapMinutes,
                                   int detourMeters, int endpointScore, int overlapScore,
                                   int timeScore, int detourScore) { }
}
