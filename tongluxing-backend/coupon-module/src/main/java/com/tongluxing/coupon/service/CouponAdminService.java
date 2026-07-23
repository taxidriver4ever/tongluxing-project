package com.tongluxing.coupon.service;

import java.util.List;

import com.tongluxing.coupon.dto.AdminCouponTemplateRequest;
import com.tongluxing.coupon.dto.AdminCouponTemplateVO;

public interface CouponAdminService {
    AdminCouponTemplateVO create(AdminCouponTemplateRequest request);
    List<AdminCouponTemplateVO> list(String status);
    AdminCouponTemplateVO status(Long id, String status);
}
