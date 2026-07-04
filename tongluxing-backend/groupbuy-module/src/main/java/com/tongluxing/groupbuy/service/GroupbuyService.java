package com.tongluxing.groupbuy.service;

import com.tongluxing.groupbuy.dto.CreateGroupbuyRequest;
import com.tongluxing.groupbuy.dto.PaidParticipantRequest;
import com.tongluxing.groupbuy.vo.GroupbuyActivityVO;
import com.tongluxing.groupbuy.vo.PageResult;
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

