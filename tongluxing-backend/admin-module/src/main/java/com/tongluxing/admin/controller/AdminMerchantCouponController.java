package com.tongluxing.admin.controller;

import org.springframework.web.bind.annotation.*;
import com.tongluxing.admin.dto.AdminAuditRequest;
import com.tongluxing.admin.dto.MerchantCouponStatusRequest;
import com.tongluxing.admin.vo.PageResult;
import com.tongluxing.common.result.Result;
import com.tongluxing.merchant.service.MerchantEcosystemService;
import com.tongluxing.merchant.vo.MerchantCouponOfferVO;
import com.tongluxing.user.support.CurrentUserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 负责管理端商家优惠券相关 HTTP 接口的参数接收、校验和统一结果封装。
 * 具体业务规则委托给服务层，控制器本身不直接操作数据库。
 */
@RestController @RequiredArgsConstructor @RequestMapping("/v1/admin/merchant-coupons")
public class AdminMerchantCouponController {
    private final MerchantEcosystemService service; private final CurrentUserContext currentUser;
    /** 执行 page 对应的领域操作，并返回统一的业务结果。 */
    @GetMapping public Result<PageResult<MerchantCouponOfferVO>> page(@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){var r=service.offersForAdmin(status,page,size);return Result.success(new PageResult<>(r.records(),r.total(),r.page(),r.size()));}
    /** 执行 detail 对应的领域操作，并返回统一的业务结果。 */
    @GetMapping("/{couponId}") public Result<MerchantCouponOfferVO> detail(@PathVariable Long couponId){return Result.success(service.offerForAdmin(couponId));}
    /** 执行 audit 对应的领域操作，并返回统一的业务结果。 */
    @PostMapping("/{couponId}/audit") public Result<MerchantCouponOfferVO> audit(@PathVariable Long couponId,@Valid @RequestBody AdminAuditRequest r){return Result.success(service.auditOffer(couponId,r.auditResult(),r.rejectReason(),currentUser.requireUserId()));}
    /** 执行 status 对应的领域操作，并返回统一的业务结果。 */
    @PutMapping("/{couponId}/status") public Result<MerchantCouponOfferVO> status(@PathVariable Long couponId,@Valid @RequestBody MerchantCouponStatusRequest r){currentUser.requireUserId();return Result.success(service.manageOfferStatus(couponId,r.status()));}
}
