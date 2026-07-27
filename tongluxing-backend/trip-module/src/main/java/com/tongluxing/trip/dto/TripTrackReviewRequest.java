package com.tongluxing.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 管理员轨迹结算审核请求。 */
public record TripTrackReviewRequest(
        @NotBlank(message = "审核决定不能为空")
        @Pattern(
                regexp = "APPROVE_SYSTEM_DISTANCE|APPROVE_MODIFIED_DISTANCE|REJECT_GROWTH|MARK_FALSE_POSITIVE",
                message = "不支持的审核决定")
        String decision,
        @PositiveOrZero(message = "审核里程不能为负数")
        Integer approvedDistanceMeters,
        @NotBlank(message = "审核原因不能为空")
        @Size(max = 255, message = "审核原因不能超过255个字符")
        String reason,
        @NotBlank(message = "requestId不能为空")
        @Size(max = 64, message = "requestId不能超过64个字符")
        String requestId
) {
}
