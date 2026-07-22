package com.tongluxing.trip.vo;

/** 行程预计时间冲突检查结果；冲突仅提醒，不强制禁止发布。 */
public record TripTimeConflictResponse(
        boolean conflict,
        String conflictTripId,
        String conflictTitle,
        String conflictStartTime,
        String conflictEndTime,
        String message
) {
}
