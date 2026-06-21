package com.tongdao.team.service;

import com.tongdao.team.dto.CreateTeamRequest;
import com.tongdao.team.dto.JoinTeamApplicationRequest;
import com.tongdao.team.dto.ReviewTeamApplicationRequest;
import com.tongdao.team.vo.TeamApplicationResponse;
import com.tongdao.team.vo.TeamMemberListResponse;
import com.tongdao.team.vo.TeamResponse;

public interface TeamService {

    TeamResponse createTeam(CreateTeamRequest request);

    TeamResponse getTeam(Long teamId);

    TeamMemberListResponse getMembers(Long teamId);

    TeamApplicationResponse apply(Long teamId, JoinTeamApplicationRequest request);

    TeamApplicationResponse review(Long applicationId, ReviewTeamApplicationRequest request);

    TeamResponse exit(Long teamId);
}
