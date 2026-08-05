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
import com.tongluxing.match.mapper.TripRecommendationMetricsMapper;
import com.tongluxing.match.mapper.TripRecommendationMetricsMapper.TripRecommendationMetricRow;
import com.tongluxing.match.service.MatchService;
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
    /** 队长评分低于 3.0 的行程不进入推荐列表。 */
    private static final double RECOMMEND_MIN_LEADER_RATING = 3.0D;
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
        tripPort.listPublicTrips(300).stream()
                // 自己不能推荐给自己；同一用户的另一条行程也不构成“同行”。
                .filter(target -> !target.tripId().equals(source.tripId()) && !target.userId().equals(source.userId()))
                // calculate 生成可解释的路线、终点、时间和兴趣四维分数。
                .map(target -> calculate(source, target, now))
                // 在持久化前淘汰时间过远或整体相关性过低的候选。
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

    /** 记录行程开始/结束漏斗事件；未知动作静默忽略，避免污染统计枚举。 */
    @Override public void recordTripLifecycle(Long tripId, String actionType) {
        if (!List.of("START", "FINISH").contains(actionType)) return;
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
        List<MatchTripCardResponse> trips = tripPort.listPublicTrips(300).stream()
                .filter(t -> isRecruiting(t.status()))
                // 不向用户展示自己发布的附近行程。
                .filter(t -> !userId.equals(t.userId()))
                .filter(t -> distanceKm(lat, lng, t.startLatitude(), t.startLongitude()) * 1000 <= radius)
                .sorted(java.util.Comparator.comparingDouble(t -> distanceKm(lat, lng, t.startLatitude(), t.startLongitude())))
                .limit(safeLimit(limit))
                .map(this::cardWithoutMatch)
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
        List<TripSearchCardResponse> candidates = new ArrayList<>();
        for (MatchTripDTO trip : tripPort.listPublicTrips(500)) {
            // 第一阶段过滤所有者、状态、公开性、过期时间及请求时间窗口。
            if (userId.equals(trip.userId()) || !isRecruiting(trip.status())
                    || !Integer.valueOf(1).equals(trip.publicFlag())
                    || trip.departureTime() == null || trip.departureTime().isBefore(LocalDateTime.now())
                    || trip.departureTime().isBefore(request.departureStart())
                    || trip.departureTime().isAfter(request.departureEnd())) {
                continue;
            }
            // 没有活跃车队、已经入队或已有待审申请的行程不应再次出现。
            MatchTeamDTO team = teamPort.findActiveTeamByTripId(trip.tripId());
            if (team == null || teamPort.hasActiveMembershipOrPending(team.teamId(), userId)) {
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
        Long userId = currentUserContext.requireUserId();
        int safePage = page == null ? 1 : Math.max(1, page);
        int safePageSize = pageSize == null ? 10 : Math.max(1, Math.min(pageSize, 30));

        // 是否有行程必须以数据库真值为准，不能由前端布尔参数决定算法分支。
        MatchTripDTO reference = tripPort.findRecommendationReferenceTrip(userId);
        boolean userHasTrip = reference != null;
        if (requestedUserHasTrip != null && requestedUserHasTrip != userHasTrip) {
            log.debug("recommend_user_trip_state_mismatch userId={} requested={} actual={}",
                    userId, requestedUserHasTrip, userHasTrip);
        }

        // 无自有行程时，距离基准必须使用用户当前定位。缺少定位无法严格执行 100km 否决规则。
        if (!userHasTrip && (latitude == null || longitude == null)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先开启定位，再查看附近推荐行程");
        }

        String effectiveSort = normalizeRecommendSort(sortBy, userHasTrip);
        LocalDateTime now = LocalDateTime.now();
        List<MatchTripDTO> candidates = tripPort.listPublicTrips(1000).stream()
                .filter(trip -> !userId.equals(trip.userId()))
                .filter(trip -> isRecruiting(trip.status()))
                .filter(trip -> trip.departureTime() != null && trip.departureTime().isAfter(now))
                .toList();

        if (candidates.isEmpty()) {
            return new TripRecommendPageResponse(0L, List.of(), userHasTrip, effectiveSort,
                    reference == null ? null : String.valueOf(reference.tripId()));
        }

        // 一次批量查询推荐指标，避免候选池逐条读取报名、收藏和评分。
        Map<Long, TripRecommendationMetricRow> metrics = recommendationMetricsMapper
                .findByTripIds(candidates.stream().map(MatchTripDTO::tripId).toList())
                .stream().collect(java.util.stream.Collectors.toMap(
                        TripRecommendationMetricRow::tripId, row -> row));

        List<RecommendationCandidate> accepted = new ArrayList<>();
        for (MatchTripDTO trip : candidates) {
            MatchTeamDTO team = teamPort.findActiveTeamByTripId(trip.tripId());
            // 推荐卡片必须支持申请入队，因此没有活跃车队的行程不进入推荐列表。
            if (team == null) continue;

            int currentVehicles = Math.max(0, trip.joinedVehicleCount() == null ? 0 : trip.joinedVehicleCount());
            int vehicleLimit = Math.max(0, trip.maxVehicleCount() == null ? 0 : trip.maxVehicleCount());
            boolean vehicleFull = vehicleLimit <= 0 || currentVehicles >= vehicleLimit;
            boolean memberFull = team.currentMemberCount() != null && team.maxMemberCount() != null
                    && team.currentMemberCount() >= team.maxMemberCount();
            if (vehicleFull || memberFull) continue;

            TripRecommendationMetricRow metric = metrics.getOrDefault(trip.tripId(),
                    new TripRecommendationMetricRow(trip.tripId(), 0, 0, 5.0D, 1.0D));
            double leaderRating = metric.leaderRating() == null ? 5.0D : metric.leaderRating();
            if (leaderRating < RECOMMEND_MIN_LEADER_RATING) continue;

            double baseLatitude = userHasTrip ? value(reference.startLatitude(), Double.NaN) : latitude;
            double baseLongitude = userHasTrip ? value(reference.startLongitude(), Double.NaN) : longitude;
            if (!Double.isFinite(baseLatitude) || !Double.isFinite(baseLongitude)
                    || trip.startLatitude() == null || trip.startLongitude() == null) {
                // 无法计算起点距离时不能绕过“超过 100km”否决条件。
                continue;
            }
            int distanceMeters = meters(baseLatitude, baseLongitude,
                    trip.startLatitude(), trip.startLongitude());
            if (distanceMeters > RECOMMEND_MAX_DISTANCE_METERS) continue;

            LocalDateTime timeBase = userHasTrip ? reference.departureTime() : now;
            if (timeBase == null) continue;
            long timeGapMinutes = Math.abs(Duration.between(timeBase, trip.departureTime()).toMinutes());
            if (timeGapMinutes > RECOMMEND_MAX_TIME_GAP_MINUTES) continue;

            Integer matchRate = userHasTrip ? recommendationMatchRate(reference, trip) : null;
            if (userHasTrip && (matchRate == null || matchRate < RECOMMEND_MIN_MATCH_RATE)) continue;

            int applications = Math.max(0, metric.applicationCount() == null ? 0 : metric.applicationCount());
            int favorites = Math.max(0, metric.favoriteCount() == null ? 0 : metric.favoriteCount());
            double positiveRate = Math.max(0D, Math.min(1D,
                    metric.positiveRate() == null ? 1D : metric.positiveRate()));
            // 严格使用：报名人数×40 + 收藏数×30 + 好评率×30。好评率按 0~1 存储。
            int heat = applications * 40 + favorites * 30 + (int) Math.round(positiveRate * 30D);

            String relationship = teamPort.relationshipStatus(team.teamId(), userId);
            boolean allowApply = List.of("NONE", "REJECTED").contains(relationship);
            accepted.add(new RecommendationCandidate(trip, team, matchRate, heat, distanceMeters,
                    timeGapMinutes, leaderRating, relationship, allowApply));
        }

        Comparator<RecommendationCandidate> comparator = switch (effectiveSort) {
            case "DISTANCE" -> Comparator.comparingInt(RecommendationCandidate::distanceMeters);
            case "TIME" -> Comparator.comparingLong(RecommendationCandidate::timeGapMinutes);
            case "HEAT" -> Comparator.comparingInt(RecommendationCandidate::heat).reversed();
            default -> Comparator.comparingInt((RecommendationCandidate value) ->
                    value.matchRate() == null ? 0 : value.matchRate()).reversed();
        };
        accepted.sort(comparator
                .thenComparing(candidate -> candidate.trip().departureTime())
                .thenComparing(candidate -> candidate.trip().tripId()));

        int fromIndex = Math.min(accepted.size(), (safePage - 1) * safePageSize);
        int toIndex = Math.min(accepted.size(), fromIndex + safePageSize);
        List<TripRecommendCardResponse> list = accepted.subList(fromIndex, toIndex).stream()
                .map(this::toRecommendCard)
                .toList();
        // 只有实际返回到当前页的行程记录曝光，避免重复分页请求放大统计。
        list.forEach(card -> log(userId, reference == null ? Long.valueOf(card.tripId()) : reference.tripId(),
                Long.valueOf(card.tripId()), value(card.teamId()), "IMPRESSION", "RECOMMEND:" + effectiveSort));
        return new TripRecommendPageResponse((long) accepted.size(), list, userHasTrip, effectiveSort,
                reference == null ? null : String.valueOf(reference.tripId()));
    }

    @Override
    public TripDiscoverPageResponse discoverTrips(
            String keyword, String searchType, String sort, Double latitude, Double longitude, Long referenceTripId,
            String startCity, String destination, String departureDateFrom, String departureDateTo,
            String vehicleType, Integer minimumRemainingSeats, Integer page, Integer size, Long refreshSeed) {
        // 发现信息流始终基于当前用户计算关系、关注状态，并排除不应重复申请的目标。
        long totalStarted = System.nanoTime();
        Long userId = currentUserContext.requireUserId();
        // 页大小最大 30，控制跨模块逐条聚合的查询成本。
        int safePage = page == null ? 1 : Math.max(1, page);
        int safeSize = size == null ? 12 : Math.max(1, Math.min(size, 30));
        int requiredSeats = minimumRemainingSeats == null ? 0 : Math.max(0, minimumRemainingSeats);
        // 文本去空白并统一小写；搜索类型不支持的值回退为 ALL。
        String normalizedKeyword = normalizeLocation(keyword);
        String normalizedSearchType = normalizeSearchType(searchType);
        // 日期字符串为空表示不限制，格式错误则直接返回明确的 400 业务异常。
        LocalDate from = parseDate(departureDateFrom);
        LocalDate to = parseDate(departureDateTo);
        // 基准行程只能使用自己的，防止借助他人路线推导非公开推荐上下文。
        MatchTripDTO reference = referenceTripId == null ? null : requireOwnedReference(referenceTripId, userId);
        List<TripDiscoverCardResponse> records = new ArrayList<>();
        Map<Long, MatchTripDTO> candidateById = new java.util.HashMap<>();
        // 推荐分用于服务端稳定排序；未选择基准行程时响应会隐藏 matchScore，
        // 因此不能再从响应对象中反向读取排序分，否则会因 null 自动拆箱触发 500。
        Map<Long, Integer> recommendationScoreByTripId = new java.util.HashMap<>();
        Map<Long, MatchTeamDTO> teamByTripId = new java.util.HashMap<>();
        // 行程号模式先走精确查询，随后仍在公开候选池中验证状态与其他过滤条件。
        MatchTripDTO exactNumberTrip = "TRIP_NUMBER".equals(normalizedSearchType) && StringUtils.hasText(keyword)
                ? tripPort.getTripByNumber(keyword.trim().toUpperCase(Locale.ROOT))
                : null;
        List<MatchTripDTO> candidates = tripPort.listPublicTrips(1000);
        long databaseFinished = System.nanoTime();
        for (MatchTripDTO trip : candidates) {
            candidateById.put(trip.tripId(), trip);
            if ("TRIP_NUMBER".equals(normalizedSearchType)
                    && (exactNumberTrip == null || !exactNumberTrip.tripId().equals(trip.tripId()))) {
                continue;
            }
            // “公开可见”和“仍可加入”是不同概念：已出发/进行中的公开行程仍展示，
            // 但按钮权限会在详情中按状态关闭。
            if (!isPubliclyVisible(trip) || trip.departureTime() == null) {
                continue;
            }
            // 基准行程属于“我的行程”固定卡片，不参与候选推荐和顺路评分。
            if (reference != null && reference.tripId().equals(trip.tripId())) continue;
            // 优先使用车队实时容量，没有车队时回退行程车辆容量。
            // 候选阶段使用行程快照容量完成排序，避免对完整候选池逐条查询 team-module。
            // 只有最终一页记录才补充实时车队容量和当前用户关系。
            MatchTeamDTO team = null;
            int current = discoverCurrentCount(trip, team);
            int max = discoverMaxCount(trip, team, current);
            if (requiredSeats > 0 && max - current < requiredSeats) continue;
            // 依次应用文本、起终点、日期、车辆和剩余名额过滤。
            List<String> waypoints = waypointNames(trip.waypointsJson());
            if (StringUtils.hasText(normalizedKeyword)
                    && !matchesKeyword(trip, waypoints, normalizedKeyword, normalizedSearchType)) continue;
            if (StringUtils.hasText(startCity) && !normalizeLocation(trip.startName()).contains(normalizeLocation(startCity))) continue;
            if (StringUtils.hasText(destination) && !normalizeLocation(trip.endName()).contains(normalizeLocation(destination))) continue;
            if (from != null && trip.departureTime().toLocalDate().isBefore(from)) continue;
            if (to != null && trip.departureTime().toLocalDate().isAfter(to)) continue;
            if (StringUtils.hasText(vehicleType)
                    && !matchesVehicleRequirement(trip.vehicleRequirements(), vehicleType)) continue;
            // 未提供位置时用 -1 表示“距离未知”，最终响应转换为 null。
            int distance = latitude == null || longitude == null ? -1
                    : meters(latitude, longitude, trip.startLatitude(), trip.startLongitude());
            // 有基准行程时计算顺路分，否则计算通用发现推荐分。
            int score = discoveryScore(trip, team, reference, distance);
            recommendationScoreByTripId.put(trip.tripId(), score);
            records.add(toDiscoverCard(trip, team, waypoints, score, distance, userId,
                    reference != null, false));
        }
        // NEARBY 优先距离；其余模式主要按分数或出发时间排序。
        String normalizedSort = StringUtils.hasText(sort) ? sort.trim().toUpperCase(Locale.ROOT) : "RECOMMENDED";
        Comparator<TripDiscoverCardResponse> comparator = switch (normalizedSort) {
            case "NEARBY" -> Comparator.comparingInt(card -> card.distanceMeters() == null || card.distanceMeters() < 0
                    ? Integer.MAX_VALUE : card.distanceMeters());
            case "DEPARTURE_TIME" -> Comparator.comparing(TripDiscoverCardResponse::departureTime);
            case "ROUTE_MATCH" -> Comparator.comparingInt((TripDiscoverCardResponse card) ->
                    recommendationScoreByTripId.getOrDefault(Long.valueOf(card.tripId()), 0)).reversed();
            default -> Comparator.comparingInt((TripDiscoverCardResponse card) ->
                    recommendationScoreByTripId.getOrDefault(Long.valueOf(card.tripId()), 0)).reversed();
        };
        // 二级排序保证同分数据在相同请求条件下稳定分页。
        records.sort(comparator.thenComparing(TripDiscoverCardResponse::departureTime)
                .thenComparing(TripDiscoverCardResponse::tripId));
        if (reference != null && isPubliclyVisible(reference)) {
            MatchTeamDTO ownTeam = teamPort.findActiveTeamByTripId(reference.tripId());
            records.add(0, toDiscoverCard(reference, ownTeam,
                    waypointNames(reference.waypointsJson()), 0, -1, userId, false, true));
        }
        // 推荐结果必须在相同基准和筛选条件下稳定；不再使用 refreshSeed 随机轮换。
        // 所有过滤排序完成后再分页，total 表示真实候选总数。
        int fromIndex = Math.min(records.size(), (safePage - 1) * safeSize);
        int toIndex = Math.min(records.size(), fromIndex + safeSize);
        List<TripDiscoverCardResponse> pageRecords = records.subList(fromIndex, toIndex).stream().map(card -> {
            MatchTripDTO trip = candidateById.get(Long.valueOf(card.tripId()));
            if (trip == null || card.tripId().equals(referenceTripId == null ? null : String.valueOf(referenceTripId))) {
                return card;
            }
            MatchTeamDTO pageTeam = teamByTripId.get(trip.tripId());
            if (pageTeam == null) {
                pageTeam = teamPort.findActiveTeamByTripId(trip.tripId());
                if (pageTeam != null) teamByTripId.put(trip.tripId(), pageTeam);
            }
            return toDiscoverCard(trip, pageTeam, waypointNames(trip.waypointsJson()),
                    card.matchScore() == null ? 0 : card.matchScore(),
                    card.distanceMeters() == null ? -1 : card.distanceMeters(), userId,
                    card.matchScore() != null, true);
        }).toList();
        TripDiscoverPageResponse response = new TripDiscoverPageResponse(safePage, safeSize, (long) records.size(),
                pageRecords);
        long finished = System.nanoTime();
        log.info("trip_discover_timing referenceTripId={} candidates={} returned={} databaseMs={} calculationAndAssemblyMs={} totalMs={}",
                referenceTripId, candidates.size(), response.records().size(), millis(totalStarted, databaseFinished),
                millis(databaseFinished, finished), millis(totalStarted, finished));
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
        List<TripDiscoverCardResponse> records = new ArrayList<>();
            // 用户公开主页保留已发布和进行中的公开行程。
        for (MatchTripDTO trip : tripPort.listPublicTrips(1000)) {
            if (!ownerUserId.equals(trip.userId()) || !Integer.valueOf(1).equals(trip.publicFlag())) {
                continue;
            }
            if (!isPubliclyVisible(trip) || trip.departureTime() == null) {
                continue;
            }
            MatchTeamDTO team = teamPort.findActiveTeamByTripId(trip.tripId());
            records.add(toDiscoverCard(trip, team, waypointNames(trip.waypointsJson()),
                    discoveryScore(trip, team, null, -1), -1, currentUserId, false, true));
        }
            // 按出发时间和 ID 稳定排序，避免公开主页翻页时记录跳动。
        records.sort(Comparator.comparing(TripDiscoverCardResponse::departureTime)
                .thenComparing(TripDiscoverCardResponse::tripId));
        int fromIndex = Math.min(records.size(), (safePage - 1) * safeSize);
        int toIndex = Math.min(records.size(), fromIndex + safeSize);
        return new TripDiscoverPageResponse(safePage, safeSize, (long) records.size(),
                records.subList(fromIndex, toIndex));
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
                            member.totalTripCount(), member.totalDistanceMeters()))
                    .toList();
        boolean joinable = current < max && isJoinable(trip);
        // 申请要求非本人、有活跃车队、有容量且当前关系允许重新申请。
        boolean allowApply = team != null && !ownerTrip && joinable
                && List.of("NONE", "REJECTED").contains(relationship);
        // 咨询只开放给关注者、互关用户或已入队成员，防止陌生人骚扰。
        boolean allowConsultation = !ownerTrip && (followed
                || Boolean.TRUE.equals(follow.mutual()) || "JOINED".equals(relationship));
        return new TripPublicDetailResponse(String.valueOf(trip.tripId()), trip.title(), trip.status(),
                trip.startName(), waypointNames(trip.waypointsJson()), trip.endName(), format(trip.departureTime()),
                trip.estimatedDays(), trip.description(), trip.coverImageKey(),
                trip.startLatitude(), trip.startLongitude(), trip.endLatitude(), trip.endLongitude(),
                "", trip.routeDistance(),
                trip.routeDuration(), trip.joinedVehicleCount(), trip.maxVehicleCount(), current, max,
                 Math.max(0, max - current), null,
                 vehicleRequirements(trip.vehicleRequirements()), trip.budgetDescription(), trip.remark(),
                 team == null ? null : team.notice(), team == null ? null : team.teamDesc(),
                 discoverTags(trip), discoverOwner(trip, followed), members,
                relationship, ownerTrip, allowConsultation, allowApply, joinable,
                tripFavoriteMapper.exists(userId, tripId) > 0);
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

    private MatchResult calculate(MatchTripDTO source, MatchTripDTO target, LocalDateTime now) {
        RouteMatchScore score = routeMatchScore(source, target);
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

    private MatchTripCardResponse cardWithoutMatch(MatchTripDTO target) {
        // 附近场景没有预计算推荐，使用统一中性分数并将 matchId 留空。
        MatchTeamDTO team = teamPort.findActiveTeamByTripId(target.tripId());
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
                discoverTags(trip), trip.coverImageKey(),
                relationship, discoverOwner(trip, currentUserId, enrichRelationship));
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
    private TripRecommendCardResponse toRecommendCard(RecommendationCandidate candidate) {
        MatchTripDTO trip = candidate.trip();
        MatchTeamDTO team = candidate.team();
        int currentVehicles = Math.max(0, trip.joinedVehicleCount() == null ? 0 : trip.joinedVehicleCount());
        int vehicleLimit = Math.max(currentVehicles, trip.maxVehicleCount() == null ? currentVehicles : trip.maxVehicleCount());
        return new TripRecommendCardResponse(
                String.valueOf(trip.tripId()), String.valueOf(team.teamId()),
                StringUtils.hasText(team.teamName()) ? team.teamName() : trip.title(),
                trip.startName(), trip.endName(), format(trip.departureTime()),
                currentVehicles, vehicleLimit, candidate.matchRate(), candidate.heat(),
                candidate.distanceMeters(), Math.round(candidate.distanceMeters() / 100D) / 10D,
                candidate.timeGapMinutes(), Math.round(candidate.leaderRating() * 10D) / 10D,
                String.valueOf(trip.userId()), trip.ownerNickname(), trip.ownerAvatarImageKey(),
                candidate.relationshipStatus(), true, candidate.allowApply(), trip.status());
    }

    /**
     * 严格按需求权重计算综合顺路率：空间 50%、时间 25%、节奏 15%、站点 10%。
     */
    private int recommendationMatchRate(MatchTripDTO source, MatchTripDTO target) {
        int spatial = spatialMatchScore(source, target);
        int time = timeMatchScore(source.departureTime(), target.departureTime());
        int pace = paceMatchScore(source, target);
        int stops = stationMatchScore(waypointNames(source.waypointsJson()), waypointNames(target.waypointsJson()));
        return Math.max(0, Math.min(100, (int) Math.round(
                spatial * 0.50D + time * 0.25D + pace * 0.15D + stops * 0.10D)));
    }

    /** 空间匹配综合真实路线重合和起终点接近度，结果统一为 0~100。 */
    private int spatialMatchScore(MatchTripDTO source, MatchTripDTO target) {
        int routeOverlap = routeOverlapRate(source.routePolyline(), target.routePolyline());
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
    private int paceMatchScore(MatchTripDTO source, MatchTripDTO target) {
        int depthScore;
        if (!StringUtils.hasText(source.travelDepth()) || !StringUtils.hasText(target.travelDepth())) {
            depthScore = 70;
        } else {
            depthScore = source.travelDepth().equalsIgnoreCase(target.travelDepth()) ? 100 : 40;
        }
        double sourceDaily = dailyDistance(source);
        double targetDaily = dailyDistance(target);
        int distanceScore = sourceDaily <= 0 || targetDaily <= 0 ? 70
                : (int) Math.round(Math.min(sourceDaily, targetDaily) / Math.max(sourceDaily, targetDaily) * 100D);
        return (depthScore + distanceScore) / 2;
    }

    private double dailyDistance(MatchTripDTO trip) {
        if (trip.routeDistance() == null || trip.routeDistance() <= 0) return 0D;
        return (double) trip.routeDistance() / Math.max(1, trip.estimatedDays() == null ? 1 : trip.estimatedDays());
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
            MatchTripDTO trip, MatchTeamDTO team, Integer matchRate, int heat, int distanceMeters,
            long timeGapMinutes, double leaderRating, String relationshipStatus, boolean allowApply
    ) {
    }

    /**
     * 计算发现信息流排序分。
     *
     * <p>有基准行程时偏重起终点接近度、时间和途经点重合；无基准时偏重剩余容量、
     * 发起人历史行程、驾驶认证和当前位置距离。两种结果都限制在 0~100。</p>
     */
    private int discoveryScore(MatchTripDTO trip, MatchTeamDTO team, MatchTripDTO reference, int distance) {
        if (reference != null) {
            return routeMatchScore(reference, trip).total();
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

    /**
     * 推荐列表与发现列表共用同一套顺路评分：起终点 40、真实路线重合 35、
     * 出发时间 15、预计绕行 10。实际路线最多等距采样 60 点，避免长折线 O(n²) 放大。
     */
    private RouteMatchScore routeMatchScore(MatchTripDTO source, MatchTripDTO target) {
        int start = proximityScore(distanceKm(source.startLatitude(), source.startLongitude(),
                target.startLatitude(), target.startLongitude()), 20, 80);
        int end = proximityScore(distanceKm(source.endLatitude(), source.endLongitude(),
                target.endLatitude(), target.endLongitude()), 20, 120);
        int overlapRate = routeOverlapRate(source.routePolyline(), target.routePolyline());
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
            boolean close = target.stream().anyMatch(candidate ->
                    distanceKm(point[0], point[1], candidate[0], candidate[1]) <= 5D);
            if (close) matched++;
        }
        return (int) Math.round(matched * 100D / source.size());
    }

    private List<double[]> routePoints(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            var root = objectMapper.readTree(json);
            if (!root.isArray() || root.isEmpty()) return List.of();
            int step = Math.max(1, (int) Math.ceil(root.size() / 60D));
            List<double[]> points = new ArrayList<>();
            for (int i = 0; i < root.size(); i += step) {
                var node = root.get(i);
                double latitude = node.has("latitude") ? node.path("latitude").asDouble()
                        : node.path("lat").asDouble(Double.NaN);
                double longitude = node.has("longitude") ? node.path("longitude").asDouble()
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
                && List.of("PUBLISHED", "RECRUITING", "RUNNING", "ONGOING").contains(trip.status());
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
        return List.of("PUBLISHED", "RUNNING", "ONGOING").contains(trip.status())
                && Integer.valueOf(1).equals(trip.publicFlag());
    }
    /** 判断行程是否仍接受新成员；兼容 PUBLISHED 与 RECRUITING 两种上游状态。 */
    private boolean isRecruiting(String status) {
        return List.of("PUBLISHED", "RECRUITING").contains(status);
    }
    /** 查询仍为 VALID 的推荐结果。 */
    private MatchResult requireResult(Long id) { MatchResult r = resultMapper.findById(id); if (r == null || !"VALID".equals(r.getResultStatus())) throw new BusinessException(ResultCode.NOT_FOUND, "推荐结果不存在"); return r; }
    /** 按距离在给定 range 内线性衰减，返回 0~max 的接近度分数。 */
    private int proximityScore(double km, int max, double range) { return (int) Math.round(max * Math.max(0, 1 - km / range)); }
    /** 使用 Haversine 公式计算两组经纬度的地表距离（公里）；坐标缺失返回哨兵值 999。 */
    private double distanceKm(Double a, Double b, Double c, Double d) { if (a == null || b == null || c == null || d == null) return 999; double p1=Math.toRadians(a),p2=Math.toRadians(c),x=p2-p1,y=Math.toRadians(d-b);double h=Math.sin(x/2)*Math.sin(x/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(y/2)*Math.sin(y/2);return 6371*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h)); }
    /** 把返回数量限制在 1~50，空值默认 20。 */
    private int safeLimit(Integer limit) { return limit == null ? 20 : Math.max(1, Math.min(limit, 50)); }
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
