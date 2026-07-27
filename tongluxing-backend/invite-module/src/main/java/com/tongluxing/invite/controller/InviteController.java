package com.tongluxing.invite.controller;

import org.springframework.web.bind.annotation.*;

import com.tongluxing.common.result.Result;
import com.tongluxing.invite.dto.CompleteRequest;
import com.tongluxing.invite.dto.InviteBindRequest;
import com.tongluxing.invite.dto.InvitePreviewRequest;
import com.tongluxing.invite.dto.InviteRewardResult;
import com.tongluxing.invite.model.InviteModels.*;
import com.tongluxing.invite.service.InviteService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 邀请模块接口控制器。 */
@RestController
@RequiredArgsConstructor
public class InviteController {

    private final InviteService service;

    @GetMapping({"/v1/invites/me/code", "/v1/invites/code"})
    public Result<InviteCodeVO> code() {
        return Result.success(service.currentCode());
    }

    @GetMapping("/v1/invites/me/qr")
    public Result<InviteQrVO> qr() {
        return Result.success(service.currentQr());
    }

    @GetMapping("/v1/invites/qr/validate")
    public Result<InviteQrValidationVO> validateQr(@RequestParam String token) {
        return Result.success(service.validateQr(token));
    }

    @GetMapping("/v1/invites/bind-status")
    public Result<InviteBindStatusVO> bindStatus() {
        return Result.success(service.currentBindStatus());
    }

    @PostMapping("/v1/invites/preview")
    public Result<InvitePreviewVO> preview(@Valid @RequestBody InvitePreviewRequest request) {
        return Result.success(service.previewCurrent(request.inviteCode()));
    }

    @PostMapping("/v1/invites/bind")
    public Result<InviteBindVO> bind(@Valid @RequestBody InviteBindRequest request) {
        return Result.success(service.bindCurrent(request));
    }

    @GetMapping({"/v1/invites/me/summary", "/v1/invites/me/rewards"})
    public Result<InviteRewardProgressVO> summary() {
        return Result.success(service.currentProgress());
    }

    @GetMapping("/v1/invites/me/records")
    public Result<PageResult<InvitationVO>> records(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.currentRecords(status, page, size));
    }

    @PostMapping("/internal/v1/invites/first-team-completed")
    public Result<InviteRewardResult> completed(@RequestBody CompleteRequest request) {
        return Result.success(service.completeFirstTeam(request.userId(), request.teamId(), request.bizId()));
    }
}
