package com.tongluxing.drivertrack.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 导航联调使用的偏航模拟请求。 */
public record MockDeviationRequest(
        @NotNull @Min(0) @Max(2) Integer deviationStatus
) {
}
