package com.tongdao.user.vo;

public record IdentityStatusResponse(
        Long userId,
        String realName,
        String idCardNoMask,
        String faceImageUrl,
        String status,
        String rejectReason
) {
}
