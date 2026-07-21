package com.tongluxing.trip.vo;

/** 当前用户是否已经存在一个不可并行的新行程。 */
public record ActiveTripStateResponse(
        boolean active,
        String tripId,
        String message
) {
}
