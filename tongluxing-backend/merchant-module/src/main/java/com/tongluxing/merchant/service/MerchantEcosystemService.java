package com.tongluxing.merchant.service;

import java.util.List;
import com.tongluxing.merchant.dto.MerchantCouponOfferRequest;
import com.tongluxing.merchant.dto.MerchantStoreRequest;
import com.tongluxing.merchant.vo.*;

public interface MerchantEcosystemService {
    MerchantCenterOverviewVO overview();
    List<MerchantStoreVO> stores();
    MerchantStoreVO createStore(MerchantStoreRequest request);
    List<MerchantCouponOfferVO> offers();
    MerchantCouponOfferVO createOffer(MerchantCouponOfferRequest request);
    MerchantCouponOfferVO resubmitOffer(Long couponId, MerchantCouponOfferRequest request);
    PageResult<MerchantCouponOfferVO> offersForAdmin(String status,int page,int size);
    MerchantCouponOfferVO offerForAdmin(Long couponId);
    MerchantCouponOfferVO auditOffer(Long couponId,String result,String reason,Long reviewerId);
    PageResult<MerchantCouponOfferVO> marketplace(int page,int size);
    MerchantCouponOfferVO marketplaceDetail(Long couponId);
    MerchantCouponOfferVO groupbuySnapshot(Long couponId);
    void reserveGroupbuyStock(Long couponId, Integer quantity);
}
