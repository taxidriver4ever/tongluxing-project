package com.tongdao.groupbuy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateGroupbuyRequest(
        @NotNull Long productId,
        @NotNull @Min(1) Integer targetPeople,
        @NotNull @Min(1) Integer validHours,
        @NotBlank String requestId
) {
}

