package com.tongluxing.invite.controller;

import com.tongluxing.invite.dto.InviteRewardResult;
import org.springframework.web.bind.annotation.*;
import com.tongluxing.common.result.Result;
import com.tongluxing.invite.dto.CompleteRequest;
import com.tongluxing.invite.model.InviteModels.*;
import com.tongluxing.invite.service.InviteService;
import lombok.RequiredArgsConstructor;

/**
 * 邀请模块接口控制器。
 *
 * <p>对外提供邀请码查询、邀请记录和奖励进度查询；邀请绑定由 UserRegisteredEvent 驱动。</p>
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
}
