package com.tongluxing.merchant.service;

import com.tongluxing.merchant.dto.MerchantPartnerApplicationRequest;
import com.tongluxing.merchant.dto.MerchantPartnerCancellationRequest;
import com.tongluxing.merchant.vo.MerchantPartnerApplicationVO;
import com.tongluxing.merchant.vo.PageResult;
import com.tongluxing.merchant.vo.PartnerCouponPoolVO;

/**
 * 商家合作业务服务契约，对上层提供稳定的领域操作入口。
 * 调用方无需了解底层表结构、状态校验和事务实现细节。
 */
public interface MerchantPartnerService {
    MerchantPartnerApplicationVO current();
    MerchantPartnerApplicationVO apply(MerchantPartnerApplicationRequest request);
    MerchantPartnerApplicationVO requestCancellation(MerchantPartnerCancellationRequest request);
    PageResult<MerchantPartnerApplicationVO> applications(String status, int page, int size);
    MerchantPartnerApplicationVO detail(Long merchantId);
    MerchantPartnerApplicationVO audit(Long merchantId, String result, String reason, Long reviewerId);
    MerchantPartnerApplicationVO auditCancellation(Long merchantId, String result, String reason, Long reviewerId);
    boolean isApprovedPartner(Long merchantId);
    PageResult<PartnerCouponPoolVO> partnerCoupons(String status, int page, int size);
    PartnerCouponPoolVO partnerCoupon(Long id);
    PartnerCouponPoolVO auditPartnerCoupon(Long id, String result);
}
