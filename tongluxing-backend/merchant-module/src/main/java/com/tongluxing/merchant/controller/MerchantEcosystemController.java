package com.tongluxing.merchant.controller;

import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.tongluxing.common.result.Result;
import com.tongluxing.merchant.dto.MerchantCouponOfferRequest;
import com.tongluxing.merchant.dto.MerchantStoreRequest;
import com.tongluxing.merchant.service.MerchantEcosystemService;
import com.tongluxing.merchant.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated @RestController @RequiredArgsConstructor @RequestMapping("/v1/merchants")
public class MerchantEcosystemController {
    private final MerchantEcosystemService service;
    @GetMapping("/center/overview") public Result<MerchantCenterOverviewVO> overview(){return Result.success(service.overview());}
    @GetMapping("/stores") public Result<List<MerchantStoreVO>> stores(){return Result.success(service.stores());}
    @PostMapping("/stores") public Result<MerchantStoreVO> createStore(@Valid @RequestBody MerchantStoreRequest r){return Result.success(service.createStore(r));}
    @GetMapping("/coupon-offers") public Result<List<MerchantCouponOfferVO>> offers(){return Result.success(service.offers());}
    @PostMapping("/coupon-offers") public Result<MerchantCouponOfferVO> createOffer(@Valid @RequestBody MerchantCouponOfferRequest r){return Result.success(service.createOffer(r));}
    @PutMapping("/coupon-offers/{couponId}/resubmit") public Result<MerchantCouponOfferVO> resubmit(@PathVariable Long couponId,@Valid @RequestBody MerchantCouponOfferRequest r){return Result.success(service.resubmitOffer(couponId,r));}
}
