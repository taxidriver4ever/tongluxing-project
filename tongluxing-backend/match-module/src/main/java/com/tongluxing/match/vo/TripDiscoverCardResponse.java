package com.tongluxing.match.vo;

import java.util.List;

/**
 * 公共发现信息流中的行程卡片。
 *
 * @param tripId 行程 ID
 * @param title 标题
 * @param status 行程状态
 * @param startName 起点
 * @param waypoints 途经点名称
 * @param endName 终点
 * @param departureTime 格式化出发时间
 * @param estimatedDays 预计持续天数
 * @param description 公开介绍
 * @param joinedVehicleCount 已加入车辆数
 * @param maxVehicleCount 最大车辆数
 * @param memberCount 当前成员数
 * @param maxMemberCount 最大成员数
 * @param remainingSeats 剩余名额
 * @param matchScore 当前发现上下文中的推荐分
 * @param distanceMeters 用户位置到行程起点距离；未提供定位时为空
 * @param tags 驾驶认证、车辆要求等公开标签
 * @param coverImageKey 封面资源 Key
 * @param relationshipStatus 当前用户与车队的关系
 * @param owner 发起人公开摘要
 */
public record TripDiscoverCardResponse(
        String tripId,
        String title,
        String status,
        String startName,
        List<String> waypoints,
        String endName,
        String departureTime,
        Integer estimatedDays,
        String description,
        Integer joinedVehicleCount,
        Integer maxVehicleCount,
        Integer memberCount,
        Integer maxMemberCount,
        Integer remainingSeats,
        Integer matchScore,
        Integer distanceMeters,
        List<String> tags,
        String coverImageKey,
        String relationshipStatus,
        TripDiscoverOwnerResponse owner
) {
}
