package com.tongluxing.application.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tongluxing.match.integration.MatchTeamPort.MatchTeamDTO;
import com.tongluxing.team.entity.TeamJoinApplication;
import com.tongluxing.team.entity.TeamMember;
import com.tongluxing.team.mapper.TeamJoinApplicationMapper;
import com.tongluxing.team.mapper.TeamMapper;
import com.tongluxing.team.mapper.TeamMemberMapper;
import com.tongluxing.team.service.TeamService;
import com.tongluxing.user.mapper.UserDomainMapper;

class MatchTeamAdapterTest {

    @Test
    void relationshipStatusesUsesFixedBatchQueriesAndOwnerNeedsNoQuery() {
        TeamMapper teamMapper = mock(TeamMapper.class);
        TeamMemberMapper memberMapper = mock(TeamMemberMapper.class);
        TeamJoinApplicationMapper applicationMapper = mock(TeamJoinApplicationMapper.class);
        MatchTeamAdapter adapter = new MatchTeamAdapter(teamMapper, memberMapper, applicationMapper,
                mock(TeamService.class), mock(UserDomainMapper.class));

        when(memberMapper.findStatusesByUserAndTeams(eq(7L), anyList()))
                .thenReturn(List.of(member(102L, "ACTIVE"), member(104L, "EXITED")));
        when(applicationMapper.findLatestStatusesByUserAndTeams(eq(7L), anyList()))
                .thenReturn(List.of(application(103L, "PENDING"), application(104L, "APPROVED")));

        Map<Long, String> statuses = adapter.relationshipStatuses(List.of(
                team(101L, 7L), team(102L, 8L), team(103L, 9L), team(104L, 10L)), 7L);

        assertEquals(Map.of(101L, "OWNER", 102L, "JOINED", 103L, "PENDING", 104L, "EXITED"), statuses);
        verify(memberMapper).findStatusesByUserAndTeams(eq(7L), anyList());
        verify(applicationMapper).findLatestStatusesByUserAndTeams(eq(7L), anyList());
        verify(teamMapper, never()).findById(org.mockito.ArgumentMatchers.anyLong());
        verify(memberMapper, never()).findByTeamAndUser(org.mockito.ArgumentMatchers.anyLong(), eq(7L));
        verify(applicationMapper, never()).findLatest(org.mockito.ArgumentMatchers.anyLong(), eq(7L));
    }

    private MatchTeamDTO team(Long teamId, Long ownerId) {
        return new MatchTeamDTO(teamId, teamId + 1000, ownerId, "team", null, null,
                "start", "end", null, 1, 4, "OPEN", true);
    }

    private TeamMember member(Long teamId, String status) {
        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setMemberStatus(status);
        return member;
    }

    private TeamJoinApplication application(Long teamId, String status) {
        TeamJoinApplication application = new TeamJoinApplication();
        application.setTeamId(teamId);
        application.setApplicationStatus(status);
        return application;
    }
}
