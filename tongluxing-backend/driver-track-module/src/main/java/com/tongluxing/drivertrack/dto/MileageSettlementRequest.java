package com.tongluxing.drivertrack.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 内部里程结算请求。
 */
public record MileageSettlementRequest(
        @NotNull Long tripId,
        @NotNull Long userId,
        @NotNull @Min(0) Integer distanceMeters
) {
}
