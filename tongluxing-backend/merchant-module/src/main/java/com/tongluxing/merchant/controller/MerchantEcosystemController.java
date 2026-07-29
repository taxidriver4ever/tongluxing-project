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

/**
 * 负责商家生态相关 HTTP 接口的参数接收、校验和统一结果封装。
 * 具体业务规则委托给服务层，控制器本身不直接操作数据库。
 */
@Validated @RestController @RequiredArgsConstructor @RequestMapping("/v1/merchants")
public class MerchantEcosystemController {
    private final MerchantEcosystemService service;
    /** 执行 overview 对应的领域操作，并返回统一的业务结果。 */
    @GetMapping("/center/overview") public Result<MerchantCenterOverviewVO> overview(){return Result.success(service.overview());}
    /** 执行 stores 对应的领域操作，并返回统一的业务结果。 */
    @GetMapping("/stores") public Result<List<MerchantStoreVO>> stores(){return Result.success(service.stores());}
    /** 校验请求并创建对应资源。 */
    @PostMapping("/stores") public Result<MerchantStoreVO> createStore(@Valid @RequestBody MerchantStoreRequest r){return Result.success(service.createStore(r));}
    /** 执行 offers 对应的领域操作，并返回统一的业务结果。 */
    @GetMapping("/coupon-offers") public Result<List<MerchantCouponOfferVO>> offers(){return Result.success(service.offers());}
    /** 校验请求并创建对应资源。 */
    @PostMapping("/coupon-offers") public Result<MerchantCouponOfferVO> createOffer(@Valid @RequestBody MerchantCouponOfferRequest r){return Result.success(service.createOffer(r));}
    /** 执行 resubmit 对应的领域操作，并返回统一的业务结果。 */
    @PutMapping("/coupon-offers/{couponId}/resubmit") public Result<MerchantCouponOfferVO> resubmit(@PathVariable Long couponId,@Valid @RequestBody MerchantCouponOfferRequest r){return Result.success(service.resubmitOffer(couponId,r));}
}
