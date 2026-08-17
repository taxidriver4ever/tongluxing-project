package com.tongluxing.drivertrack.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * App 批量上传 GPS 点请求。
 */
public record DriverTrackBatchRequest(
        @Valid
        @NotEmpty
        @Size(max = 200)
        List<DriverTrackPointRequest> points,
        @Valid DriverTrackCompressionRequest compression,
        @Valid @Size(max = 200) List<DriverTrackCompressedSegmentRequest> compressedSegments
) {
}
