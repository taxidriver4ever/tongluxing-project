package com.tongluxing.admin.service;

import com.tongluxing.admin.dto.AdminCouponIssueRequest;
import com.tongluxing.coupon.integration.CouponFacade.CouponIssueResult;

/** 运营后台人工发券服务，统一处理业务幂等与审计日志。 */
public interface AdminCouponIssueService {

    CouponIssueResult issue(AdminCouponIssueRequest request);
}
