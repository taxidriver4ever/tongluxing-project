package com.tongdao;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tongdao.match.integration.MatchTeamPort;
import com.tongdao.team.entity.Team;
import com.tongdao.team.mapper.TeamMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MatchTeamAdapter implements MatchTeamPort {
    private final TeamMapper teamMapper;

    @Override
    public List<MatchTeamDTO> listPublicActiveTeams(int limit) {
        return teamMapper.findPublicActive(limit).stream()
                .map(this::toDTO)
                .toList();
    }

    private MatchTeamDTO toDTO(Team team) {
        return new MatchTeamDTO(team.getId(), team.getTripId(), team.getOwnerUserId(), team.getTeamName(),
                team.getStartName(), team.getEndName(), team.getDepartureTime(), team.getCurrentMemberCount(),
                team.getMaxMemberCount());
    }
}
