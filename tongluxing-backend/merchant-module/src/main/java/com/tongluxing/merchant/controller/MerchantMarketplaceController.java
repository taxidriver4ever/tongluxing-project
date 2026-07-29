package com.tongluxing.merchant.controller;

import org.springframework.web.bind.annotation.*;
import com.tongluxing.common.result.Result;
import com.tongluxing.merchant.service.MerchantEcosystemService;
import com.tongluxing.merchant.vo.*;
import lombok.RequiredArgsConstructor;

/**
 * 负责商家市场相关 HTTP 接口的参数接收、校验和统一结果封装。
 * 具体业务规则委托给服务层，控制器本身不直接操作数据库。
 */
@RestController @RequiredArgsConstructor @RequestMapping("/v1/merchant-market")
public class MerchantMarketplaceController {
    private final MerchantEcosystemService service;
    /** 执行 coupons 对应的领域操作，并返回统一的业务结果。 */
    @GetMapping("/coupons") public Result<PageResult<MerchantCouponOfferVO>> coupons(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return Result.success(service.marketplace(page,size));}
    /** 执行 detail 对应的领域操作，并返回统一的业务结果。 */
    @GetMapping("/coupons/{couponId}") public Result<MerchantCouponOfferVO> detail(@PathVariable Long couponId){return Result.success(service.marketplaceDetail(couponId));}
}
