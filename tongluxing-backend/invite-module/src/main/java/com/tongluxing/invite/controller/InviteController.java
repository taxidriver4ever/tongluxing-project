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

/**
 * 邀请模块 HTTP 接口控制器。
 *
 * <p>用户侧接口的用户身份均由业务层从登录上下文读取，
 * Controller 只负责路由、参数校验和 {@link Result} 响应包装。</p>
 */
@RestController
@RequiredArgsConstructor
public class InviteController {

    /** 邀请业务服务。 */
    private final InviteService service;

    /** 查询或幂等生成当前用户的唯一邀请码。 */
    @GetMapping({"/v1/invites/me/code", "/v1/invites/code"})
    public Result<InviteCodeVO> code() {
        // 保留新旧两个路径，避免已发布客户端调用失效。
        return Result.success(service.currentCode());
    }

    /** 生成当前用户带签名、有过期时间的邀请二维码。 */
    @GetMapping("/v1/invites/me/qr")
    public Result<InviteQrVO> qr() {
        // 返回 token、deep link 和 PNG Base64，客户端无需自行组装签名内容。
        return Result.success(service.currentQr());
    }

    /** 校验邀请二维码 token 的签名、过期时间和邀请码归属。 */
    @GetMapping("/v1/invites/qr/validate")
    public Result<InviteQrValidationVO> validateQr(@RequestParam String token) {
        // 校验失败作为正常业务响应返回，不向客户端泄露解码异常。
        return Result.success(service.validateQr(token));
    }

    /** 查询当前用户的邀请绑定状态和七天窗口剩余时间。 */
    @GetMapping("/v1/invites/bind-status")
    public Result<InviteBindStatusVO> bindStatus() {
        return Result.success(service.currentBindStatus());
    }

    /** 绑定前预览邀请人的最小公开资料。 */
    @PostMapping("/v1/invites/preview")
    public Result<InvitePreviewVO> preview(@Valid @RequestBody InvitePreviewRequest request) {
        // @Valid 先检查邀请码非空，业务层再校验格式、启用状态和关系合法性。
        return Result.success(service.previewCurrent(request.inviteCode()));
    }

    /** 幂等绑定当前用户与邀请人的唯一关系。 */
    @PostMapping("/v1/invites/bind")
    public Result<InviteBindVO> bind(@Valid @RequestBody InviteBindRequest request) {
        return Result.success(service.bindCurrent(request));
    }

    /** 查询当前用户作为邀请人的统计和奖励进度。 */
    @GetMapping({"/v1/invites/me/summary", "/v1/invites/me/rewards"})
    public Result<InviteRewardProgressVO> summary() {
        return Result.success(service.currentProgress());
    }

    /** 分页查询当前用户邀请到的用户记录。 */
    @GetMapping("/v1/invites/me/records")
    public Result<PageResult<InvitationVO>> records(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        // 页码和每页数在 Service 内会再限制范围，防止超大查询。
        return Result.success(service.currentRecords(status, page, size));
    }

    /** 供组队模块通知“被邀请人首次组队完成”的内部接口。 */
    @PostMapping("/internal/v1/invites/first-team-completed")
    public Result<InviteRewardResult> completed(@RequestBody CompleteRequest request) {
        // bizId 用于上游业务追踪，奖励另有基于关系 ID 的幂等业务号。
        return Result.success(service.completeFirstTeam(request.userId(), request.teamId(), request.bizId()));
    }
}
