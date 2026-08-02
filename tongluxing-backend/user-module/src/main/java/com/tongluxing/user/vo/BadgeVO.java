package com.tongluxing.user.vo;

import java.time.LocalDateTime;

/**
 * 单个徽章展示对象。
 *
 * <p>currentValue 与 threshold 描述当前完成进度；awardedAt 非空表示已经获得。</p>
 *
 * @param badgeId 徽章主键
 * @param badgeCode 稳定业务编码
 * @param badgeName 展示名称
 * @param badgeImageKey 图片资源 Key
 * @param conditionDescription 获得条件说明
 * @param eventType 驱动进度的事件类型
 * @param currentValue 当前进度
 * @param threshold 达成阈值
 * @param awardedAt 获得时间
 */
public record BadgeVO(
        Long badgeId, String badgeCode, String badgeName, String badgeImageKey,
        String conditionDescription, String eventType, Integer currentValue, Integer threshold,
        LocalDateTime awardedAt
) {
}

