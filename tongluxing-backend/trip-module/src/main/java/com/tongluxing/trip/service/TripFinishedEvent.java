package com.tongluxing.trip.service;

/** 行程结束后通知聊天模块归档行程群聊。 */
public record TripFinishedEvent(Long tripId) {
}
