package com.tongluxing.admin.vo;

/** 后台轨迹审核中的行程成员与有效轨迹统计。 */
public record AdminTripTrackMemberVO(
        Long userId,
        String memberRole,
        String joinStatus,
        String nickname,
        Integer distanceMeters,
        Integer totalPointCount,
        Integer validPointCount
) {
}
