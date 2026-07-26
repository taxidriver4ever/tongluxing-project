package com.tongluxing.trip.service;

/** 群主完成行程编辑后发布，供聊天模块发送轻量通知。 */
public record TripUpdatedEvent(Long tripId, Long operatorUserId) {
}
