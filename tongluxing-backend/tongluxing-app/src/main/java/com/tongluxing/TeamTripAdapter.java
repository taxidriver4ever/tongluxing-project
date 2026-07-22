package com.tongluxing;

import org.springframework.stereotype.Component;

import com.tongluxing.team.integration.TeamTripPort;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.mapper.TripMapper;

import lombok.RequiredArgsConstructor;

/** 车队模块访问行程模块的适配器。 */
@Component
@RequiredArgsConstructor
public class TeamTripAdapter implements TeamTripPort {

    private final TripMapper tripMapper;

    @Override
    public Long findRunningOwnedTripId(Long userId) {
        return tripMapper.findRunningTripIdByUserId(userId);
    }

    @Override
    public TeamTripDTO getTrip(Long tripId) {
        Trip trip = tripMapper.findById(tripId);
        if (trip == null) {
            return null;
        }
        return new TeamTripDTO(trip.getId(), trip.getUserId(), trip.getStartName(), trip.getEndName(),
                trip.getDepartureTime(), trip.getStatus());
    }
}
