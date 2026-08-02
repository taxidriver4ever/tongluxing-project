package com.tongluxing.match.vo;

/**
 * 发现同行入队申请结果。
 *
 * @param matchId 触发申请的推荐结果 ID；搜索或附近申请没有推荐记录时为空
 * @param tripId 目标行程 ID
 * @param teamId 目标车队 ID
 * @param applicationId team-module 创建的申请 ID
 * @param status 初始申请状态，当前为 PENDING
 */
public record MatchApplyResponse(String matchId, String tripId, String teamId,
                                 String applicationId, String status) {
}
