package com.tongluxing;

import java.util.List;

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
        return trip == null ? null : toDTO(trip);
    }

    /**
     * 查询公开行程列表，作为匹配推荐候选池。
     *
     * @param limit 最大查询数量
     * @return 匹配模块行程摘要列表
     */
    @Override
    public List<MatchTripDTO> listPublicTrips(int limit) {
        return tripMapper.findPublicTrips(limit).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * 将行程实体转换为匹配模块所需的最小字段集合。
     *
     * @param trip 行程实体
     * @return 匹配模块行程 DTO
     */
    private MatchTripDTO toDTO(Trip trip) {
        UserQueryDTO profile = userMapper.findProfile(trip.getUserId());
        VehicleProfile vehicle = trip.getVehicleId() == null
                ? null : vehicleMapper.findByIdAndUserId(trip.getVehicleId(), trip.getUserId());
        String vehicleSummary = vehicle == null ? "未公开车辆"
                : ((vehicle.getBrand() == null ? "" : vehicle.getBrand()) + " "
                        + (vehicle.getModel() == null ? "" : vehicle.getModel())).trim();
        var growth = growthService.getSummary(trip.getUserId());
        var badges = growthService.getBadgeWall(trip.getUserId());
        AuthAccount account = authAccountMapper.findByUserId(trip.getUserId());
        return new MatchTripDTO(trip.getId(), trip.getUserId(), trip.getVehicleId(),
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
                trip.getTitle(), trip.getDescription(), trip.getCoverImageKey(),
                trip.getStartName(), trip.getEndName(),
                decimal(trip.getStartLat()), decimal(trip.getStartLng()), decimal(trip.getEndLat()), decimal(trip.getEndLng()),
                trip.getDepartureTime(), trip.getEstimatedDays(), trip.getRouteDistance(), trip.getRouteDuration(),
                trip.getRoutePolyline(), trip.getWaypointsJson(), trip.getRemark(), trip.getTravelDepth(), trip.getExpectedPeople(),
                trip.getMaxVehicleCount(), trip.getJoinedVehicleCount(), trip.getStatus(), trip.getPublicFlag());
    }

    private Double decimal(java.math.BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
