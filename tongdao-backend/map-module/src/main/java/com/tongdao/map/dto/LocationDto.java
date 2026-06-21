package com.tongdao.map.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LocationDto(
        @Size(max = 128) String name,
        @Size(max = 255) String address,
        @NotNull BigDecimal latitude,
        @NotNull BigDecimal longitude
) {
}
