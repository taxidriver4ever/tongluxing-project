package com.tongdao.map.vo;

import java.util.List;

public record NearbyMapResponse(
        List<MapMarkerResponse> markers
) {
}
