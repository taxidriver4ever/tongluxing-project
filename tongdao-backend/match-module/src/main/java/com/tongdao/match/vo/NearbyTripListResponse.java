package com.tongdao.match.vo;

import java.util.List;

public record NearbyTripListResponse(
        List<MatchTripCardResponse> trips
) {
}
