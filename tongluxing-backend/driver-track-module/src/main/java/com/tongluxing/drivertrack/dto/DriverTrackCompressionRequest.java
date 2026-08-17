package com.tongluxing.drivertrack.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** App 端因缓存压力主动压缩本批轨迹的汇总信息。 */
public record DriverTrackCompressionRequest(
        Boolean compressed,
        @Pattern(regexp = "RDP", message = "目前仅支持RDP压缩") String type,
        @Pattern(regexp = "NORMAL|DEGRADED_L1|DEGRADED_L2|DEGRADED_L3") String quality,
        @Min(0) Integer originalPointCount,
        @Min(0) Integer uploadedPointCount
) {
}
