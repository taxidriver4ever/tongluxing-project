package com.tongluxing.match.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 用户申请加入搜索到的行程。 */
public record TripApplicationRequest(
        @Size(max = 255) String message,
        Boolean selfDrive,
        Long applicantVehicleId,
        @Min(1) @Max(8) Integer companionCount
) {
}
