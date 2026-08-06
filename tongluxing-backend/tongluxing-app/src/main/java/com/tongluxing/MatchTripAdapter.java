package com.tongluxing;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tongluxing.match.integration.MatchTripPort;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.mapper.TripMapper;
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
    private final UserDomainMapper userMapper;
    private final VehicleProfileMapper vehicleMapper;
    private final GrowthService growthService;
    private final AuthAccountMapper authAccountMapper;

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
    public List<MatchTripDTO> listPublicTrips(int limit) {
        Map<Long, OwnerSnapshot> owners = new HashMap<>();
        return tripMapper.findPublicTrips(null, limit).stream()
                .map(trip -> toDTO(trip, owners))
                .toList();
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
                trip.getRoutePolyline(), trip.getWaypointsJson(), trip.getRemark(), trip.getTravelDepth(), trip.getExpectedPeople(),
                trip.getMaxVehicleCount(), trip.getJoinedVehicleCount(), trip.getStatus(), trip.getPublicFlag(),
                normalizeTripType(trip), normalizePublisherRole(trip), trip.getCaptainUserId());
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
