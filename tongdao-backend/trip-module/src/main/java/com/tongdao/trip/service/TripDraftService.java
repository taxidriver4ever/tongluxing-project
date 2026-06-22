package com.tongdao.trip.service;

import com.tongdao.user.model.UserModels.PageResult;
import com.tongdao.user.model.UserModels.PublishResultVO;
import com.tongdao.user.model.UserModels.TeamMatchVO;
import com.tongdao.user.model.UserModels.TripDraftRequest;
import com.tongdao.user.model.UserModels.TripDraftVO;

public interface TripDraftService {
    TripDraftVO create(TripDraftRequest request);
    PageResult<TripDraftVO> list(String status, int page, int size);
    TripDraftVO update(Long id, TripDraftRequest request);
    void delete(Long id);
    PublishResultVO publish(Long id, String publishType);
    PageResult<TeamMatchVO> recommendTeams(Long id, int page, int size);
}
