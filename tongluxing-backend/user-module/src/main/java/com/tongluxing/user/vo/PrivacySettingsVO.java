package com.tongluxing.user.vo;

/**
 * 当前用户可编辑的公开资料、定位与通知权限。
 *
 * <p>profileVisibility 控制整个公开主页；cityVisible、bioVisible 和
 * tripStatsVisible 是主页允许公开后的细粒度字段开关。</p>
 *
 * @param profileVisibility 主页可见性：PUBLIC 或 PRIVATE
 * @param vehicleVisibility 车辆可见性：PUBLIC、TEAM_ONLY 或 PRIVATE
 * @param inviteEnabled 是否启用邀请能力
 * @param cityVisible 是否公开城市
 * @param bioVisible 是否公开简介
 * @param tripStatsVisible 是否公开行程统计
 * @param levelVisible 是否公开等级
 * @param locationEnabled 是否授权定位能力
 * @param notificationEnabled 是否允许通知
 */
public record PrivacySettingsVO(
        String profileVisibility,
        String vehicleVisibility,
        Boolean inviteEnabled,
        Boolean cityVisible,
        Boolean bioVisible,
        Boolean tripStatsVisible,
        Boolean levelVisible,
        Boolean locationEnabled,
        Boolean notificationEnabled
) {
}

