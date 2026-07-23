package com.tongluxing.merchant.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.merchant.dto.MerchantQueryDTO;
import com.tongluxing.merchant.vo.PartnerCouponPoolVO;

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

    @Select("""
        select p.id couponPoolId,p.merchant_id merchantId,m.merchant_name merchantName,p.coupon_name couponName,
          p.coupon_type couponType,p.threshold_amount thresholdAmount,p.discount_amount discountAmount,
          p.total_stock totalStock,p.used_stock usedStock,p.valid_days validDays,p.settlement_mode settlementMode,
          p.audit_status auditStatus,p.pool_status poolStatus
        from merchant_coupon_pool p join merchant_profile m on m.id=p.merchant_id and m.deleted=0
        where p.source_type='PARTNER' and (#{status}='' or p.audit_status=#{status}) and p.deleted=0
        order by p.updated_at desc limit #{offset},#{size}
        """)
    List<PartnerCouponPoolVO> findPartnerPools(@Param("status") String status, @Param("offset") int offset,
                                               @Param("size") int size);

    @Select("select count(*) from merchant_coupon_pool where source_type='PARTNER' and (#{status}='' or audit_status=#{status}) and deleted=0")
    long countPartnerPools(@Param("status") String status);

    @Select("""
        select p.id couponPoolId,p.merchant_id merchantId,m.merchant_name merchantName,p.coupon_name couponName,
          p.coupon_type couponType,p.threshold_amount thresholdAmount,p.discount_amount discountAmount,
          p.total_stock totalStock,p.used_stock usedStock,p.valid_days validDays,p.settlement_mode settlementMode,
          p.audit_status auditStatus,p.pool_status poolStatus
        from merchant_coupon_pool p join merchant_profile m on m.id=p.merchant_id and m.deleted=0
        where p.id=#{id} and p.deleted=0 limit 1
        """)
    PartnerCouponPoolVO findPartnerPool(@Param("id") Long id);

    @Update("""
        update merchant_coupon_pool set audit_status=#{status},pool_status=case when #{status}='APPROVED' then 'ACTIVE' else 'INACTIVE' end,
          updated_at=#{now} where id=#{id} and source_type='PARTNER' and audit_status='PENDING' and deleted=0
        """)
    int auditPartnerPool(@Param("id") Long id, @Param("status") String status, @Param("now") LocalDateTime now);

    @Insert("""
        insert into coupon_template(id,coupon_name,coupon_type,issuer_id,threshold_amount,discount_amount,
          scope_json,validity_type,valid_days,valid_start_at,valid_end_at,total_quantity,claimed_quantity,
          per_user_limit,template_status,created_at,updated_at,deleted)
        select id,left(coupon_name,64),coupon_type,merchant_id,threshold_amount,discount_amount,
          json_object('orderTypes',json_array('MERCHANT'),'merchantId',merchant_id),'DAYS_AFTER_CLAIM',valid_days,
          null,null,total_stock,used_stock,1,'ACTIVE',#{now},#{now},0
        from merchant_coupon_pool where id=#{id} and audit_status='APPROVED' and deleted=0
        on duplicate key update coupon_name=values(coupon_name),coupon_type=values(coupon_type),issuer_id=values(issuer_id),
          threshold_amount=values(threshold_amount),discount_amount=values(discount_amount),scope_json=values(scope_json),
          valid_days=values(valid_days),total_quantity=values(total_quantity),template_status='ACTIVE',updated_at=values(updated_at),deleted=0
        """)
    int activatePartnerTemplate(@Param("id") Long id, @Param("now") LocalDateTime now);
}
