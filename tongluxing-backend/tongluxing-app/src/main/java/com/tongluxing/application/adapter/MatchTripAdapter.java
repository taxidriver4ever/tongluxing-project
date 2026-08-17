package com.tongluxing.application.adapter;

import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.tongluxing.match.integration.MatchTripPort;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.config.RecommendationRouteProperties;
import com.tongluxing.trip.mapper.TripMapper;
import com.tongluxing.trip.mapper.TripRouteMapper;
import com.tongluxing.trip.query.TripMatchCandidateRow;
import com.tongluxing.trip.query.TripRecommendationCandidateRow;
import com.tongluxing.trip.support.RoutePolylineUtils;
import com.tongluxing.user.dto.UserQueryDTO;
import com.tongluxing.user.mapper.UserDomainMapper;
import com.tongluxing.vehicle.entity.VehicleProfile;
import com.tongluxing.vehicle.mapper.VehicleProfileMapper;
import com.tongluxing.growth.service.GrowthService;
import com.tongluxing.auth.entity.AuthAccount;
import com.tongluxing.auth.mapper.AuthAccountMapper;
import com.tongluxing.user.vo.BadgeWallVO;
import com.tongluxing.user.vo.GrowthSummaryVO;

import lombok.RequiredArgsConstructor;

/**
 * 匹配模块访问行程模块的适配器。
 *
 * <p>应用层负责把 trip-module 的实体数据转换为 match-module 的行程端口 DTO。</p>
 */
@Component
@RequiredArgsConstructor
public class MatchTripAdapter implements MatchTripPort {
    private final TripMapper tripMapper;
    private final TripRouteMapper routeMapper;
    private final ObjectMapper objectMapper;
    private final UserDomainMapper userMapper;
    private final VehicleProfileMapper vehicleMapper;
    private final GrowthService growthService;
    private final AuthAccountMapper authAccountMapper;
    private final RecommendationRouteProperties recommendationRouteProperties;

    /**
     * 按行程 ID 查询用于匹配计算的行程摘要。
     *
     * @param tripId 行程 ID
     * @return 匹配模块行程摘要；不存在时返回 null
     */
    @Override
    public MatchTripDTO getTrip(Long tripId) {
        Trip trip = tripMapper.findById(tripId);
        return trip == null ? null : toDTO(trip, new HashMap<>());
    }

    @Override
    public MatchTripDTO getTripByNumber(String tripNumber) {
        Trip trip = tripMapper.findByTripNumber(tripNumber);
        return trip == null ? null : toDTO(trip, new HashMap<>());
    }

    /**
     * 查询公开行程列表，作为匹配推荐候选池。
     *
     * @param limit 最大查询数量
     * @return 匹配模块行程摘要列表
     */
    @Override
    public List<MatchTripDTO> listPublicTrips(MatchCandidateQuery query) {
        int limit = Math.max(1, Math.min(query == null ? 1000 : query.limit(), 1000));
        MatchCandidateQuery effective = query == null
                ? new MatchCandidateQuery(null, null, null, null, null, null, limit)
                : query;
        return tripMapper.findMatchCandidates(effective.excludeUserId(), effective.ownerUserId(),
                        effective.departureFrom(), effective.departureTo(), effective.startCity(),
                        effective.destination(), limit).stream()
                .map(this::toCandidateDTO)
                .toList();
    }

    @Override
    public List<MatchRecommendationCandidateDTO> listRecommendationCandidates(MatchCandidateQuery query) {
        int limit = Math.max(1, Math.min(query == null ? 1000 : query.limit(), 1000));
        MatchCandidateQuery effective = query == null
                ? new MatchCandidateQuery(null, null, null, null, null, null, limit) : query;
        return tripMapper.findRecommendationCandidates(effective.excludeUserId(), effective.departureFrom(),
                        effective.departureTo(), limit).stream()
                .map(this::toRecommendationCandidateDTO).toList();
    }

    @Override
    public Map<Long, MatchTripDTO> getTripDetails(List<Long> tripIds) {
        if (tripIds == null || tripIds.isEmpty()) return Map.of();
        List<Long> ids = tripIds.stream().filter(java.util.Objects::nonNull).distinct().limit(30).toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, MatchTripDTO> result = new LinkedHashMap<>();
        tripMapper.findMatchCandidateDetailsByIds(ids).forEach(row -> result.put(row.getTripId(), toCandidateDTO(row)));
        return result;
    }

    @Override
    public Map<Long, String> getMatchPolylines(List<Long> tripIds) {
        if (tripIds == null || tripIds.isEmpty()) return Map.of();
        List<Long> ids = tripIds.stream().filter(java.util.Objects::nonNull).distinct().limit(1000).toList();
        if (ids.isEmpty()) return Map.of();

        Map<Long, String> result = new LinkedHashMap<>();
        routeMapper.findMatchPolylinesByTripIds(ids).forEach(route -> {
            if (route.getTripId() != null && route.getMatchPolyline() != null && !route.getMatchPolyline().isBlank()) {
                String normalized = RoutePolylineUtils.simplifyForMatching(objectMapper, route.getMatchPolyline(),
                        recommendationRouteProperties.getRdpEpsilon(), recommendationRouteProperties.getMaxPoints());
                result.put(route.getTripId(), normalized);
                // 兼容旧版最多 80 点数据；只更新派生字段，完整 polyline 保持不变。
                if (!normalized.equals(route.getMatchPolyline())) {
                    route.setMatchPolyline(normalized);
                    routeMapper.updateMatchPolyline(route);
                }
            }
        });

        // 历史路线升级采用懒回填：仅第一次匹配且 match_polyline 为空时读取完整路线，
        // RDP 压缩后立刻写回数据库；后续推荐只读 match_polyline。
        List<Long> missingIds = ids.stream().filter(id -> !result.containsKey(id)).toList();
        if (!missingIds.isEmpty()) {
            routeMapper.findFullPolylinesForMatchBackfill(missingIds).forEach(route -> {
                if (route.getTripId() == null || route.getPolyline() == null || route.getPolyline().isBlank()) return;
                String matchPolyline = RoutePolylineUtils.simplifyForMatching(objectMapper, route.getPolyline(),
                        recommendationRouteProperties.getRdpEpsilon(), recommendationRouteProperties.getMaxPoints());
                if (matchPolyline == null || matchPolyline.isBlank()) return;
                route.setMatchPolyline(matchPolyline);
                routeMapper.updateMatchPolyline(route);
                result.put(route.getTripId(), matchPolyline);
            });
        }
        return result;
    }

    @Override
    public MatchTripDTO findRecommendationReferenceTrip(Long userId) {
        // 推荐基准必须是用户自己发布的行程，不能使用加入他人队伍的行程替代。
        Trip trip = tripMapper.findRecommendationReferenceByUserId(userId);
        return trip == null ? null : toDTO(trip, new HashMap<>());
    }

    /**
     * 将行程实体转换为匹配模块所需的最小字段集合。
     *
     * @param trip 行程实体
     * @return 匹配模块行程 DTO
     */
    private MatchTripDTO toDTO(Trip trip, Map<Long, OwnerSnapshot> owners) {
        OwnerSnapshot owner = owners.computeIfAbsent(trip.getUserId(), userId -> new OwnerSnapshot(
                userMapper.findProfile(userId), growthService.getSummary(userId),
                growthService.getBadgeWall(userId), authAccountMapper.findByUserId(userId)));
        UserQueryDTO profile = owner.profile();
        VehicleProfile vehicle = trip.getVehicleId() == null
                ? null : vehicleMapper.findByIdAndUserId(trip.getVehicleId(), trip.getUserId());
        String vehicleSummary = vehicle == null ? "未公开车辆"
                : ((vehicle.getBrand() == null ? "" : vehicle.getBrand()) + " "
                        + (vehicle.getModel() == null ? "" : vehicle.getModel())).trim();
        GrowthSummaryVO growth = owner.growth();
        BadgeWallVO badges = owner.badges();
        AuthAccount account = owner.account();
        return new MatchTripDTO(trip.getId(), trip.getTripNumber(), trip.getUserId(), trip.getVehicleId(),
                profile == null || profile.getNickname() == null || profile.getNickname().isBlank()
                        ? "同路行车友" : profile.getNickname(),
                profile == null ? null : profile.getAvatarImageKey(),
                profile != null && "APPROVED".equals(profile.getCertificationStatus()),
                growth == null ? "LV1" : growth.levelCode(),
                profile == null ? 0 : profile.getTotalTripCount(),
                profile == null ? 0L : profile.getTotalDistanceMeters(),
                account == null || account.getLastLoginTime() == null ? null : account.getLastLoginTime().toString(),
                badges == null || badges.earned() == null ? 0 : badges.earned().size(),
                vehicle == null ? null : vehicle.getVehicleType(),
                vehicleSummary.isBlank() ? "已认证车辆" : vehicleSummary,
                trip.getVehicleRequirements(), trip.getBudgetDescription(),
                trip.getTitle(), trip.getDescription(),
                trip.getStartName(), trip.getEndName(),
                decimal(firstNonNull(trip.getStartLatitude(), trip.getStartLat())),
                decimal(firstNonNull(trip.getStartLongitude(), trip.getStartLng())),
                decimal(firstNonNull(trip.getEndLatitude(), trip.getEndLat())),
                decimal(firstNonNull(trip.getEndLongitude(), trip.getEndLng())),
                trip.getDepartureTime(), trip.getEstimatedDays(), trip.getRouteDistance(), trip.getRouteDuration(),
                null, trip.getWaypointsJson(), trip.getRemark(), trip.getTravelDepth(), trip.getExpectedPeople(),
                trip.getMaxVehicleCount(), trip.getJoinedVehicleCount(), trip.getStatus(), trip.getPublicFlag(),
                normalizeTripType(trip), normalizePublisherRole(trip), trip.getCaptainUserId());
    }


    /** 候选池已经由 TripMapper 一次聚合用户/成长/车辆摘要，这里只做无 SQL 的对象转换。 */
    private MatchTripDTO toCandidateDTO(TripMatchCandidateRow row) {
        String vehicleSummary = ((row.getVehicleBrand() == null ? "" : row.getVehicleBrand()) + " "
                + (row.getVehicleModel() == null ? "" : row.getVehicleModel())).trim();
        return new MatchTripDTO(row.getTripId(), row.getTripNumber(), row.getUserId(), row.getVehicleId(),
                row.getOwnerNickname() == null || row.getOwnerNickname().isBlank() ? "同路行车友" : row.getOwnerNickname(),
                row.getOwnerAvatarImageKey(), Boolean.TRUE.equals(row.getDriverVerified()),
                row.getOwnerLevelCode() == null ? "LV1" : row.getOwnerLevelCode(),
                row.getOwnerTotalTripCount() == null ? 0 : row.getOwnerTotalTripCount(),
                row.getOwnerTotalDistanceMeters() == null ? 0L : row.getOwnerTotalDistanceMeters(),
                row.getOwnerLastActiveAt() == null ? null : row.getOwnerLastActiveAt().toString(),
                row.getOwnerBadgeCount() == null ? 0 : row.getOwnerBadgeCount(),
                row.getVehicleType(), vehicleSummary.isBlank() ? "未公开车辆" : vehicleSummary,
                row.getVehicleRequirements(), row.getBudgetDescription(), row.getTitle(), row.getDescription(),
                row.getStartName(), row.getEndName(), decimal(row.getStartLatitude()), decimal(row.getStartLongitude()),
                decimal(row.getEndLatitude()), decimal(row.getEndLongitude()), row.getDepartureTime(), row.getEstimatedDays(),
                row.getRouteDistance(), row.getRouteDuration(), null, row.getWaypointsJson(), row.getRemark(),
                row.getTravelDepth(), row.getExpectedPeople(), row.getMaxVehicleCount(), row.getJoinedVehicleCount(),
                row.getStatus(), row.getPublicFlag(), normalizeTripType(row), normalizePublisherRole(row), row.getCaptainUserId());
    }

    private MatchRecommendationCandidateDTO toRecommendationCandidateDTO(TripRecommendationCandidateRow row) {
        return new MatchRecommendationCandidateDTO(row.getTripId(), row.getUserId(),
                decimal(row.getStartLatitude()), decimal(row.getStartLongitude()),
                decimal(row.getEndLatitude()), decimal(row.getEndLongitude()), row.getDepartureTime(),
                row.getEstimatedDays(), row.getRouteDistance(), row.getWaypointsJson(), row.getTravelDepth(),
                row.getMaxVehicleCount(), row.getJoinedVehicleCount(), row.getStatus());
    }

    private String normalizeTripType(TripMatchCandidateRow row) {
        if (row.getTripType() != null && !row.getTripType().isBlank()) return row.getTripType();
        return row.getVehicleId() == null ? "PASSENGER_DEMAND" : "DRIVER_TRIP";
    }

    private String normalizePublisherRole(TripMatchCandidateRow row) {
        if (row.getPublisherRole() != null && !row.getPublisherRole().isBlank()) return row.getPublisherRole();
        return "PASSENGER_DEMAND".equals(normalizeTripType(row)) ? "PASSENGER" : "DRIVER";
    }

    /** 兼容迁移前的历史数据：旧行程没有 P0 类型字段时按车辆信息推导。 */
    private String normalizeTripType(Trip trip) {
        if (trip.getTripType() != null && !trip.getTripType().isBlank()) {
            return trip.getTripType();
        }
        return trip.getVehicleId() == null ? "PASSENGER_DEMAND" : "DRIVER_TRIP";
    }

    /** 发布身份与行程类型保持一致，避免历史空值传到发现页。 */
    private String normalizePublisherRole(Trip trip) {
        if (trip.getPublisherRole() != null && !trip.getPublisherRole().isBlank()) {
            return trip.getPublisherRole();
        }
        return "PASSENGER_DEMAND".equals(normalizeTripType(trip)) ? "PASSENGER" : "DRIVER";
    }

    private Double decimal(java.math.BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    /** 优先使用标准位置字段，并兼容迁移前只写入 start_lat/end_lat 的历史数据。 */
    private java.math.BigDecimal firstNonNull(java.math.BigDecimal primary, java.math.BigDecimal fallback) {
        return primary == null ? fallback : primary;
    }

    private record OwnerSnapshot(UserQueryDTO profile, GrowthSummaryVO growth,
                                 BadgeWallVO badges, AuthAccount account) { }
}
