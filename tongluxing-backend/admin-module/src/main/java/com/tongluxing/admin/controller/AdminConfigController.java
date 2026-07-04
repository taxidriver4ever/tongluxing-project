package com.tongluxing.admin.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.admin.dto.AdminConfigUpdateRequest;
import com.tongluxing.admin.service.AdminConfigService;
import com.tongluxing.admin.vo.AdminConfigVO;
import com.tongluxing.common.result.Result;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 运营配置接口。
 *
 * <p>集中管理成长、邀请、优惠券等运营配置。控制层固定配置域，服务层负责版本化和审计。</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin")
public class AdminConfigController {

    /** 运营配置服务。 */
    private final AdminConfigService configService;

    /** 查询成长规则配置。 */
    @GetMapping("/growth-rules")
    public Result<AdminConfigVO> getGrowthRules(@RequestParam(defaultValue = "growth.rules") String configKey) {
        return Result.success(configService.getConfig("GROWTH", configKey));
    }

    /** 更新成长规则配置并生成新版本。 */
    @PutMapping("/growth-rules")
    public Result<AdminConfigVO> updateGrowthRules(@Valid @RequestBody AdminConfigUpdateRequest request) {
        return Result.success(configService.updateConfig("GROWTH", request));
    }

    /** 查询邀请规则配置。 */
    @GetMapping("/invite-rules")
    public Result<AdminConfigVO> getInviteRules(@RequestParam(defaultValue = "invite.rules") String configKey) {
        return Result.success(configService.getConfig("INVITE", configKey));
    }

    /** 更新邀请规则配置并生成新版本。 */
    @PutMapping("/invite-rules")
    public Result<AdminConfigVO> updateInviteRules(@Valid @RequestBody AdminConfigUpdateRequest request) {
        return Result.success(configService.updateConfig("INVITE", request));
    }

    /** 查询优惠券预算配置。 */
    @GetMapping("/coupon-budgets")
    public Result<AdminConfigVO> getCouponBudgets(@RequestParam(defaultValue = "coupon.budgets") String configKey) {
        return Result.success(configService.getConfig("COUPON", configKey));
    }

    /** 更新优惠券预算配置并生成新版本。 */
    @PutMapping("/coupon-budgets")
    public Result<AdminConfigVO> updateCouponBudgets(@Valid @RequestBody AdminConfigUpdateRequest request) {
        return Result.success(configService.updateConfig("COUPON", request));
    }
}
