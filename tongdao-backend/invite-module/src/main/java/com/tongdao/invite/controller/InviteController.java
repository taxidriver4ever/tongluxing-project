package com.tongdao.invite.controller;

import org.springframework.web.bind.annotation.*;
import com.tongdao.common.result.Result;
import com.tongdao.invite.integration.InviteFacade.InviteRewardResult;
import com.tongdao.invite.model.InviteModels.*;
import com.tongdao.invite.service.InviteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * 邀请模块接口控制器。
 *
 * <p>对外提供邀请码查询、邀请码绑定、邀请记录和奖励进度查询；同时提供内部接口接收“首次组队完成”事件。</p>
 */
@RestController
@RequiredArgsConstructor
public class InviteController {

    /** 邀请业务服务。 */
    private final InviteService service;

    /** 获取当前登录用户的邀请码。 */
    @GetMapping("/v1/invites/me/code")
    public Result<InviteCodeVO> code() {
        return Result.success(service.currentCode());
    }

    /** 当前登录用户绑定他人的邀请码。 */
    @PostMapping("/v1/invites/bind")
    public Result<InviteBindVO> bind(@Valid @RequestBody BindRequest request) {
        return Result.success(service.bindCurrent(request.inviteCode()));
    }

    /** 查询当前登录用户的邀请奖励进度摘要。 */
    @GetMapping("/v1/invites/me/summary")
    public Result<InviteRewardProgressVO> summary() {
        return Result.success(service.currentProgress());
    }

    /** 分页查询当前登录用户邀请过的人。 */
    @GetMapping("/v1/invites/me/records")
    public Result<PageResult<InvitationVO>> records(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.currentRecords(status, page, size));
    }

    /** 查询当前登录用户的邀请奖励进度；与 summary 保持同一份数据。 */
    @GetMapping("/v1/invites/me/rewards")
    public Result<InviteRewardProgressVO> rewards() {
        return Result.success(service.currentProgress());
    }

    /** 内部接口：被邀请人首次完成组队后，触发邀请关系转有效并发放奖励。 */
    @PostMapping("/internal/v1/invites/first-team-completed")
    public Result<InviteRewardResult> completed(@RequestBody CompleteRequest request) {
        return Result.success(service.completeFirstTeam(request.userId(), request.teamId(), request.bizId()));
    }

    /** 绑定邀请码请求。 */
    public record BindRequest(
            /** 邀请人分享的邀请码。 */
            @NotBlank
            String inviteCode
    ) {
    }

    /** 首次组队完成事件请求。 */
    public record CompleteRequest(
            /** 完成首次组队的用户 ID，也就是被邀请人。 */
            Long userId,
            /** 完成的队伍 ID。 */
            Long teamId,
            /** 业务幂等号，通常由上游事件或订单/队伍完成记录生成。 */
            String bizId
    ) {
    }
}
