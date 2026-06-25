package com.tongdao.match.integration;

import java.time.LocalDateTime;
import java.util.List;

public interface MatchTripPort {

    MatchTripDTO getTrip(Long tripId);

    List<MatchTripDTO> listPublicTrips(int limit);

    record MatchTripDTO(
            Long tripId,
            Long userId,
            String startName,
            String endName,
            LocalDateTime departureTime,
            String travelDepth
    ) {
    }
}
