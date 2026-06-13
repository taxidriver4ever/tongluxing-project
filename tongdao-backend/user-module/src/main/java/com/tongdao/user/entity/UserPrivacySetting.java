package com.tongdao.user.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class UserPrivacySetting {

    private Long id;
    private Long userId;
    private Integer profileVisible;
    private Integer phoneVisible;
    private Integer tripVisible;
    private Integer locationVisible;
    private Integer allowTeamInvite;
    private Integer allowPrivateMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
