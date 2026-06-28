package com.tongdao.groupbuy.service;

import com.tongdao.groupbuy.dto.CreateGroupbuyRequest;
import com.tongdao.groupbuy.dto.PaidParticipantRequest;
import com.tongdao.groupbuy.vo.GroupbuyActivityVO;
import com.tongdao.groupbuy.vo.PageResult;
/**
 * GroupbuyService 业务服务接口。
 */
public interface GroupbuyService {
    GroupbuyActivityVO create(CreateGroupbuyRequest request);

    PageResult<GroupbuyActivityVO> list(String status, int page, int size);

    GroupbuyActivityVO detail(Long activityId);

    GroupbuyActivityVO addPaidParticipant(Long activityId, PaidParticipantRequest request);

    GroupbuyActivityVO expire(Long activityId);
}

