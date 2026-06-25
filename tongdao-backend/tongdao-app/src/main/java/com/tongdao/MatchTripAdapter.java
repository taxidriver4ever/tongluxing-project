package com.tongdao;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tongdao.match.integration.MatchTripPort;
import com.tongdao.trip.entity.Trip;
import com.tongdao.trip.mapper.TripMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MatchTripAdapter implements MatchTripPort {
    private final TripMapper tripMapper;

    @Override
    public MatchTripDTO getTrip(Long tripId) {
        Trip trip = tripMapper.findById(tripId);
        return trip == null ? null : toDTO(trip);
    }

    @Override
    public List<MatchTripDTO> listPublicTrips(int limit) {
        return tripMapper.findPublicTrips(limit).stream()
                .map(this::toDTO)
                .toList();
    }

    private MatchTripDTO toDTO(Trip trip) {
        return new MatchTripDTO(trip.getId(), trip.getUserId(), trip.getStartName(), trip.getEndName(),
                trip.getDepartureTime(), trip.getTravelDepth());
    }
}
