package com.tongluxing.merchant.vo;

import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** 合作商申请及审核状态。 */
public record MerchantPartnerApplicationVO(
        @JsonSerialize(using = ToStringSerializer.class) Long merchantId,
        String merchantName, String category,
        String applicationReason, String cooperationCategories, Integer plannedMonthlyStock,
        String applicationStatus, String rejectReason, Long reviewerId,
        LocalDateTime submittedAt, LocalDateTime reviewedAt,
        String cancellationStatus, String cancellationReason, String cancellationRejectReason,
        Long cancellationReviewerId, LocalDateTime cancellationRequestedAt,
        LocalDateTime cancellationReviewedAt
) {
}
