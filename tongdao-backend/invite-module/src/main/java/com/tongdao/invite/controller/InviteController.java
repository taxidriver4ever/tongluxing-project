package com.tongdao.invite.controller;

import org.springframework.web.bind.annotation.*;
import com.tongdao.common.result.Result;
import com.tongdao.invite.integration.InviteFacade.InviteRewardResult;
import com.tongdao.invite.service.InviteService;
import com.tongdao.user.model.UserModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class InviteController {
    private final InviteService service;

    @GetMapping("/v1/invites/me/code")
    public Result<InviteCodeVO> code() { return Result.success(service.currentCode()); }

    @PostMapping("/v1/invites/bind")
    public Result<InviteBindVO> bind(@Valid @RequestBody BindRequest request) {
        return Result.success(service.bindCurrent(request.inviteCode()));
    }

    @GetMapping("/v1/invites/me/summary")
    public Result<InviteRewardProgressVO> summary() { return Result.success(service.currentProgress()); }

    @GetMapping("/v1/invites/me/records")
    public Result<PageResult<InvitationVO>> records(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.currentRecords(status, page, size));
    }

    @GetMapping("/v1/invites/me/rewards")
    public Result<InviteRewardProgressVO> rewards() { return Result.success(service.currentProgress()); }

    @PostMapping("/internal/v1/invites/first-team-completed")
    public Result<InviteRewardResult> completed(@RequestBody CompleteRequest request) {
        return Result.success(service.completeFirstTeam(request.userId(), request.teamId(), request.bizId()));
    }

    public record BindRequest(@NotBlank String inviteCode) {}
    public record CompleteRequest(Long userId, Long teamId, String bizId) {}
}
