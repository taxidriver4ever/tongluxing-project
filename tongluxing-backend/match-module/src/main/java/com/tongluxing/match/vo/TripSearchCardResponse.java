package com.tongluxing.match.vo;

import java.util.List;

/**
 * 高级搜索结果中的公开行程卡片。
 *
 * @param tripId 行程 ID
 * @param ownerUserId 发起人 ID
 * @param ownerNickname 发起人昵称
 * @param ownerAvatarImageKey 发起人头像 Key
 * @param title 行程标题
 * @param startName 起点
 * @param endName 终点
 * @param waypoints 途经点名称
 * @param departureTime 格式化出发时间
 * @param estimatedEndTime 由路线时长或预计天数推算的结束时间
 * @param vehicleSummary 车辆公开摘要
 * @param currentMemberCount 当前成员数
 * @param maxMemberCount 最大成员数
 * @param remainingSeats 剩余名额
 * @param matchScore 针对本次搜索条件计算的分数
 * @param startDistanceMeters 搜索起点与行程起点距离（米）
 * @param endDistanceMeters 搜索终点与行程终点距离（米）
 * @param status 行程状态
 * @param joinable 是否仍有名额
 */
public record TripSearchCardResponse(
        String tripId,
        String ownerUserId,
        String ownerNickname,
        String ownerAvatarImageKey,
        String title,
        String startName,
        String endName,
        List<String> waypoints,
        String departureTime,
        String estimatedEndTime,
        String vehicleSummary,
        Integer currentMemberCount,
        Integer maxMemberCount,
        Integer remainingSeats,
        Integer matchScore,
        Integer startDistanceMeters,
        Integer endDistanceMeters,
        String status,
        Boolean joinable
) {
}
