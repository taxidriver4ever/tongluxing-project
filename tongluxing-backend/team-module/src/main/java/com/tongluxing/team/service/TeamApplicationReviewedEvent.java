package com.tongluxing.team.service;

/** 入队申请审核事件，用于推荐转化漏斗与后续通知。 */
public record TeamApplicationReviewedEvent(Long teamId, Long targetTripId, Long applicantUserId, String status) { }
