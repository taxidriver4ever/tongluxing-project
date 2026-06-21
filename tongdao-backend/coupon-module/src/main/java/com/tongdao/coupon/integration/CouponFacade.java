package com.tongdao.coupon.integration;
import java.math.BigDecimal;import java.util.List;import com.tongdao.user.model.UserModels.*;
public interface CouponFacade { CouponCountVO count(Long userId); CouponIssueResult issue(Long userId,Long templateId,String sourceType,String sourceBizId); record CouponIssueResult(Long couponUserId,String status,boolean duplicate){} }
