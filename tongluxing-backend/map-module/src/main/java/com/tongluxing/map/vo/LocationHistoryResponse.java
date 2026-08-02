package com.tongluxing.map.vo;

import java.math.BigDecimal;

/** 当前用户的地点选择历史；只包含已通过 resolve 确认的地点。 */
public record LocationHistoryResponse(
        /** 历史记录 ID，仅用于删除当前用户该条记录。 */
        String historyId,
        /** 地点名称。 */
        String name,
        /** 地点详细地址。 */
        String address,
        /** 纬度。 */
        BigDecimal latitude,
        /** 经度。 */
        BigDecimal longitude,
        /** 距客户端当前坐标的球面直线距离，未传当前坐标时为空。 */
        Integer distanceMeters
) {
}
