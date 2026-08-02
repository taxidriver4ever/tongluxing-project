package com.tongluxing.match.vo;

/**
 * 预计算推荐或附近查询使用的目标行程卡片。
 *
 * @param matchId 推荐结果 ID；附近列表没有预计算结果时为空
 * @param tripId 目标行程 ID
 * @param userId 目标行程发起人 ID
 * @param title 行程标题
 * @param startName 起点
 * @param endName 终点
 * @param departureTime 格式化出发时间
 * @param travelDepth 旅行深度/节奏分类
 * @param status 行程状态
 * @param matchScore 综合匹配分 0~100
 * @param overlapRate 路线重合度近似值 0~100
 * @param departureGapMinutes 与源行程出发时间差（分钟）
 * @param distanceGapMeters 起终点综合偏差（米）
 * @param expectedPeople 预计同行人数
 * @param teamId 目标车队 ID，尚未建队时为空
 * @param currentMemberCount 当前成员数
 * @param maxMemberCount 最大成员数
 * @param joinable 是否存在可申请的活跃车队
 */
public record MatchTripCardResponse(
        String matchId,
        String tripId,
        String userId,
        String title,
        String startName,
        String endName,
        String departureTime,
        String travelDepth,
        String status,
        Integer matchScore,
        Integer overlapRate,
        Integer departureGapMinutes,
        Integer distanceGapMeters,
        Integer expectedPeople,
        String teamId,
        Integer currentMemberCount,
        Integer maxMemberCount,
        Boolean joinable
) {
}
