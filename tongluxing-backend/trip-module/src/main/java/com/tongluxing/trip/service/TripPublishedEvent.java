package com.tongluxing.trip.service;

import java.util.List;

/**
 * 行程发布事件。匹配模块监听该事件预先生成“发现同行”推荐结果。
 */
public record TripPublishedEvent(
        Long tripId,
        String tripName,
        Long ownerUserId,
        Long ownerVehicleId,
        Integer maxMemberCount,
        Boolean publicFlag,
        List<Long> memberUserIds
) {
}
