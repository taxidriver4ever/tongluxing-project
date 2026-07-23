package com.tongluxing.coupon.controller;

import java.math.BigDecimal;
import java.util.List;

import com.tongluxing.coupon.dto.CouponIssueRequest;
import com.tongluxing.coupon.dto.AdminCouponTemplateVO;
import org.springframework.web.bind.annotation.*;
import com.tongluxing.common.result.Result;
import com.tongluxing.coupon.integration.CouponFacade.CouponIssueResult;
import com.tongluxing.coupon.service.CouponService;
import com.tongluxing.user.model.UserModels.*;

import lombok.RequiredArgsConstructor;

/**
 * 优惠券接口控制器。
 *
 * <p>对外提供用户优惠券列表、详情、可用券查询和自主领取能力；
 * 对内提供发券、锁券、订单结果回调等接口，供订单、营销等模块调用。</p>
 */
@RestController
@RequiredArgsConstructor
public class CouponController {
    private final CouponService service;

    /**
     * 分页查询当前登录用户的优惠券列表。
     *
     * @param status 可选，优惠券状态过滤，例如 AVAILABLE、LOCKED、USED
     * @param type 可选，优惠券类型过滤
     * @param page 页码，从 1 开始；服务层会兜底修正非法值
     * @param size 每页数量；服务层会限制最大返回条数
     */
    @GetMapping("/v1/coupons/me")
    public Result<PageResult<CouponSummaryVO>> list(@RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String type,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.currentCoupons(status, type, page, size));
    }

    /**
     * 查询当前登录用户某张优惠券的详情。
     */
    @GetMapping("/v1/coupons/me/{id}")
    public Result<UserCouponDetailVO> detail(@PathVariable Long id) {
        return Result.success(service.currentCoupon(id));
    }

    /**
     * 查询当前订单场景下可使用的优惠券。
     *
     * @param orderType 订单类型，用于匹配优惠券适用范围
     * @param merchantId 商户 ID；平台通用券可为空
     * @param amount 订单金额，用于判断门槛和抵扣金额
     */
    @GetMapping("/v1/coupons/available")
    public Result<List<AvailableCouponVO>> available(@RequestParam String orderType,
                                                     @RequestParam(required = false) Long merchantId,
                                                     @RequestParam BigDecimal amount) {
        return Result.success(service.available(orderType, merchantId, amount));
    }

    /**
     * 当前登录用户主动领取指定模板的优惠券。
     */
    @PostMapping("/v1/coupons/templates/{id}/claim")
    public Result<CouponIssueResult> claim(@PathVariable Long id) {
        return Result.success(service.claim(id));
    }

    @GetMapping("/v1/coupons/templates/claimable")
    public Result<List<AdminCouponTemplateVO>> claimable() {
        return Result.success(service.claimableTemplates());
    }

    /**
     * 内部接口：向指定用户发放优惠券。
     *
     * <p>sourceType + sourceBizId 用于标记发券来源并保证幂等。</p>
     */
    @PostMapping("/internal/v1/coupons/issues")
    public Result<CouponIssueResult> issue(@RequestBody CouponIssueRequest request) {
        return Result.success(service.issue(request.userId(), request.templateId(), request.sourceType(), request.sourceBizId()));
    }

    /**
     * 内部接口：订单创建/支付前锁定优惠券。
     */
    @PostMapping("/internal/v1/coupons/{id}/lock")
    public Result<CouponDeductionVO> lock(@PathVariable Long id, @RequestBody CouponLockRequest request) {
        return Result.success(service.lock(id, request));
    }

    /**
     * 内部接口：根据订单支付结果确认使用或释放优惠券。
     */
    @PostMapping("/internal/v1/coupons/order-result")
    public Result<Void> result(@RequestBody CouponOrderResultRequest request) {
        service.orderResult(request);
        return Result.success();
    }

}
