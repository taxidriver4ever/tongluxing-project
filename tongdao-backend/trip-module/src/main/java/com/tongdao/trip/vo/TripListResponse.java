package com.tongdao.trip.vo;

import java.util.List;

public record TripListResponse(
        List<TripResponse> trips
) {
}
