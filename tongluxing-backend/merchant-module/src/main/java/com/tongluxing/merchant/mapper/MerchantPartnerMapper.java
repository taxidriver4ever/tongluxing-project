package com.tongluxing.merchant.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.merchant.vo.MerchantPartnerApplicationVO;

/**
 * 商家合作 MyBatis 数据访问接口。
 * 方法直接对应数据库读写语句；事务边界由调用它的服务层统一管理。
 */
@Mapper
public interface MerchantPartnerMapper {
    String SELECT = """
        select p.merchant_id merchantId,m.merchant_name merchantName,m.category,
          p.application_reason applicationReason,p.cooperation_categories cooperationCategories,
          p.planned_monthly_stock plannedMonthlyStock,p.application_status applicationStatus,
          p.reject_reason rejectReason,p.reviewer_id reviewerId,p.created_at submittedAt,p.reviewed_at reviewedAt,
          coalesce(c.cancellation_status,'NONE') cancellationStatus,c.cancellation_reason cancellationReason,
          c.reject_reason cancellationRejectReason,c.reviewer_id cancellationReviewerId,
          c.requested_at cancellationRequestedAt,c.reviewed_at cancellationReviewedAt
        from merchant_partner_application p join merchant_profile m on m.id=p.merchant_id and m.deleted=0
        left join merchant_partner_cancellation c on c.merchant_id=p.merchant_id and c.deleted=0
        """;

    @Select(SELECT + " where p.merchant_id=#{merchantId} and p.deleted=0 limit 1")
    MerchantPartnerApplicationVO find(@Param("merchantId") Long merchantId);

    @Insert("""
        insert into merchant_partner_application(merchant_id,application_reason,cooperation_categories,
          planned_monthly_stock,application_status,reject_reason,created_at,updated_at,deleted)
        values(#{merchantId},#{reason},#{categories},#{stock},'PENDING','',#{now},#{now},0)
        on duplicate key update application_reason=values(application_reason),cooperation_categories=values(cooperation_categories),
          planned_monthly_stock=values(planned_monthly_stock),application_status='PENDING',reject_reason='',
          reviewer_id=null,reviewed_at=null,updated_at=values(updated_at),deleted=0
        """)
    int submit(@Param("merchantId") Long merchantId, @Param("reason") String reason,
               @Param("categories") String categories, @Param("stock") Integer stock,
               @Param("now") LocalDateTime now);

    @Update("update merchant_partner_cancellation set deleted=1,updated_at=#{now} where merchant_id=#{merchantId} and deleted=0")
    int clearCancellation(@Param("merchantId") Long merchantId, @Param("now") LocalDateTime now);

    @Insert("""
        insert into merchant_partner_cancellation(merchant_id,cancellation_reason,cancellation_status,reject_reason,
          requested_at,updated_at,deleted) values(#{merchantId},#{reason},'PENDING','',#{now},#{now},0)
        on duplicate key update cancellation_reason=values(cancellation_reason),cancellation_status='PENDING',
          reject_reason='',reviewer_id=null,requested_at=values(requested_at),reviewed_at=null,
          updated_at=values(updated_at),deleted=0
        """)
    int requestCancellation(@Param("merchantId") Long merchantId, @Param("reason") String reason,
                            @Param("now") LocalDateTime now);

    @Select(SELECT + " where (#{status}='' or p.application_status=#{status} or c.cancellation_status=#{status}) and p.deleted=0 order by greatest(p.updated_at,coalesce(c.updated_at,p.updated_at)) desc limit #{offset},#{size}")
    List<MerchantPartnerApplicationVO> page(@Param("status") String status, @Param("offset") int offset, @Param("size") int size);

    @Select("""
        select count(*) from merchant_partner_application p
        left join merchant_partner_cancellation c on c.merchant_id=p.merchant_id and c.deleted=0
        where (#{status}='' or p.application_status=#{status} or c.cancellation_status=#{status}) and p.deleted=0
        """)
    long count(@Param("status") String status);

    @Update("""
        update merchant_partner_application set application_status=#{status},reject_reason=#{reason},
          reviewer_id=#{reviewerId},reviewed_at=#{now},updated_at=#{now}
        where merchant_id=#{merchantId} and application_status='PENDING' and deleted=0
        """)
    int audit(@Param("merchantId") Long merchantId, @Param("status") String status,
              @Param("reason") String reason, @Param("reviewerId") Long reviewerId,
              @Param("now") LocalDateTime now);

    @Update("update merchant_profile set merchant_level='PARTNER',updated_at=#{now} where id=#{merchantId} and deleted=0")
    int grantPartner(@Param("merchantId") Long merchantId, @Param("now") LocalDateTime now);

    @Update("""
        update merchant_partner_cancellation set cancellation_status=#{status},reject_reason=#{reason},
          reviewer_id=#{reviewerId},reviewed_at=#{now},updated_at=#{now}
        where merchant_id=#{merchantId} and cancellation_status='PENDING' and deleted=0
        """)
    int auditCancellation(@Param("merchantId") Long merchantId, @Param("status") String status,
                          @Param("reason") String reason, @Param("reviewerId") Long reviewerId,
                          @Param("now") LocalDateTime now);

    @Update("update merchant_partner_application set application_status='CANCELLED',updated_at=#{now} where merchant_id=#{merchantId} and application_status='APPROVED' and deleted=0")
    int cancelApplication(@Param("merchantId") Long merchantId, @Param("now") LocalDateTime now);

    @Update("update merchant_profile set merchant_level='L1',updated_at=#{now} where id=#{merchantId} and deleted=0")
    int revokePartner(@Param("merchantId") Long merchantId, @Param("now") LocalDateTime now);

    @Update("update merchant_coupon_pool set pool_status='INACTIVE',updated_at=#{now} where merchant_id=#{merchantId} and source_type='PARTNER' and deleted=0")
    int deactivatePartnerPools(@Param("merchantId") Long merchantId, @Param("now") LocalDateTime now);

    @Update("""
        update coupon_template t join merchant_coupon_pool p on p.id=t.id and p.source_type='PARTNER'
        set t.template_status='INACTIVE',t.updated_at=#{now}
        where p.merchant_id=#{merchantId} and t.deleted=0 and p.deleted=0
        """)
    int deactivatePartnerTemplates(@Param("merchantId") Long merchantId, @Param("now") LocalDateTime now);
}
