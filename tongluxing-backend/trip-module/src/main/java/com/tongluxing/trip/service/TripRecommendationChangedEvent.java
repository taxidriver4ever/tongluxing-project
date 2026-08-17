package com.tongluxing.trip.service;

/** 会明显改变推荐候选或基准行程的核心变更。 */
public record TripRecommendationChangedEvent(Long tripId, Long userId, String reason) { }
