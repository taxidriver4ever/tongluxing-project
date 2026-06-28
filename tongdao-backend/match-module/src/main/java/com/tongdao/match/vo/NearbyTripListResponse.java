package com.tongdao.match.vo;

import java.util.List;

/**
 * NearbyTripListResponse 响应数据对象。
 */
public record NearbyTripListResponse(
        List<MatchTripCardResponse> trips
) {
}
