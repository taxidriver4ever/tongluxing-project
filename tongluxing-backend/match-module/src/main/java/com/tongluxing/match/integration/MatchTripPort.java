package com.tongluxing.match.integration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 匹配模块访问行程数据的跨模块端口。
 *
 * <p>只暴露匹配算法和公开发现页面需要的行程摘要，避免 match-module 依赖
 * trip-module 的数据库实体、草稿状态或内部写入规则。</p>
 */
public interface MatchTripPort {

    /**
     * 根据行程 ID 获取用于匹配计算的行程摘要。
     *
     * @param tripId 行程 ID
     * @return 行程不存在时返回 null
     */
    MatchTripDTO getTrip(Long tripId);

    /**
     * 按面向用户展示的公开行程号精确查询，供 TRIP_NUMBER 搜索模式使用。
     *
     * @param tripNumber 公开行程号
     * @return 精确匹配行程，不存在时为 null
     */
    MatchTripDTO getTripByNumber(String tripNumber);

    /**
     * 查询公开行程轻量候选池。完整 polyline 不随候选列表读取。
     */
    List<MatchTripDTO> listPublicTrips(MatchCandidateQuery query);

    /** 保留旧调用方式，默认只做公开状态过滤。 */
    default List<MatchTripDTO> listPublicTrips(int limit) {
        return listPublicTrips(new MatchCandidateQuery(null, null, null, null, null, null, limit));
    }

    /**
     * 顺路率精算阶段批量获取少量完整路线。
     */
    Map<Long, String> getRoutePolylines(List<Long> tripIds);

    /** 单条详情/精算按需读取完整路线。 */
    default String getRoutePolyline(Long tripId) {
        if (tripId == null) return null;
        return getRoutePolylines(List.of(tripId)).get(tripId);
    }

    /** SQL 可提前下推的候选条件。 */
    record MatchCandidateQuery(
            Long excludeUserId,
            Long ownerUserId,
            LocalDateTime departureFrom,
            LocalDateTime departureTo,
            String startCity,
            String destination,
            int limit
    ) { }

    /**
     * 查询当前用户可作为推荐基准的自有行程。
     *
     * <p>优先进行中的行程，其次选择最近待出发的公开行程。该方法只查询用户自己
     * 发布的行程，不把用户加入的其他队伍当作“我的行程”推荐基准。</p>
     *
     * @param userId 当前登录用户 ID
     * @return 没有可用自有行程时返回 null
     */
    MatchTripDTO findRecommendationReferenceTrip(Long userId);

    /**
     * 行程匹配所需的最小字段集合。
     *
     * <p>owner、vehicle 和 route 字段已经由端口实现完成跨表聚合；字段为空表示上游
     * 尚未配置，Service 必须使用安全默认值而不能假设全部存在。</p>
     *
     * @param tripId 行程主键
     * @param tripNumber 公开行程号
     * @param userId 发起人用户 ID
     * @param vehicleId 主车辆 ID
     * @param ownerNickname 发起人昵称
     * @param ownerAvatarImageKey 发起人头像 Key
     * @param driverVerified 发起人是否通过驾驶认证
     * @param ownerLevelCode 发起人等级编码
     * @param ownerTotalTripCount 发起人累计行程数
     * @param ownerTotalDistanceMeters 发起人累计里程（米）
     * @param ownerLastActiveAt 发起人最近活跃时间文本
     * @param ownerBadgeCount 发起人徽章数
     * @param vehicleType 车辆类型
     * @param vehicleSummary 车辆公开摘要
     * @param vehicleRequirements 对同行车辆的逗号分隔要求
     * @param budgetDescription 费用预算说明
     * @param title 行程标题
     * @param description 行程公开介绍
     * @param startName 起点名称
     * @param endName 终点名称
     * @param startLatitude 起点纬度
     * @param startLongitude 起点经度
     * @param endLatitude 终点纬度
     * @param endLongitude 终点经度
     * @param departureTime 计划出发时间
     * @param estimatedDays 预计天数
     * @param routeDistance 路线距离（米）
     * @param routeDuration 路线时长（秒）
     * @param routePolyline 路线折线编码
     * @param waypointsJson 途经点 JSON
     * @param remark 行程备注
     * @param travelDepth 旅行深度/节奏分类
     * @param expectedPeople 预计人数
     * @param maxVehicleCount 最大车辆数
     * @param joinedVehicleCount 已加入车辆数
     * @param status 行程状态
     * @param publicFlag 公开标志
     */
    record MatchTripDTO(
            Long tripId,
            String tripNumber,
            Long userId,
            Long vehicleId,
            String ownerNickname,
            String ownerAvatarImageKey,
            Boolean driverVerified,
            String ownerLevelCode,
            Integer ownerTotalTripCount,
            Long ownerTotalDistanceMeters,
            String ownerLastActiveAt,
            Integer ownerBadgeCount,
            String vehicleType,
            String vehicleSummary,
            String vehicleRequirements,
            String budgetDescription,
            String title,
            String description,
            String startName,
            String endName,
            Double startLatitude,
            Double startLongitude,
            Double endLatitude,
            Double endLongitude,
            LocalDateTime departureTime,
            Integer estimatedDays,
            Integer routeDistance,
            Integer routeDuration,
            String routePolyline,
            String waypointsJson,
            String remark,
            String travelDepth,
            Integer expectedPeople,
            Integer maxVehicleCount,
            Integer joinedVehicleCount,
            String status,
            Integer publicFlag,
            String tripType,
            String publisherRole,
            Long captainUserId
    ) {
    }
}
