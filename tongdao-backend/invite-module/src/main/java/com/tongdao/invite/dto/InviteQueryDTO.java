package com.tongdao.invite.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class InviteQueryDTO {
    private Long id;
    private Long userId;
    private String inviteCode;
    private Boolean enabledFlag;
    private Long relationId;
    private Long inviterUserId;
    private Long inviteeUserId;
    private String status;
    private LocalDateTime boundAt;
    private LocalDateTime firstTeamCompletedAt;
}
