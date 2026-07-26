package com.tongluxing.admin.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.admin.dto.AdminCouponIssueRequest;
import com.tongluxing.common.result.Result;
import com.tongluxing.coupon.dto.AdminCouponTemplateRequest;
import com.tongluxing.coupon.dto.AdminCouponTemplateVO;
import com.tongluxing.coupon.service.CouponAdminService;
import com.tongluxing.admin.service.AdminCouponIssueService;
import com.tongluxing.coupon.integration.CouponFacade.CouponIssueResult;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/coupon-templates")
public class AdminCouponTemplateController {
    private final CouponAdminService service;
    private final AdminCouponIssueService issueService;
    @GetMapping public Result<List<AdminCouponTemplateVO>> list(@RequestParam(required=false) String status){return Result.success(service.list(status));}
    @PostMapping public Result<AdminCouponTemplateVO> create(@Valid @RequestBody AdminCouponTemplateRequest request){return Result.success(service.create(request));}
    @PutMapping("/{id}/status") public Result<AdminCouponTemplateVO> status(@PathVariable Long id,@RequestBody Map<String,String> body){return Result.success(service.status(id,body.get("status")));}

    @PostMapping("/issues")
    public Result<CouponIssueResult> issue(@Valid @RequestBody AdminCouponIssueRequest request) {
        return Result.success(issueService.issue(request));
    }
}
