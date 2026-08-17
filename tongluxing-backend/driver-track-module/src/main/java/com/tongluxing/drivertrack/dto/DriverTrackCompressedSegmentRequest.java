package com.tongluxing.drivertrack.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 两个保留点之间被客户端主动省略的原始 sequence 范围。 */
public record DriverTrackCompressedSegmentRequest(
        @NotNull @Min(1) Long fromSequenceNo,
        @NotNull @Min(1) Long toSequenceNo,
        @NotNull @Pattern(regexp = "DEGRADED_L1|DEGRADED_L2|DEGRADED_L3") String quality,
        @NotNull @Min(2) Integer originalPointCount
) {
}
