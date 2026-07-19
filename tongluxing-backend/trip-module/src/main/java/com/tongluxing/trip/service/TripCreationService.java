package com.tongluxing.trip.service;

import java.util.List;

import com.tongluxing.trip.dto.TripDraftSaveRequest;
import com.tongluxing.trip.dto.TripWaypointCommand;
import com.tongluxing.trip.dto.TripWaypointOrderRequest;
import com.tongluxing.trip.vo.TripCreationWaypointResponse;
import com.tongluxing.trip.vo.TripDraftDetailResponse;
import com.tongluxing.trip.vo.TripDraftPublishResponse;
import com.tongluxing.trip.vo.TripDraftRouteResponse;

/** 创建行程闭环服务。 */
public interface TripCreationService {
    TripDraftDetailResponse createDraft(TripDraftSaveRequest request);
    TripDraftDetailResponse getDraft(Long draftId);
    List<TripDraftDetailResponse> listDrafts(String status);
    TripDraftDetailResponse updateDraft(Long draftId, TripDraftSaveRequest request);
    void deleteDraft(Long draftId);
    int cleanupExpiredDrafts();
    TripCreationWaypointResponse addWaypoint(Long draftId, TripWaypointCommand request);
    TripCreationWaypointResponse updateWaypoint(Long draftId, Long waypointId, TripWaypointCommand request);
    void deleteWaypoint(Long draftId, Long waypointId);
    List<TripCreationWaypointResponse> reorderWaypoints(Long draftId, TripWaypointOrderRequest request);
    TripDraftRouteResponse planRoute(Long draftId);
    TripDraftRouteResponse getRoute(Long draftId);
    TripDraftPublishResponse publish(Long draftId);
}
