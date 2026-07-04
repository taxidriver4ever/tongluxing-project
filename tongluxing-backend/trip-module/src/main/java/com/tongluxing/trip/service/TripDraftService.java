package com.tongluxing.trip.service;

import com.tongluxing.user.model.UserModels.PageResult;
import com.tongluxing.user.model.UserModels.PublishResultVO;
import com.tongluxing.user.model.UserModels.TeamMatchVO;
import com.tongluxing.user.model.UserModels.TripDraftRequest;
import com.tongluxing.user.model.UserModels.TripDraftVO;

/**
 * 行程草稿业务服务接口。
 */
public interface TripDraftService {

    /**
     * 创建行程草稿。
     */
    TripDraftVO create(TripDraftRequest request);

    /**
     * 分页查询行程草稿。
     */
    PageResult<TripDraftVO> list(String status, int page, int size);

    /**
     * 更新行程草稿。
     */
    TripDraftVO update(Long id, TripDraftRequest request);

    /**
     * 删除行程草稿。
     */
    void delete(Long id);

    /**
     * 发布草稿为行程或车队。
     */
    PublishResultVO publish(Long id, String publishType);

    /**
     * 查询草稿推荐车队。
     */
    PageResult<TeamMatchVO> recommendTeams(Long id, int page, int size);
}
