package com.tongluxing.user.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 行程草稿返回对象。
 *
 * <p>publishedTripId 仅在草稿已经发布时有值；updatedAt 用于草稿列表排序和
 * 多端编辑冲突提示。</p>
 *
 * @param draftId 草稿主键
 * @param startLocation 起点
 * @param endLocation 终点
 * @param waypoints 途经点列表
 * @param departureTime 计划出发时间
 * @param durationDays 预计天数
 * @param peopleCount 出行人数
 * @param remark 备注
 * @param draftStatus 草稿状态
 * @param publishedTripId 发布后生成的行程 ID
 * @param updatedAt 最后更新时间
 */
public record TripDraftVO(
        Long draftId, LocationVO startLocation, LocationVO endLocation, List<LocationVO> waypoints,
        LocalDateTime departureTime, Integer durationDays, Integer peopleCount, String remark,
        String draftStatus, Long publishedTripId, LocalDateTime updatedAt
) {
}

