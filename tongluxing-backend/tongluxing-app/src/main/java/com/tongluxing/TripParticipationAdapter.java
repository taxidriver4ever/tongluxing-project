package com.tongluxing;

import org.springframework.stereotype.Component;

import com.tongluxing.team.entity.Team;
import com.tongluxing.team.entity.TeamMember;
import com.tongluxing.team.mapper.TeamMapper;
import com.tongluxing.team.mapper.TeamMemberMapper;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.integration.TripParticipationPort;
import com.tongluxing.trip.mapper.TripMapper;

import lombok.RequiredArgsConstructor;

/** 行程模块查询活跃车队参与关系的适配器。 */
@Component
@RequiredArgsConstructor
public class TripParticipationAdapter implements TripParticipationPort {
    private final TeamMemberMapper teamMemberMapper;
    private final TeamMapper teamMapper;
    private final TripMapper tripMapper;

    @Override
    public Long findActiveParticipatingTripId(Long userId) {
        TeamMember membership = teamMemberMapper.findActiveByUserId(userId);
        if (membership != null) {
            Team team = teamMapper.findById(membership.getTeamId());
            if (team != null && "ACTIVE".equals(team.getTeamStatus()) && isActiveTrip(team.getTripId())) {
                return team.getTripId();
            }
        }
        Team ownedTeam = teamMapper.findActiveOwnedByUser(userId);
        return ownedTeam != null && isActiveTrip(ownedTeam.getTripId()) ? ownedTeam.getTripId() : null;
    }

    private boolean isActiveTrip(Long tripId) {
        Trip trip = tripMapper.findById(tripId);
        return trip != null && ("PUBLISHED".equals(trip.getStatus())
                || "RUNNING".equals(trip.getStatus())
                || "ONGOING".equals(trip.getStatus()));
    }
}
