package com.tongluxing.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.admin.dto.AdminAuditRequest;
import com.tongluxing.admin.vo.PageResult;
import com.tongluxing.common.result.Result;
import com.tongluxing.merchant.service.MerchantPartnerService;
import com.tongluxing.merchant.vo.PartnerCouponPoolVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 负责管理端合作商家优惠券相关 HTTP 接口的参数接收、校验和统一结果封装。
 * 具体业务规则委托给服务层，控制器本身不直接操作数据库。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/partner-coupons")
public class AdminPartnerCouponController {
    private final MerchantPartnerService service;
    /** 执行 page 对应的领域操作，并返回统一的业务结果。 */
    @GetMapping public Result<PageResult<PartnerCouponPoolVO>> page(@RequestParam(required=false) String status,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){
        var r=service.partnerCoupons(status,page,size);return Result.success(new PageResult<>(r.records(),r.total(),r.page(),r.size()));
    }
    /** 执行 detail 对应的领域操作，并返回统一的业务结果。 */
    @GetMapping("/{id}") public Result<PartnerCouponPoolVO> detail(@PathVariable Long id){return Result.success(service.partnerCoupon(id));}
    /** 执行 audit 对应的领域操作，并返回统一的业务结果。 */
    @PostMapping("/{id}/audit") public Result<PartnerCouponPoolVO> audit(@PathVariable Long id,@Valid @RequestBody AdminAuditRequest request){return Result.success(service.auditPartnerCoupon(id,request.auditResult()));}
}
