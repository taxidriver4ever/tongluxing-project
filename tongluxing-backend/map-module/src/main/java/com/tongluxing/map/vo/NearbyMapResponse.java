package com.tongluxing.map.vo;

import java.util.List;

/**
 * 附近地图点位响应。
 */
public record NearbyMapResponse(
        /** 附近标记点列表。 */
        List<MapMarkerResponse> markers
) {
}
