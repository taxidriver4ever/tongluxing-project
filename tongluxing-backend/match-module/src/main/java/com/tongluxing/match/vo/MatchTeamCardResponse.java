package com.tongluxing.match.vo;

/**
 * 推荐或附近车队列表使用的公开车队卡片。
 *
 * @param teamId 车队 ID
 * @param tripId 关联行程 ID
 * @param teamName 车队名称
 * @param startName 起点名称
 * @param endName 终点名称
 * @param departureTime 格式化后的出发时间
 * @param currentMemberCount 当前成员数
 * @param maxMemberCount 最大成员数
 * @param matchScore 车队匹配分
 * @param overlapRate 路线重合度近似值
 */
public record MatchTeamCardResponse(
        String teamId,
        String tripId,
        String teamName,
        String startName,
        String endName,
        String departureTime,
        Integer currentMemberCount,
        Integer maxMemberCount,
        Integer matchScore,
        Integer overlapRate
) {
}
