package com.tongluxing.merchant.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongluxing.merchant.dto.MerchantQueryDTO;

/**
 * 商家推广统计数据访问接口。
 */
@Mapper
public interface MerchantPromotionStatsMapper {

    /**
     * 汇总某个推广码的全部聚合数据。
     */
    @Select("""
            select #{promotionId} id,
                   coalesce(sum(register_count), 0) registerCount,
                   coalesce(sum(coupon_claim_count), 0) couponClaimCount,
                   coalesce(sum(coupon_verify_count), 0) couponVerifyCount,
                   coalesce(sum(order_count), 0) orderCount,
                   coalesce(sum(trade_amount), 0) tradeAmount
            from merchant_promotion_stats
            where promotion_code_id = #{promotionId}
              and merchant_id = #{merchantId}
            """)
    MerchantQueryDTO aggregateByPromotion(@Param("merchantId") Long merchantId,
                                          @Param("promotionId") Long promotionId);
}
