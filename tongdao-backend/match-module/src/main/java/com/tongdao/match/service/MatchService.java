package com.tongdao.match.service;

import com.tongdao.match.vo.MatchRecommendationListResponse;
import com.tongdao.match.vo.NearbyTeamListResponse;
import com.tongdao.match.vo.NearbyTripListResponse;

public interface MatchService {

    MatchRecommendationListResponse getTripRecommendations(Long tripId, Integer limit);

    NearbyTripListResponse getNearbyTrips(String latitude, String longitude, Integer radiusMeters, Integer limit);

    NearbyTeamListResponse getNearbyTeams(String latitude, String longitude, Integer radiusMeters, Integer limit);
}
