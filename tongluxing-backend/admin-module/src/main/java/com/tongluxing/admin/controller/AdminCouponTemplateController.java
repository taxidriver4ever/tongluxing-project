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

/**
 * 负责管理端优惠券模板相关 HTTP 接口的参数接收、校验和统一结果封装。
 * 具体业务规则委托给服务层，控制器本身不直接操作数据库。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/coupon-templates")
public class AdminCouponTemplateController {
    private final CouponAdminService service;
    private final AdminCouponIssueService issueService;
    /** 按筛选条件查询列表，并限制返回数量以保护接口与数据库。 */
    @GetMapping public Result<List<AdminCouponTemplateVO>> list(@RequestParam(required=false) String status){return Result.success(service.list(status));}
    /** 校验请求并创建资源；重复请求由业务层按幂等规则处理。 */
    @PostMapping public Result<AdminCouponTemplateVO> create(@Valid @RequestBody AdminCouponTemplateRequest request){return Result.success(service.create(request));}
    /** 执行 status 对应的领域操作，并返回统一的业务结果。 */
    @PutMapping("/{id}/status") public Result<AdminCouponTemplateVO> status(@PathVariable Long id,@RequestBody Map<String,String> body){return Result.success(service.status(id,body.get("status")));}

    @PostMapping("/issues")
    /** 执行 issue 对应的领域操作，并返回统一的业务结果。 */
    public Result<CouponIssueResult> issue(@Valid @RequestBody AdminCouponIssueRequest request) {
        return Result.success(issueService.issue(request));
    }
}
