package com.tongluxing.user.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 关注/粉丝列表的公开用户摘要。 */
@Data
public class UserFollowQueryDTO {
    private Long userId;
    private String nickname;
    private String avatarImageKey;
    private String certificationStatus;
    private Integer totalTripCount;
    private Long totalDistanceMeters;
    private LocalDateTime followedAt;
}
