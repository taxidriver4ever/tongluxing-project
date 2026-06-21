package com.tongdao.team.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.common.result.Result;
import com.tongdao.team.dto.CreateTeamRequest;
import com.tongdao.team.dto.JoinTeamApplicationRequest;
import com.tongdao.team.dto.ReviewTeamApplicationRequest;
import com.tongdao.team.service.TeamService;
import com.tongdao.team.vo.TeamApplicationResponse;
import com.tongdao.team.vo.TeamMemberListResponse;
import com.tongdao.team.vo.TeamResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/teams")
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    public Result<TeamResponse> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        return Result.success(teamService.createTeam(request));
    }

    @GetMapping("/{teamId}")
    public Result<TeamResponse> getTeam(@PathVariable Long teamId) {
        return Result.success(teamService.getTeam(teamId));
    }

    @GetMapping("/{teamId}/members")
    public Result<TeamMemberListResponse> getMembers(@PathVariable Long teamId) {
        return Result.success(teamService.getMembers(teamId));
    }

    @PostMapping("/{teamId}/applications")
    public Result<TeamApplicationResponse> apply(@PathVariable Long teamId,
                                                 @Valid @RequestBody JoinTeamApplicationRequest request) {
        return Result.success(teamService.apply(teamId, request));
    }

    @PostMapping("/applications/{applicationId}/review")
    public Result<TeamApplicationResponse> review(@PathVariable Long applicationId,
                                                  @Valid @RequestBody ReviewTeamApplicationRequest request) {
        return Result.success(teamService.review(applicationId, request));
    }

    @PostMapping("/{teamId}/exit")
    public Result<TeamResponse> exit(@PathVariable Long teamId) {
        return Result.success(teamService.exit(teamId));
    }
}
