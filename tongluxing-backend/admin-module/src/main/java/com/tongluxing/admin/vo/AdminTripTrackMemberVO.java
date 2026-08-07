package com.tongluxing.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台轨迹审核中的行程成员信息。
 *
 * <p>只有队长有轨迹与里程统计；普通成员只展示最新位置快照，
 * 用于核对是否长期失联或明显远离队伍。</p>
 */
public record AdminTripTrackMemberVO(
        Long userId,
        String memberRole,
        String joinStatus,
        String nickname,
        Integer distanceMeters,
        Integer totalPointCount,
        Integer validPointCount,
        BigDecimal latestLongitude,
        BigDecimal latestLatitude,
        Integer latestAccuracyMeters,
        Integer mockLocation,
        LocalDateTime latestLocationTime
) {
}
