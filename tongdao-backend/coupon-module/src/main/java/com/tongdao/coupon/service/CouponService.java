package com.tongdao.coupon.service;

import java.math.BigDecimal;
import java.util.List;

import com.tongdao.coupon.integration.CouponFacade;
import com.tongdao.user.model.UserModels.*;

/**
 * 优惠券模块业务服务。
 *
 * <p>在 {@link CouponFacade} 的跨模块发券、统计能力基础上，
 * 提供当前登录用户维度的查询、领取、可用券筛选和订单锁券能力。</p>
 */
public interface CouponService extends CouponFacade {

    /**
     * 分页查询当前登录用户的优惠券列表。
     */
    PageResult<CouponSummaryVO> currentCoupons(String status, String type, int page, int size);

    /**
     * 查询当前登录用户的单张优惠券详情。
     */
    UserCouponDetailVO currentCoupon(Long id);

    /**
     * 查询当前订单场景下可使用的优惠券。
     */
    List<AvailableCouponVO> available(String orderType, Long merchantId, BigDecimal amount);

    /**
     * 当前登录用户主动领取优惠券模板。
     */
    CouponIssueResult claim(Long templateId);

    /**
     * 锁定优惠券，防止同一张券在支付过程中被重复使用。
     */
    CouponDeductionVO lock(Long id, CouponLockRequest request);

    /**
     * 根据订单结果确认使用或释放优惠券。
     */
    void orderResult(CouponOrderResultRequest request);
}
