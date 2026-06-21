package com.tongdao.trip.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LocationRequest(
        @Size(max = 128) String name,
        @Size(max = 255) String address,
        @NotNull BigDecimal latitude,
        @NotNull BigDecimal longitude
) {
}
