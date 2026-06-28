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

/**
 * 车队模块接口控制器，提供建队、查队、成员、入队审批和退出能力。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/teams")
public class TeamController {

    private final TeamService teamService;

    /**
     * 基于当前用户自己的行程创建车队。
     */
    @PostMapping
    public Result<TeamResponse> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        return Result.success(teamService.createTeam(request));
    }

    /**
     * 查询车队详情。
     */
    @GetMapping("/{teamId}")
    public Result<TeamResponse> getTeam(@PathVariable Long teamId) {
        return Result.success(teamService.getTeam(teamId));
    }

    /**
     * 查询车队当前活跃成员列表。
     */
    @GetMapping("/{teamId}/members")
    public Result<TeamMemberListResponse> getMembers(@PathVariable Long teamId) {
        return Result.success(teamService.getMembers(teamId));
    }

    /**
     * 当前用户提交入队申请。
     */
    @PostMapping("/{teamId}/applications")
    public Result<TeamApplicationResponse> apply(@PathVariable Long teamId,
                                                 @Valid @RequestBody JoinTeamApplicationRequest request) {
        return Result.success(teamService.apply(teamId, request));
    }

    /**
     * 队长审批入队申请。
     */
    @PostMapping("/applications/{applicationId}/review")
    public Result<TeamApplicationResponse> review(@PathVariable Long applicationId,
                                                  @Valid @RequestBody ReviewTeamApplicationRequest request) {
        return Result.success(teamService.review(applicationId, request));
    }

    /**
     * 当前用户退出车队，队长不能通过该接口直接退出。
     */
    @PostMapping("/{teamId}/exit")
    public Result<TeamResponse> exit(@PathVariable Long teamId) {
        return Result.success(teamService.exit(teamId));
    }
}
