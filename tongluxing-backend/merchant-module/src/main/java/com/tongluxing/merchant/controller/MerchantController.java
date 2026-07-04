package com.tongluxing.merchant.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.merchant.dto.CreateMerchantCouponPoolRequest;
import com.tongluxing.merchant.dto.CreateMerchantProductRequest;
import com.tongluxing.merchant.dto.CreatePromotionCodeRequest;
import com.tongluxing.merchant.dto.MerchantApplicationRequest;
import com.tongluxing.merchant.dto.UpdateMerchantProductRequest;
import com.tongluxing.merchant.dto.UpdateMerchantProfileRequest;
import com.tongluxing.merchant.dto.UpdateRewardPoolRequest;
import com.tongluxing.merchant.service.MerchantService;
import com.tongluxing.merchant.vo.MerchantAssessmentVO;
import com.tongluxing.merchant.vo.MerchantCouponPoolVO;
import com.tongluxing.merchant.vo.MerchantProductVO;
import com.tongluxing.merchant.vo.MerchantProfileVO;
import com.tongluxing.merchant.vo.MerchantPromotionCodeVO;
import com.tongluxing.merchant.vo.MerchantPromotionStatsVO;
import com.tongluxing.merchant.vo.MerchantRewardPoolVO;
import com.tongluxing.merchant.vo.PageResult;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 商家模块 HTTP 接口控制器。
 *
 * <p>接口严格围绕文档定义的商家入驻、资料、商品、券池、奖励池、推广码、统计和考核能力。
 * 订单、支付、核销、用户券实例等能力不在 merchant-module 内处理。</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/merchants")
public class MerchantController {
    private final MerchantService merchantService;

    /** 提交商家入驻申请。 */
    @PostMapping("/applications")
    public Result<MerchantProfileVO> submitApplication(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody MerchantApplicationRequest request) {
        return Result.success(merchantService.submitApplication(request, requestId));
    }

    /** 查询当前商家资料。 */
    @GetMapping("/me")
    public Result<MerchantProfileVO> me() {
        return Result.success(merchantService.currentProfile());
    }

    /** 修改当前商家资料。 */
    @PutMapping("/me")
    public Result<MerchantProfileVO> updateMe(@Valid @RequestBody UpdateMerchantProfileRequest request) {
        return Result.success(merchantService.updateCurrentProfile(request));
    }

    /** 查询当前商家的商品列表。 */
    @GetMapping("/products")
    public Result<PageResult<MerchantProductVO>> products(@RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return Result.success(merchantService.products(status, page, size));
    }

    /** 发布拼团商品。 */
    @PostMapping("/products")
    public Result<MerchantProductVO> createProduct(@Valid @RequestBody CreateMerchantProductRequest request) {
        return Result.success(merchantService.createProduct(request));
    }

    /** 编辑拼团商品。 */
    @PutMapping("/products/{productId}")
    public Result<MerchantProductVO> updateProduct(@PathVariable Long productId,
                                                   @Valid @RequestBody UpdateMerchantProductRequest request) {
        return Result.success(merchantService.updateProduct(productId, request));
    }

    /** 下架拼团商品。 */
    @PutMapping("/products/{productId}/off-shelf")
    public Result<MerchantProductVO> offShelfProduct(@PathVariable Long productId) {
        return Result.success(merchantService.offShelfProduct(productId));
    }

    /** 查询商家券池配置。 */
    @GetMapping("/coupons")
    public Result<List<MerchantCouponPoolVO>> coupons() {
        return Result.success(merchantService.couponPools());
    }

    /** 创建商家券池配置。 */
    @PostMapping("/coupons")
    public Result<MerchantCouponPoolVO> createCouponPool(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody CreateMerchantCouponPoolRequest request) {
        return Result.success(merchantService.createCouponPool(request, requestId));
    }

    /** 设置是否加入奖励合作商家池。 */
    @PutMapping("/reward-pool")
    public Result<MerchantRewardPoolVO> updateRewardPool(@Valid @RequestBody UpdateRewardPoolRequest request) {
        return Result.success(merchantService.updateRewardPool(request));
    }

    /** 创建商家推广码。 */
    @PostMapping("/promotion-codes")
    public Result<MerchantPromotionCodeVO> createPromotionCode(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody CreatePromotionCodeRequest request) {
        return Result.success(merchantService.createPromotionCode(request, requestId));
    }

    /** 查询商家推广码列表。 */
    @GetMapping("/promotion-codes")
    public Result<List<MerchantPromotionCodeVO>> promotionCodes() {
        return Result.success(merchantService.promotionCodes());
    }

    /** 查询推广活动聚合数据。 */
    @GetMapping("/promotions/{promotionId}/stats")
    public Result<MerchantPromotionStatsVO> promotionStats(@PathVariable Long promotionId) {
        return Result.success(merchantService.promotionStats(promotionId));
    }

    /** 查询商家考核中心。 */
    @GetMapping("/assessment")
    public Result<MerchantAssessmentVO> assessment() {
        return Result.success(merchantService.assessment());
    }
}
