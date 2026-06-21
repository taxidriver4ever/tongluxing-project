package com.tongdao.map.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoutePlanRequest(
        @Valid @NotNull LocationDto startLocation,
        @Valid @NotNull LocationDto endLocation,
        @Valid @Size(max = 5) List<LocationDto> waypoints
) {
}
