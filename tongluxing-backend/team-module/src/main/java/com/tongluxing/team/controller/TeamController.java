package com.tongluxing.team.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.team.dto.CreateTeamRequest;
import com.tongluxing.team.dto.JoinTeamApplicationRequest;
import com.tongluxing.team.dto.ReviewTeamApplicationRequest;
import com.tongluxing.team.dto.UpdateTeamSettingsRequest;
import com.tongluxing.team.dto.RemoveTeamMemberRequest;
import com.tongluxing.team.dto.ConfirmPassengerVehicleRequest;
import com.tongluxing.team.service.TeamService;
import com.tongluxing.team.vo.TeamApplicationResponse;
import com.tongluxing.team.vo.TeamMemberListResponse;
import com.tongluxing.team.vo.TeamResponse;

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

    /** 当前申请人主动取消尚未审批的入队申请。 */
    @PostMapping("/applications/{applicationId}/cancel")
    public Result<TeamApplicationResponse> cancelApplication(@PathVariable Long applicationId) {
        return Result.success(teamService.cancelApplication(applicationId));
    }

    /** 队长在互动消息中查询自己收到的入队申请。 */
    @GetMapping("/applications/received")
    public Result<java.util.List<TeamApplicationResponse>> receivedApplications(
            @RequestParam(defaultValue = "PENDING") String status) {
        return Result.success(teamService.getReceivedApplications(status));
    }

    /** 查询当前用户发布或加入的当前车队。 */
    @GetMapping("/me/current")
    public Result<TeamResponse> getMyCurrentTeam() {
        return Result.success(teamService.getMyCurrentTeam());
    }

    /** 队长暂停/恢复招募、配置途中加入、阈值和隐私。 */
    @PatchMapping("/{teamId}/settings")
    public Result<TeamResponse> updateSettings(@PathVariable Long teamId,
                                               @Valid @RequestBody UpdateTeamSettingsRequest request) {
        return Result.success(teamService.updateSettings(teamId, request));
    }

    /** 队长移除指定成员。 */
    @PostMapping("/{teamId}/members/{memberUserId}/remove")
    public Result<TeamResponse> removeMember(@PathVariable Long teamId,
                                             @PathVariable Long memberUserId,
                                             @Valid @RequestBody RemoveTeamMemberRequest request) {
        return Result.success(teamService.removeMember(teamId, memberUserId, request));
    }

    /** 被关联车主确认或拒绝乘客的同车关系。 */
    @PostMapping("/{teamId}/members/{passengerUserId}/vehicle-confirmation")
    public Result<TeamMemberListResponse> confirmPassengerVehicle(
            @PathVariable Long teamId,
            @PathVariable Long passengerUserId,
            @Valid @RequestBody ConfirmPassengerVehicleRequest request) {
        return Result.success(teamService.confirmPassengerVehicle(teamId, passengerUserId, request));
    }

    /**
     * 当前用户退出车队，队长不能通过该接口直接退出。
     */
    @PostMapping("/{teamId}/exit")
    public Result<TeamResponse> exit(@PathVariable Long teamId) {
        return Result.success(teamService.exit(teamId));
    }
}
