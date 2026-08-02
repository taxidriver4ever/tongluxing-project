package com.tongluxing.trip.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 草稿经停点新增或修改请求。 */
public record TripWaypointCommand(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 255) String address,
        @NotNull BigDecimal longitude,
        @NotNull BigDecimal latitude,
        @NotBlank @Pattern(regexp = "MEETING|REST|HOTEL|FUEL|CHARGING|CHECK_IN|NORMAL") String type,
        @Min(1) @Max(20) Integer sort,
        @Min(0) @Max(1440) Integer stayMinutes,
        @Size(max = 255) String remark
) { }
