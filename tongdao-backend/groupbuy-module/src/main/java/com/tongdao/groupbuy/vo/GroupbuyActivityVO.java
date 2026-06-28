package com.tongdao.groupbuy.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
/**
 * GroupbuyActivityVO 视图响应对象。
 */

public record GroupbuyActivityVO(
        Long activityId,
        Long merchantId,
        Long productId,
        Long initiatorUserId,
        Integer targetPeople,
        Integer currentPeople,
        BigDecimal groupPrice,
        String activityStatus,
        LocalDateTime startAt,
        LocalDateTime expireAt,
        LocalDateTime successAt,
        LocalDateTime failedAt,
        List<GroupbuyParticipantVO> participants
) {
}

