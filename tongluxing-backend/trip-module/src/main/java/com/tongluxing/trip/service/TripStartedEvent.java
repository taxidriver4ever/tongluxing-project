package com.tongluxing.trip.service;

import java.util.List;

/** 行程开始后通知聊天模块创建并初始化行程群聊。 */
public record TripStartedEvent(
        Long tripId,
        String tripName,
        Long ownerUserId,
        List<Long> memberUserIds
) {
}
