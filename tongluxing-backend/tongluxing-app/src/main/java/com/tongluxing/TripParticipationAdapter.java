package com.tongluxing;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tongluxing.team.entity.Team;
import com.tongluxing.team.entity.TeamMember;
import com.tongluxing.team.mapper.TeamMapper;
import com.tongluxing.team.mapper.TeamMemberMapper;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.integration.TripParticipationPort;
import com.tongluxing.trip.mapper.TripMapper;

import lombok.RequiredArgsConstructor;

/** 行程模块查询“进行中”车队参与关系的适配器。 */
@Component
@RequiredArgsConstructor
public class TripParticipationAdapter implements TripParticipationPort {
    private final TeamMemberMapper teamMemberMapper;
    private final TeamMapper teamMapper;
    private final TripMapper tripMapper;

    @Override
    public Long findRunningParticipatingTripId(Long userId) {
        for (TeamMember membership : teamMemberMapper.findActiveListByUserId(userId)) {
            Team team = teamMapper.findById(membership.getTeamId());
            if (team != null && "ACTIVE".equals(team.getTeamStatus()) && isRunningTrip(team.getTripId())) {
                return team.getTripId();
            }
        }
        Team ownedTeam = teamMapper.findActiveOwnedByUser(userId);
        return ownedTeam != null && isRunningTrip(ownedTeam.getTripId()) ? ownedTeam.getTripId() : null;
    }

    @Override
    public Long findCurrentParticipatingTripId(Long userId) {
        for (TeamMember membership : teamMemberMapper.findActiveListByUserId(userId)) {
            // P0 允许用户保留自己发布的行程，同时只加入一个其他人的有效队伍。
            if ("OWNER".equals(membership.getMemberRole())) {
                continue;
            }
            Team team = teamMapper.findById(membership.getTeamId());
            if (team == null || !"ACTIVE".equals(team.getTeamStatus()) || team.getOwnerUserId().equals(userId)) {
                continue;
            }
            Trip trip = tripMapper.findById(team.getTripId());
            if (trip != null && List.of("PUBLISHED", "READY", "CONFIRMING", "RUNNING", "ONGOING")
                    .contains(trip.getStatus())) {
                return trip.getId();
            }
        }
        return null;
    }

    @Override
    public List<Long> findActiveParticipantUserIds(Long tripId) {
        Team team = teamMapper.findAnyActiveByTripId(tripId);
        if (team == null) {
            return List.of();
        }
        return teamMemberMapper.findActiveByTeamId(team.getId()).stream()
                .map(TeamMember::getUserId)
                .distinct()
                .toList();
    }

    private boolean isRunningTrip(Long tripId) {
        Trip trip = tripMapper.findById(tripId);
        return trip != null && ("RUNNING".equals(trip.getStatus()) || "ONGOING".equals(trip.getStatus()));
    }
}
