package com.tongluxing.drivertrack.service;

import com.tongluxing.drivertrack.dto.DriverTrackBatchRequest;
import com.tongluxing.drivertrack.dto.DriverTrackPointRequest;
import com.tongluxing.drivertrack.dto.MockDeviationRequest;
import com.tongluxing.drivertrack.vo.DriverDeviationResponse;
import com.tongluxing.drivertrack.vo.DriverDistanceResponse;
import com.tongluxing.drivertrack.vo.DriverTrackListResponse;
import com.tongluxing.drivertrack.vo.DriverTrackUploadResponse;

/**
 * 驾驶轨迹服务。
 */
public interface DriverTrackService {

    DriverTrackUploadResponse uploadPoint(DriverTrackPointRequest request);

    DriverTrackListResponse uploadBatch(DriverTrackBatchRequest request);

    DriverTrackListResponse getTrack(Long tripId);

    DriverDeviationResponse getDeviation(Long tripId);

    DriverDistanceResponse getDistance(Long tripId);

    DriverDeviationResponse mockDeviation(Long tripId, MockDeviationRequest request);
}
