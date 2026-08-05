package com.tongluxing.trip.service;

import java.util.List;

/**
 * 行程发布事件。
 *
 * <p>乘客需求同样进入匹配池，但没有队长和车队；监听方必须通过
 * {@code tripType/captainUserId} 判断是否创建业务车队和腾讯 IM 群。</p>
 */
public record TripPublishedEvent(
        Long tripId,
        String tripName,
        Long ownerUserId,
        Long ownerVehicleId,
        Integer maxMemberCount,
        Boolean publicFlag,
        List<Long> memberUserIds,
        String tripType,
        Long captainUserId
) {
    /** 兼容未感知 P0 身份模型的旧调用。 */
    public TripPublishedEvent(Long tripId, String tripName, Long ownerUserId, Long ownerVehicleId,
                              Integer maxMemberCount, Boolean publicFlag, List<Long> memberUserIds) {
        this(tripId, tripName, ownerUserId, ownerVehicleId, maxMemberCount, publicFlag,
                memberUserIds, "DRIVER_TRIP", ownerUserId);
    }

    public boolean hasCaptain() {
        return "DRIVER_TRIP".equals(tripType) && captainUserId != null;
    }
}
