package com.tongdao.user.vo;

import java.util.List;

public record EmergencyContactListResponse(
        List<EmergencyContactResponse> contacts
) {
}
