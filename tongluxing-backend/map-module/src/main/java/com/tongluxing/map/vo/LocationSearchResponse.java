package com.tongluxing.map.vo;

import java.math.BigDecimal;

/** 高德 POI 地点搜索建议。 */
public record LocationSearchResponse(
        /** POI 名称。 */
        String name,
        /** POI 地址。 */
        String address,
        /** POI 纬度。 */
        BigDecimal latitude,
        /** POI 经度。 */
        BigDecimal longitude,
        /** 距当前位置的直线距离，单位米；无定位参数时为空。 */
        Integer distanceMeters
) {
}
