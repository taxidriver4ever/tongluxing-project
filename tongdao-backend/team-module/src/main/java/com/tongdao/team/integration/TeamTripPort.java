package com.tongdao.team.integration;

import java.time.LocalDateTime;

public interface TeamTripPort {

    TeamTripDTO getTrip(Long tripId);

    record TeamTripDTO(
            Long tripId,
            Long ownerUserId,
            String startName,
            String endName,
            LocalDateTime departureTime
    ) {
    }
}
