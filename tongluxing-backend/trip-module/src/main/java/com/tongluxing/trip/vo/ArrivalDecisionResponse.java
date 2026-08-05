package com.tongluxing.trip.vo;

/** 到达终点后的开放式结束状态。 */
public record ArrivalDecisionResponse(
        String tripId,
        String arrivalStatus,
        String enteredAt,
        String decisionDeadline,
        Boolean canEnd,
        Boolean canContinue
) {
}
