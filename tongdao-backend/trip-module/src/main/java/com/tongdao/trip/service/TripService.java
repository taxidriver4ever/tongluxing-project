package com.tongdao.trip.service;

import java.util.List;

import com.tongdao.trip.dto.CreateTripRequest;
import com.tongdao.trip.dto.UpdateTripRequest;
import com.tongdao.trip.vo.TripListResponse;
import com.tongdao.trip.vo.TripMemberSnapshotResponse;
import com.tongdao.trip.vo.TripResponse;

public interface TripService {

    TripResponse createTrip(CreateTripRequest request);

    TripListResponse getMyTrips(String scope);

    TripResponse getTrip(Long tripId);

    TripResponse updateTrip(Long tripId, UpdateTripRequest request);

    TripResponse endTrip(Long tripId);

    TripResponse cancelTrip(Long tripId);

    TripListResponse getPublicTrips(Integer limit);

    List<TripMemberSnapshotResponse> getMembers(Long tripId);
}
