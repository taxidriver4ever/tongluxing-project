package com.tongdao.user.vo;

public record EmergencyContactResponse(
        Long contactId,
        String contactName,
        String relation,
        String phoneMask,
        Boolean isDefault
) {
}
