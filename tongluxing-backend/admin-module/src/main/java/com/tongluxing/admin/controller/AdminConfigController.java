package com.tongluxing.admin.controller;

import java.time.YearMonth;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.admin.dto.AdminConfigUpdateRequest;
import com.tongluxing.admin.service.AdminConfigService;
import com.tongluxing.admin.service.MonthlyLevelCouponGrantService;
import com.tongluxing.admin.service.impl.MonthlyLevelCouponGrantServiceImpl;
import com.tongluxing.admin.vo.AdminConfigVO;
import com.tongluxing.common.result.Result;
import com.tongluxing.common.exception.BusinessException;

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
    private final MonthlyLevelCouponGrantService monthlyGrantService;

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

    /** 查询月度等级发券规则。 */
    @GetMapping("/coupon-issuance-rules")
    public Result<AdminConfigVO> getCouponIssuanceRules() {
        return Result.success(configService.getConfig("COUPON", MonthlyLevelCouponGrantServiceImpl.CONFIG_KEY));
    }

    /** 更新月度等级发券规则。 */
    @PutMapping("/coupon-issuance-rules")
    public Result<AdminConfigVO> updateCouponIssuanceRules(@Valid @RequestBody AdminConfigUpdateRequest request) {
        if (!MonthlyLevelCouponGrantServiceImpl.CONFIG_KEY.equals(request.configKey())) {
            throw new BusinessException("配置键必须为 " + MonthlyLevelCouponGrantServiceImpl.CONFIG_KEY);
        }
        return Result.success(configService.updateConfig("COUPON", request));
    }

    /** 手动补发指定月份，来源唯一键保证重复执行不会重复到账。 */
    @PostMapping("/coupon-issuance-rules/run")
    public Result<MonthlyLevelCouponGrantService.GrantSummary> runCouponIssuanceRules(
            @RequestParam(required = false) String month) {
        YearMonth target;
        try {
            target = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        } catch (RuntimeException exception) {
            throw new BusinessException("月份格式必须为 YYYY-MM");
        }
        return Result.success(monthlyGrantService.grant(target));
    }
}
