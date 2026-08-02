package com.tongluxing.match.vo;

import java.util.List;

/**
 * 未入队用户可查看的搜索行程详情，不包含群聊消息和成员隐私。
 *
 * @param tripId 行程 ID
 * @param ownerUserId 发起人 ID
 * @param ownerNickname 发起人昵称
 * @param ownerAvatarImageKey 发起人头像 Key
 * @param title 标题
 * @param description 公开介绍
 * @param startName 起点
 * @param endName 终点
 * @param waypoints 途经点名称
 * @param departureTime 出发时间
 * @param estimatedEndTime 预计结束时间
 * @param routeDistanceMeters 路线距离（米）
 * @param routeDurationSeconds 路线时长（秒）
 * @param teamId 关联车队 ID
 * @param teamName 车队名称
 * @param announcement 公告
 * @param vehicleSummary 车辆公开摘要
 * @param currentMemberCount 当前成员数
 * @param maxMemberCount 最大成员数
 * @param remainingSeats 剩余名额
 * @param joinRequirement 入队要求
 * @param status 行程状态
 * @param joinable 是否仍可申请
 */
public record TripSearchDetailResponse(
        String tripId,
        String ownerUserId,
        String ownerNickname,
        String ownerAvatarImageKey,
        String title,
        String description,
        String startName,
        String endName,
        List<String> waypoints,
        String departureTime,
        String estimatedEndTime,
        Integer routeDistanceMeters,
        Integer routeDurationSeconds,
        String teamId,
        String teamName,
        String announcement,
        String vehicleSummary,
        Integer currentMemberCount,
        Integer maxMemberCount,
        Integer remainingSeats,
        String joinRequirement,
        String status,
        Boolean joinable
) {
}
