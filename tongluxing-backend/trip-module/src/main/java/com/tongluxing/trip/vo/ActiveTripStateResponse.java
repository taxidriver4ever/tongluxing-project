package com.tongluxing.trip.vo;

/** 当前用户是否拥有或参加一个进行中的行程。 */
public record ActiveTripStateResponse(
        boolean active,
        String tripId,
        String message
) {
}
