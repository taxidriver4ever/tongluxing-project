package com.tongluxing.groupbuy.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 拼团活动响应视图。
 *
 * @param activityId 活动 ID
 * @param merchantId 商家 ID
 * @param productId 商品 ID
 * @param initiatorUserId 发起用户 ID
 * @param targetPeople 成团人数
 * @param currentPeople 当前已支付参团人数
 * @param groupPrice 拼团价格
 * @param activityStatus 活动状态：ONGOING、SUCCESS、FAILED、OFFLINE
 * @param startAt 活动开始时间
 * @param expireAt 活动过期时间
 * @param successAt 成团成功时间
 * @param failedAt 失败或下线时间
 * @param participants 已支付参与人列表
 */
public record GroupbuyActivityVO(
        Long activityId,
        Long merchantId,
        Long productId,
        Long couponId,
        String couponName,
        String merchantName,
        Long storeId,
        String storeName,
        String storeAddress,
        BigDecimal originalPrice,
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

