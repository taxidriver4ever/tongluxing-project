package com.tongluxing.merchant.service;

import java.util.List;

import com.tongluxing.merchant.dto.CreateMerchantCouponPoolRequest;
import com.tongluxing.merchant.dto.CreateMerchantProductRequest;
import com.tongluxing.merchant.dto.CreatePromotionCodeRequest;
import com.tongluxing.merchant.dto.MerchantApplicationRequest;
import com.tongluxing.merchant.dto.UpdateMerchantProductRequest;
import com.tongluxing.merchant.dto.UpdateMerchantProfileRequest;
import com.tongluxing.merchant.dto.UpdateRewardPoolRequest;
import com.tongluxing.merchant.vo.MerchantAssessmentVO;
import com.tongluxing.merchant.vo.MerchantCouponPoolVO;
import com.tongluxing.merchant.vo.MerchantProductVO;
import com.tongluxing.merchant.vo.MerchantProfileVO;
import com.tongluxing.merchant.vo.MerchantPromotionCodeVO;
import com.tongluxing.merchant.vo.MerchantPromotionStatsVO;
import com.tongluxing.merchant.vo.MerchantRewardPoolVO;
import com.tongluxing.merchant.vo.PageResult;

/**
 * 商家模块业务服务接口。
 */
public interface MerchantService {

    MerchantProfileVO submitApplication(MerchantApplicationRequest request, String requestId);

    MerchantProfileVO currentProfile();

    MerchantProfileVO updateCurrentProfile(UpdateMerchantProfileRequest request);

    PageResult<MerchantProductVO> products(String status, int page, int size);

    MerchantProductVO createProduct(CreateMerchantProductRequest request);

    MerchantProductVO updateProduct(Long productId, UpdateMerchantProductRequest request);

    MerchantProductVO offShelfProduct(Long productId);

    MerchantProductVO productSnapshot(Long productId);

    void decreaseProductStock(Long productId, Integer quantity, String requestId);

    List<MerchantCouponPoolVO> couponPools();

    MerchantCouponPoolVO createCouponPool(CreateMerchantCouponPoolRequest request, String requestId);

    MerchantRewardPoolVO updateRewardPool(UpdateRewardPoolRequest request);

    MerchantPromotionCodeVO createPromotionCode(CreatePromotionCodeRequest request, String requestId);

    List<MerchantPromotionCodeVO> promotionCodes();

    MerchantPromotionStatsVO promotionStats(Long promotionId);

    MerchantAssessmentVO assessment();
}
