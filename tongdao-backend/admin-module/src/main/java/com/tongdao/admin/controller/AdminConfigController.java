package com.tongdao.admin.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.admin.dto.AdminConfigUpdateRequest;
import com.tongdao.admin.service.AdminConfigService;
import com.tongdao.admin.vo.AdminConfigVO;
import com.tongdao.common.result.Result;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin")
public class AdminConfigController {
    private final AdminConfigService configService;

    @GetMapping("/growth-rules")
    public Result<AdminConfigVO> getGrowthRules(@RequestParam(defaultValue = "growth.rules") String configKey) {
        return Result.success(configService.getConfig("GROWTH", configKey));
    }

    @PutMapping("/growth-rules")
    public Result<AdminConfigVO> updateGrowthRules(@Valid @RequestBody AdminConfigUpdateRequest request) {
        return Result.success(configService.updateConfig("GROWTH", request));
    }

    @GetMapping("/invite-rules")
    public Result<AdminConfigVO> getInviteRules(@RequestParam(defaultValue = "invite.rules") String configKey) {
        return Result.success(configService.getConfig("INVITE", configKey));
    }

    @PutMapping("/invite-rules")
    public Result<AdminConfigVO> updateInviteRules(@Valid @RequestBody AdminConfigUpdateRequest request) {
        return Result.success(configService.updateConfig("INVITE", request));
    }

    @GetMapping("/coupon-budgets")
    public Result<AdminConfigVO> getCouponBudgets(@RequestParam(defaultValue = "coupon.budgets") String configKey) {
        return Result.success(configService.getConfig("COUPON", configKey));
    }

    @PutMapping("/coupon-budgets")
    public Result<AdminConfigVO> updateCouponBudgets(@Valid @RequestBody AdminConfigUpdateRequest request) {
        return Result.success(configService.updateConfig("COUPON", request));
    }
}
