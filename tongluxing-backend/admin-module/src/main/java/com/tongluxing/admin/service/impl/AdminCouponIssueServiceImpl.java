package com.tongluxing.admin.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tongluxing.admin.dto.AdminCouponIssueRequest;
import com.tongluxing.admin.mapper.AdminAuditLogMapper;
import com.tongluxing.admin.service.AdminCouponIssueService;
import com.tongluxing.coupon.integration.CouponFacade;
import com.tongluxing.coupon.integration.CouponFacade.CouponIssueResult;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/** 人工发券实现：优惠券模块负责库存与幂等，后台模块负责权限上下文和操作审计。 */
@Service
@RequiredArgsConstructor
public class AdminCouponIssueServiceImpl implements AdminCouponIssueService {

    private final CouponFacade couponFacade;
    private final AdminAuditLogMapper auditLogMapper;
    private final AdminSupport support;
    private final CurrentUserContext currentUser;

    @Override
    @Transactional
    public CouponIssueResult issue(AdminCouponIssueRequest request) {
        CouponIssueResult result = couponFacade.issue(
                request.userId(), request.templateId(), "ADMIN_MANUAL", request.requestId());

        // 相同 requestId 重试时优惠券门面会返回原发券结果；审计日志只保留一条。
        if (auditLogMapper.findByRequestId(request.requestId()) == null) {
            support.audit(
                    "COUPON_MANUAL_ISSUE",
                    "coupon-module",
                    "COUPON_TEMPLATE",
                    String.valueOf(request.templateId()),
                    request.requestId(),
                    currentUser.requireUserId(),
                    request.reason().trim(),
                    "SUCCESS",
                    "{\"userId\":\"%s\",\"templateId\":\"%s\"}".formatted(
                            request.userId(), request.templateId()),
                    "{\"couponUserId\":\"%s\",\"status\":\"%s\",\"duplicate\":%s}".formatted(
                            result.couponUserId(), result.status(), result.duplicate()));
        }
        return result;
    }
}
