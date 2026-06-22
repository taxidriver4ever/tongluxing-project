package com.tongdao.trip.service;

import java.util.List;

import com.tongdao.user.model.UserModels.TeamMatchVO;
import com.tongdao.user.model.UserModels.TripDraftVO;

public interface TripDraftPublishPort {
    PublishOutcome publish(Long userId, TripDraftVO draft, String publishType, String bizId);
    List<TeamMatchVO> recommendTeams(Long userId, TripDraftVO draft, int page, int size);
    record PublishOutcome(Long tripId, Long publishedId) { }
}
