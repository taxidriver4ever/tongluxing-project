package com.tongluxing.user.dto.request;

import jakarta.validation.constraints.Pattern;

/**
 * 隐私设置增量更新请求。
 *
 * <p>所有字段均可为空：null 表示保持数据库旧值，false 表示明确关闭布尔能力。</p>
 *
 * @param profileVisibility 新的主页可见性
 * @param vehicleVisibility 新的车辆可见性
 * @param inviteEnabled 是否启用邀请能力
 * @param cityVisible 是否公开城市
 * @param bioVisible 是否公开简介
 * @param tripStatsVisible 是否公开行程统计
 * @param levelVisible 是否公开等级
 * @param locationEnabled 是否授权定位能力
 * @param notificationEnabled 是否允许通知
 */
public record UpdatePrivacySettingsRequest(
        @Pattern(regexp = "PUBLIC|PRIVATE") String profileVisibility,
        @Pattern(regexp = "PUBLIC|TEAM_ONLY|PRIVATE") String vehicleVisibility,
        Boolean inviteEnabled,
        Boolean cityVisible,
        Boolean bioVisible,
        Boolean tripStatsVisible,
        Boolean levelVisible,
        Boolean locationEnabled,
        Boolean notificationEnabled
) {
}

