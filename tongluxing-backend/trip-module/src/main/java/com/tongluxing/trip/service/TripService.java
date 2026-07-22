package com.tongluxing.trip.service;

import java.util.List;

import com.tongluxing.trip.dto.CreateTripRequest;
import com.tongluxing.trip.dto.UpdateTripRequest;
import com.tongluxing.trip.dto.TripTimeConflictRequest;
import com.tongluxing.trip.vo.ActiveTripStateResponse;
import com.tongluxing.trip.vo.TripListResponse;
import com.tongluxing.trip.vo.TripMemberSnapshotResponse;
import com.tongluxing.trip.vo.TripResponse;
import com.tongluxing.trip.vo.TripTimeConflictResponse;

/**
 * 行程模块业务服务接口。
 */
public interface TripService {

    /**
     * 创建并发布行程。
     */
    TripResponse createTrip(CreateTripRequest request);

    /**
     * 查询当前用户行程列表。
     */
    TripListResponse getMyTrips(String scope);

    /** 查询当前用户拥有或参加的进行中行程。 */
    ActiveTripStateResponse getActiveTripState();

    /** 检查预计出发时间是否与已发布行程重叠；仅返回提醒。 */
    TripTimeConflictResponse checkTimeConflict(TripTimeConflictRequest request);

    /**
     * 查询行程详情。
     */
    TripResponse getTrip(Long tripId);

    /**
     * 编辑行程。
     */
    TripResponse updateTrip(Long tripId, UpdateTripRequest request);

    /**
     * 开始驾驶行程。
     */
    TripResponse startTrip(Long tripId);

    /**
     * 结束行程。
     */
    TripResponse endTrip(Long tripId);

    /**
     * 取消行程。
     */
    TripResponse cancelTrip(Long tripId);

    /**
     * 查询公开行程列表。
     */
    TripListResponse getPublicTrips(Integer limit);

    /**
     * 查询当前用户驾驶中的行程。
     */
    TripResponse getCurrentDrivingTrip();

    /**
     * 查询行程成员快照。
     */
    List<TripMemberSnapshotResponse> getMembers(Long tripId);
}
