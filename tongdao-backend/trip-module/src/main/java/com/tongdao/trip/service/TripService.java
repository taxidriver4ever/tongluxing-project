package com.tongdao.trip.service;

import java.util.List;

import com.tongdao.trip.dto.CreateTripRequest;
import com.tongdao.trip.dto.UpdateTripRequest;
import com.tongdao.trip.vo.TripListResponse;
import com.tongdao.trip.vo.TripMemberSnapshotResponse;
import com.tongdao.trip.vo.TripResponse;

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

    /**
     * 查询行程详情。
     */
    TripResponse getTrip(Long tripId);

    /**
     * 编辑行程。
     */
    TripResponse updateTrip(Long tripId, UpdateTripRequest request);

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
     * 查询行程成员快照。
     */
    List<TripMemberSnapshotResponse> getMembers(Long tripId);
}
