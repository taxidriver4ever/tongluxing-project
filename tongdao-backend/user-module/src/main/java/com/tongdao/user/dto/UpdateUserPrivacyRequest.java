package com.tongdao.user.dto;

public record UpdateUserPrivacyRequest(
        Boolean profileVisible,
        Boolean phoneVisible,
        Boolean tripVisible,
        Boolean locationVisible,
        Boolean allowTeamInvite,
        Boolean allowPrivateMessage
) {
}
