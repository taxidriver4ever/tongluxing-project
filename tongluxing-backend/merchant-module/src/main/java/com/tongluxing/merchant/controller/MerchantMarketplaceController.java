package com.tongluxing.merchant.controller;

import org.springframework.web.bind.annotation.*;
import com.tongluxing.common.result.Result;
import com.tongluxing.merchant.service.MerchantEcosystemService;
import com.tongluxing.merchant.vo.*;
import lombok.RequiredArgsConstructor;

@RestController @RequiredArgsConstructor @RequestMapping("/v1/merchant-market")
public class MerchantMarketplaceController {
    private final MerchantEcosystemService service;
    @GetMapping("/coupons") public Result<PageResult<MerchantCouponOfferVO>> coupons(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return Result.success(service.marketplace(page,size));}
    @GetMapping("/coupons/{couponId}") public Result<MerchantCouponOfferVO> detail(@PathVariable Long couponId){return Result.success(service.marketplaceDetail(couponId));}
}
