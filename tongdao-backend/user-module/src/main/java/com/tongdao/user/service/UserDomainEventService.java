package com.tongdao.user.service;

public interface UserDomainEventService {

    void handleTeamTripCompleted(Long tripId, Long userId, String bizId);
}
