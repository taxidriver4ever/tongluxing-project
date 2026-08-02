package com.tongluxing.user.vo;

/**
 * 用户公开主页资料返回对象。
 *
 * <p>只包含允许公开展示的基础信息。用户关闭某一细粒度开关后，相应字符串返回
 * 空串、统计数值返回 0，使响应结构保持稳定。</p>
 *
 * @param userId 平台用户 ID
 * @param tongluxingId 同路行号
 * @param nickname 昵称
 * @param avatarImageKey 头像资源 Key
 * @param cityName 允许公开时的城市名称
 * @param bio 允许公开时的个人简介
 * @param drivingLicenseCertificationStatus 最新认证状态
 * @param totalTripCount 允许公开时的累计行程数
 * @param totalDistanceMeters 允许公开时的累计距离（米）
 * @param totalDurationMinutes 允许公开时的累计时长（分钟）
 * @param completedWaypointCount 允许公开时的累计途经点数
 */
public record PublicProfileVO(
        Long userId, String tongluxingId, String nickname, String avatarImageKey, String cityName, String bio,
        String drivingLicenseCertificationStatus, Integer totalTripCount, Long totalDistanceMeters,
        Long totalDurationMinutes, Integer completedWaypointCount
) {
    public PublicProfileVO(Long userId, String nickname, String avatarImageKey, String cityName, String bio,
                           String drivingLicenseCertificationStatus) {
        // 兼容只提供基础名片的旧调用方，统计字段使用安全的零值。
        this(userId, null, nickname, avatarImageKey, cityName, bio,
                drivingLicenseCertificationStatus, 0, 0L, 0L, 0);
    }

    /**
     * 兼容尚未传递同路行号、但需要携带行程统计的内部调用。
     */
    public PublicProfileVO(Long userId, String nickname, String avatarImageKey, String cityName, String bio,
                           String drivingLicenseCertificationStatus, Integer totalTripCount,
                           Long totalDistanceMeters, Long totalDurationMinutes,
                           Integer completedWaypointCount) {
        this(userId, null, nickname, avatarImageKey, cityName, bio,
                drivingLicenseCertificationStatus, totalTripCount, totalDistanceMeters,
                totalDurationMinutes, completedWaypointCount);
    }
}

