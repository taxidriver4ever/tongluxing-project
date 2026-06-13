package com.tongdao.user.vo;

public record UserPrivacyResponse(
        Long userId,
        Boolean profileVisible,
        Boolean phoneVisible,
        Boolean tripVisible,
        Boolean locationVisible,
        Boolean allowTeamInvite,
        Boolean allowPrivateMessage
) {
}
