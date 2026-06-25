package com.tongdao;

import org.springframework.stereotype.Component;

import com.tongdao.team.integration.TeamTripPort;
import com.tongdao.trip.entity.Trip;
import com.tongdao.trip.mapper.TripMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TeamTripAdapter implements TeamTripPort {
    private final TripMapper tripMapper;

    @Override
    public TeamTripDTO getTrip(Long tripId) {
        Trip trip = tripMapper.findById(tripId);
        if (trip == null) {
            return null;
        }
        return new TeamTripDTO(trip.getId(), trip.getUserId(), trip.getStartName(), trip.getEndName(),
                trip.getDepartureTime());
    }
}
