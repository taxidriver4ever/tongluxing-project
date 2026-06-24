package com.tongdao.growth.controller;

import org.springframework.web.bind.annotation.*;

import com.tongdao.common.result.Result;
import com.tongdao.growth.integration.GrowthFacade.GrowthGrantResult;
import com.tongdao.growth.service.GrowthService;
import com.tongdao.user.model.UserModels.*;

import lombok.RequiredArgsConstructor;

/**
 * 成长体系接口控制器。
 *
 * <p>对外提供当前用户的成长值、成长明细、徽章墙查询能力；同时提供内部成长值发放接口，
 * 供订单、邀请、活动等业务模块在完成关键动作后写入成长值流水。</p>
 */
@RestController
@RequiredArgsConstructor
public class GrowthController {

    private final GrowthService service;

    /**
     * 查询当前登录用户的成长值概览。
     */
    @GetMapping("/v1/growth/me")
    public Result<GrowthSummaryVO> me() {
        return Result.success(service.getCurrentSummary());
    }

    /**
     * 分页查询当前登录用户的成长值变更流水。
     *
     * @param page 页码，从 1 开始；服务层会兜底修正非法值
     * @param size 每页数量；服务层会限制最大返回条数
     */
    @GetMapping("/v1/growth/me/logs")
    public Result<PageResult<GrowthLogVO>> logs(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.getCurrentLogs(page, size));
    }

    /**
     * 查询当前登录用户的徽章墙，包含已获得和未获得的徽章。
     */
    @GetMapping("/v1/growth/me/badges")
    public Result<BadgeWallVO> badges() {
        return Result.success(service.getCurrentBadges());
    }

    /**
     * 查询指定用户可公开展示的成长值概览。
     */
    @GetMapping("/v1/growth/users/{userId}/public-summary")
    public Result<GrowthSummaryVO> publicSummary(@PathVariable Long userId) {
        return Result.success(service.getSummary(userId));
    }

    /**
     * 内部接口：给指定用户发放或扣减成长值。
     *
     * <p>bizType + bizId + userId 会作为幂等依据，重复请求不会重复增加成长值。</p>
     */
    @PostMapping("/internal/v1/growth/grants")
    public Result<GrowthGrantResult> grant(@RequestBody GrantRequest request) {
        return Result.success(service.grant(
                request.userId(),
                request.bizType(),
                request.bizId(),
                request.points(),
                request.remark()));
    }

    /**
     * 成长值发放请求。
     *
     * @param userId 目标用户 ID
     * @param bizType 业务类型，例如邀请、下单、评价等
     * @param bizId 业务唯一 ID，用于保证同一业务只发放一次
     * @param points 成长值变化量，正数表示发放，负数表示扣减
     * @param remark 流水备注，便于前端展示和后台排查
     */
    public record GrantRequest(Long userId, String bizType, String bizId, Integer points, String remark) {
    }
}
