package com.tongdao.merchant.service;

import java.util.List;

import com.tongdao.merchant.dto.CreateMerchantCouponPoolRequest;
import com.tongdao.merchant.dto.CreateMerchantProductRequest;
import com.tongdao.merchant.dto.CreatePromotionCodeRequest;
import com.tongdao.merchant.dto.MerchantApplicationRequest;
import com.tongdao.merchant.dto.UpdateMerchantProductRequest;
import com.tongdao.merchant.dto.UpdateMerchantProfileRequest;
import com.tongdao.merchant.dto.UpdateRewardPoolRequest;
import com.tongdao.merchant.vo.MerchantAssessmentVO;
import com.tongdao.merchant.vo.MerchantCouponPoolVO;
import com.tongdao.merchant.vo.MerchantProductVO;
import com.tongdao.merchant.vo.MerchantProfileVO;
import com.tongdao.merchant.vo.MerchantPromotionCodeVO;
import com.tongdao.merchant.vo.MerchantPromotionStatsVO;
import com.tongdao.merchant.vo.MerchantRewardPoolVO;
import com.tongdao.merchant.vo.PageResult;

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

    List<MerchantCouponPoolVO> couponPools();

    MerchantCouponPoolVO createCouponPool(CreateMerchantCouponPoolRequest request, String requestId);

    MerchantRewardPoolVO updateRewardPool(UpdateRewardPoolRequest request);

    MerchantPromotionCodeVO createPromotionCode(CreatePromotionCodeRequest request, String requestId);

    List<MerchantPromotionCodeVO> promotionCodes();

    MerchantPromotionStatsVO promotionStats(Long promotionId);

    MerchantAssessmentVO assessment();
}
