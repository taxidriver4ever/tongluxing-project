package com.tongluxing.merchant.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongluxing.merchant.dto.MerchantQueryDTO;

/**
 * 商家券池配置数据访问接口。
 */
@Mapper
public interface MerchantCouponPoolMapper {

    /**
     * 查询商家券池列表。
     */
    @Select("""
            select id,
                   merchant_id merchantId,
                   coupon_name couponName,
                   coupon_type couponType,
                   source_type sourceType,
                   threshold_amount thresholdAmount,
                   discount_amount discountAmount,
                   discount_rate discountRate,
                   total_stock totalStock,
                   used_stock usedStock,
                   valid_days validDays,
                   settlement_mode settlementMode,
                   audit_status auditStatus,
                   pool_status poolStatus
            from merchant_coupon_pool
            where merchant_id = #{merchantId}
              and deleted = 0
            order by created_at desc
            """)
    List<MerchantQueryDTO> findByMerchant(@Param("merchantId") Long merchantId);

    /**
     * 新增商家券池配置，默认进入待审核状态。
     */
    @Insert("""
            insert into merchant_coupon_pool(
                id, merchant_id, coupon_name, coupon_type, source_type,
                threshold_amount, discount_amount, discount_rate, total_stock,
                used_stock, valid_days, settlement_mode, audit_status, pool_status,
                created_at, updated_at, deleted
            )
            values (
                #{id}, #{merchantId}, #{couponName}, #{couponType}, #{sourceType},
                #{thresholdAmount}, #{discountAmount}, #{discountRate}, #{totalStock},
                0, #{validDays}, #{settlementMode}, 'PENDING', 'ACTIVE',
                #{now}, #{now}, 0
            )
            """)
    int insertPool(@Param("id") Long id,
                   @Param("merchantId") Long merchantId,
                   @Param("couponName") String couponName,
                   @Param("couponType") String couponType,
                   @Param("sourceType") String sourceType,
                   @Param("thresholdAmount") BigDecimal thresholdAmount,
                   @Param("discountAmount") BigDecimal discountAmount,
                   @Param("discountRate") BigDecimal discountRate,
                   @Param("totalStock") Integer totalStock,
                   @Param("validDays") Integer validDays,
                   @Param("settlementMode") String settlementMode,
                   @Param("now") LocalDateTime now);

    /**
     * 查询单个券池配置。
     */
    @Select("""
            select id,
                   merchant_id merchantId,
                   coupon_name couponName,
                   coupon_type couponType,
                   source_type sourceType,
                   threshold_amount thresholdAmount,
                   discount_amount discountAmount,
                   discount_rate discountRate,
                   total_stock totalStock,
                   used_stock usedStock,
                   valid_days validDays,
                   settlement_mode settlementMode,
                   audit_status auditStatus,
                   pool_status poolStatus
            from merchant_coupon_pool
            where id = #{id}
              and merchant_id = #{merchantId}
              and deleted = 0
            limit 1
            """)
    MerchantQueryDTO findById(@Param("merchantId") Long merchantId, @Param("id") Long id);
}
