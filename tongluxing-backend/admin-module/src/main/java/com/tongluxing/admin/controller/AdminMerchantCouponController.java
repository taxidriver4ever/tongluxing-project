package com.tongluxing.admin.controller;

import org.springframework.web.bind.annotation.*;
import com.tongluxing.admin.dto.AdminAuditRequest;
import com.tongluxing.admin.vo.PageResult;
import com.tongluxing.common.result.Result;
import com.tongluxing.merchant.service.MerchantEcosystemService;
import com.tongluxing.merchant.vo.MerchantCouponOfferVO;
import com.tongluxing.user.support.CurrentUserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController @RequiredArgsConstructor @RequestMapping("/v1/admin/merchant-coupons")
public class AdminMerchantCouponController {
    private final MerchantEcosystemService service; private final CurrentUserContext currentUser;
    @GetMapping public Result<PageResult<MerchantCouponOfferVO>> page(@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){var r=service.offersForAdmin(status,page,size);return Result.success(new PageResult<>(r.records(),r.total(),r.page(),r.size()));}
    @GetMapping("/{couponId}") public Result<MerchantCouponOfferVO> detail(@PathVariable Long couponId){return Result.success(service.offerForAdmin(couponId));}
    @PostMapping("/{couponId}/audit") public Result<MerchantCouponOfferVO> audit(@PathVariable Long couponId,@Valid @RequestBody AdminAuditRequest r){return Result.success(service.auditOffer(couponId,r.auditResult(),r.rejectReason(),currentUser.requireUserId()));}
}
