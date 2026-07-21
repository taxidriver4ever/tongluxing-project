package com.tongluxing.match.vo;

/** 发现同行入队申请结果。 */
public record MatchApplyResponse(String matchId, String tripId, String teamId,
                                 String applicationId, String status) {
}
