package com.tongluxing.coupon.service;

import java.util.List;

import com.tongluxing.coupon.dto.AdminCouponTemplateRequest;
import com.tongluxing.coupon.dto.AdminCouponTemplateVO;

/**
 * 优惠券管理端业务服务契约，对上层提供稳定的领域操作入口。
 * 调用方无需了解底层表结构、状态校验和事务实现细节。
 */
public interface CouponAdminService {
    AdminCouponTemplateVO create(AdminCouponTemplateRequest request);
    List<AdminCouponTemplateVO> list(String status);
    AdminCouponTemplateVO status(Long id, String status);
}
