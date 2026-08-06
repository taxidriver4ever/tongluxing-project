package com.tongluxing.match.vo;

import java.util.List;

/**
 * 发现行程公开详情聚合，不返回聊天消息或成员敏感字段。
 *
 * <p>allowConsultation、allowApply 和 joinable 均由服务端根据当前用户关系、容量和
 * 行程状态派生，客户端只能据此展示按钮，不能把它们当作绕过服务端校验的凭据。</p>
 *
 * @param tripId 行程 ID
 * @param title 标题
 * @param status 状态
 * @param startName 起点
 * @param waypoints 途经点
 * @param endName 终点
 * @param departureTime 出发时间
 * @param estimatedDays 预计天数
 * @param description 公开介绍
 * @param startLatitude 起点纬度，仅用于详情地图定位
 * @param startLongitude 起点经度，仅用于详情地图定位
 * @param endLatitude 终点纬度，仅用于详情地图定位
 * @param endLongitude 终点经度，仅用于详情地图定位
 * @param routePolyline 兼容旧客户端的路线字段；公开详情固定返回空串，避免下发完整道路点
 * @param routeDistanceMeters 路线距离（米）
 * @param routeDurationSeconds 路线时长（秒）
 * @param joinedVehicleCount 已加入车辆数
 * @param maxVehicleCount 最大车辆数
 * @param memberCount 当前成员数
 * @param maxMemberCount 最大成员数
 * @param remainingSeats 剩余名额
 * @param matchScore 发现推荐分
 * @param vehicleRequirements 车辆要求列表
 * @param budgetDescription 费用预算说明
 * @param notes 行程备注
 * @param announcement 车队公告
 * @param joinRequirement 入队要求
 * @param tags 公开标签
 * @param owner 发起人公开摘要
 * @param members 公开成员摘要
 * @param relationshipStatus 当前用户与车队关系
 * @param ownerTrip 当前用户是否为发起人
 * @param allowConsultation 是否允许发起咨询
 * @param allowApply 是否允许提交申请
 * @param joinable 容量是否仍可加入
 * @param favorited 当前用户是否已收藏
 */
public record TripPublicDetailResponse(
        String tripId,
        String title,
        String status,
        String startName,
        List<String> waypoints,
        String endName,
        String departureTime,
        Integer estimatedDays,
        String description,
        Double startLatitude,
        Double startLongitude,
        Double endLatitude,
        Double endLongitude,
        String routePolyline,
        Integer routeDistanceMeters,
        Integer routeDurationSeconds,
        Integer joinedVehicleCount,
        Integer maxVehicleCount,
        Integer memberCount,
        Integer maxMemberCount,
        Integer remainingSeats,
        Integer matchScore,
        List<String> vehicleRequirements,
        String budgetDescription,
        String notes,
        String announcement,
        String joinRequirement,
        List<String> tags,
        TripDiscoverOwnerResponse owner,
        List<TripPublicMemberResponse> members,
        String relationshipStatus,
        Boolean ownerTrip,
        Boolean allowConsultation,
        Boolean allowApply,
        Boolean joinable,
        Boolean favorited,
        String tripType,
        String publisherRole,
        Boolean passengerDemand,
        Boolean hasCaptain
) {
}
