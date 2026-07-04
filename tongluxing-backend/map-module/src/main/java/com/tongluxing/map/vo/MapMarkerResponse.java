package com.tongluxing.map.vo;

import java.math.BigDecimal;

/**
 * 地图标记点响应。
 */
public record MapMarkerResponse(
        /** 标记点 ID。 */
        String markerId,
        /** 标记点类型，例如 CURRENT。 */
        String markerType,
        /** 标题。 */
        String title,
        /** 副标题或地址描述。 */
        String subtitle,
        /** 纬度。 */
        BigDecimal latitude,
        /** 经度。 */
        BigDecimal longitude
) {
}
