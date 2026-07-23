package com.tongluxing.merchant.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;
import com.tongluxing.merchant.dto.MerchantEcosystemQueryDTO;

@Mapper
public interface MerchantEcosystemMapper {
    @Insert("""
        insert into merchant_store(id,merchant_id,store_name,address,longitude,latitude,contact_phone_mask,business_hours,parking_info,store_status,created_at,updated_at,deleted)
        values(#{id},#{merchantId},#{storeName},#{address},#{longitude},#{latitude},#{phoneMask},#{businessHours},#{parkingInfo},'ACTIVE',#{now},#{now},0)
        """)
    int insertStore(@Param("id") Long id,@Param("merchantId") Long merchantId,@Param("storeName") String storeName,
                    @Param("address") String address,@Param("longitude") BigDecimal longitude,@Param("latitude") BigDecimal latitude,
                    @Param("phoneMask") String phoneMask,@Param("businessHours") String businessHours,
                    @Param("parkingInfo") String parkingInfo,@Param("now") LocalDateTime now);

    @Select("""
        select id storeId,merchant_id merchantId,store_name storeName,address,longitude,latitude,
               contact_phone_mask contactPhoneMask,business_hours businessHours,parking_info parkingInfo,store_status storeStatus
        from merchant_store where merchant_id=#{merchantId} and deleted=0 order by created_at desc
        """)
    List<MerchantEcosystemQueryDTO> stores(@Param("merchantId") Long merchantId);

    @Select("""
        select id storeId,merchant_id merchantId,store_name storeName,address,longitude,latitude,
               contact_phone_mask contactPhoneMask,business_hours businessHours,parking_info parkingInfo,store_status storeStatus
        from merchant_store where id=#{storeId} and merchant_id=#{merchantId} and deleted=0 limit 1
        """)
    MerchantEcosystemQueryDTO store(@Param("merchantId") Long merchantId,@Param("storeId") Long storeId);

    @Insert("""
        insert into merchant_coupon_offer(id,merchant_id,store_id,coupon_name,cover_image_key,description,category,
          original_price,sale_price,stock,sold_count,limit_count,group_enabled,group_people,group_timeout_hours,publish_time,expire_time,use_start_time,use_end_time,
          reservation_required,refundable,holiday_available,stackable,use_instructions,audit_status,reject_reason,
          offer_status,created_at,updated_at,deleted)
        values(#{id},#{merchantId},#{storeId},#{couponName},#{coverImageKey},#{description},#{category},
          #{originalPrice},#{salePrice},#{stock},0,#{limitCount},#{groupEnabled},#{groupPeople},#{groupTimeoutHours},#{publishTime},#{expireTime},#{useStartTime},#{useEndTime},
          #{reservationRequired},#{refundable},#{holidayAvailable},#{stackable},#{useInstructions},'PENDING','',
          'ACTIVE',#{now},#{now},0)
        """)
    int insertOffer(@Param("id") Long id,@Param("merchantId") Long merchantId,@Param("storeId") Long storeId,
        @Param("couponName") String couponName,@Param("coverImageKey") String coverImageKey,@Param("description") String description,
        @Param("category") String category,@Param("originalPrice") BigDecimal originalPrice,@Param("salePrice") BigDecimal salePrice,
        @Param("stock") Integer stock,@Param("limitCount") Integer limitCount,@Param("groupEnabled") boolean groupEnabled,
        @Param("groupPeople") Integer groupPeople,@Param("groupTimeoutHours") Integer groupTimeoutHours,@Param("publishTime") LocalDateTime publishTime,
        @Param("expireTime") LocalDateTime expireTime,@Param("useStartTime") LocalDateTime useStartTime,
        @Param("useEndTime") LocalDateTime useEndTime,@Param("reservationRequired") boolean reservationRequired,
        @Param("refundable") boolean refundable,@Param("holidayAvailable") boolean holidayAvailable,
        @Param("stackable") boolean stackable,@Param("useInstructions") String useInstructions,@Param("now") LocalDateTime now);

    String OFFER_SELECT = """
        select c.id couponId,c.merchant_id merchantId,m.merchant_name merchantName,c.store_id storeId,
          s.store_name storeName,s.address storeAddress,s.longitude,s.latitude,c.coupon_name couponName,
          c.cover_image_key coverImageKey,c.description,c.category,c.original_price originalPrice,c.sale_price salePrice,
          c.stock,c.sold_count soldCount,c.limit_count limitCount,c.group_enabled groupEnabled,
          c.group_people groupPeople,c.group_timeout_hours groupTimeoutHours,c.publish_time publishTime,c.expire_time expireTime,
          c.use_start_time useStartTime,c.use_end_time useEndTime,c.reservation_required reservationRequired,
          c.refundable,c.holiday_available holidayAvailable,c.stackable,c.use_instructions useInstructions,
          c.audit_status auditStatus,c.reject_reason rejectReason,c.offer_status offerStatus
        from merchant_coupon_offer c join merchant_profile m on m.id=c.merchant_id
        join merchant_store s on s.id=c.store_id
        """;

    @Select(OFFER_SELECT + " where c.merchant_id=#{merchantId} and c.deleted=0 order by c.created_at desc")
    List<MerchantEcosystemQueryDTO> offersByMerchant(@Param("merchantId") Long merchantId);

    @Select(OFFER_SELECT + " where c.id=#{couponId} and c.deleted=0 limit 1")
    MerchantEcosystemQueryDTO offer(@Param("couponId") Long couponId);

    @Select(OFFER_SELECT + " where (#{status}='' or c.audit_status=#{status}) and c.deleted=0 order by c.created_at desc limit #{offset},#{size}")
    List<MerchantEcosystemQueryDTO> offersForAdmin(@Param("status") String status,@Param("offset") int offset,@Param("size") int size);

    @Select("select count(*) from merchant_coupon_offer where (#{status}='' or audit_status=#{status}) and deleted=0")
    long countOffers(@Param("status") String status);

    @Select(OFFER_SELECT + " where c.audit_status='APPROVED' and c.offer_status='ACTIVE' and c.publish_time<=#{now} and c.expire_time>#{now} and c.deleted=0 order by c.reviewed_at desc limit #{offset},#{size}")
    List<MerchantEcosystemQueryDTO> marketOffers(@Param("now") LocalDateTime now,@Param("offset") int offset,@Param("size") int size);

    @Update("""
        update merchant_coupon_offer set audit_status=#{status},reject_reason=#{reason},reviewer_id=#{reviewerId},reviewed_at=#{now},updated_at=#{now}
        where id=#{couponId} and audit_status='PENDING' and deleted=0
        """)
    int auditOffer(@Param("couponId") Long couponId,@Param("status") String status,@Param("reason") String reason,
                   @Param("reviewerId") Long reviewerId,@Param("now") LocalDateTime now);

    @Update("""
        update merchant_coupon_offer
        set offer_status=#{status}, updated_at=#{now}
        where id=#{couponId} and audit_status='APPROVED' and deleted=0
        """)
    int updateOfferStatus(@Param("couponId") Long couponId, @Param("status") String status,
                          @Param("now") LocalDateTime now);

    @Update("""
        update coupon_template
        set template_status=#{status}, updated_at=#{now}
        where id=#{couponId} and deleted=0
        """)
    int updateClaimTemplateStatus(@Param("couponId") Long couponId, @Param("status") String status,
                                  @Param("now") LocalDateTime now);

    /** 审核通过后用同一业务 ID 激活用户可领取模板，避免市场券与券包模板断链。 */
    @Insert("""
        insert into coupon_template(id,coupon_name,coupon_type,issuer_id,threshold_amount,discount_amount,
          scope_json,validity_type,valid_days,valid_start_at,valid_end_at,total_quantity,claimed_quantity,
          per_user_limit,template_status,created_at,updated_at,deleted)
        select id,left(coupon_name,64),'CASH',merchant_id,0,greatest(original_price-sale_price,0.01),
          json_object('orderTypes',json_array('MERCHANT'),'merchantId',merchant_id,'storeId',store_id),
          'FIXED',null,use_start_time,use_end_time,stock,0,1,'ACTIVE',#{now},#{now},0
        from merchant_coupon_offer where id=#{couponId} and audit_status='APPROVED' and deleted=0
        on duplicate key update coupon_name=values(coupon_name),issuer_id=values(issuer_id),
          discount_amount=values(discount_amount),scope_json=values(scope_json),
          valid_start_at=values(valid_start_at),valid_end_at=values(valid_end_at),
          total_quantity=values(total_quantity),template_status='ACTIVE',updated_at=values(updated_at),deleted=0
        """)
    int activateClaimTemplate(@Param("couponId") Long couponId,@Param("now") LocalDateTime now);

    @Update("""
        update merchant_coupon_offer set coupon_name=#{couponName},cover_image_key=#{coverImageKey},description=#{description},
          category=#{category},original_price=#{originalPrice},sale_price=#{salePrice},stock=#{stock},limit_count=#{limitCount},
          group_enabled=#{groupEnabled},group_people=#{groupPeople},group_timeout_hours=#{groupTimeoutHours},
          publish_time=#{publishTime},expire_time=#{expireTime},use_start_time=#{useStartTime},use_end_time=#{useEndTime},
          reservation_required=#{reservationRequired},refundable=#{refundable},holiday_available=#{holidayAvailable},
          stackable=#{stackable},use_instructions=#{useInstructions},audit_status='PENDING',reject_reason='',updated_at=#{now}
        where id=#{couponId} and merchant_id=#{merchantId} and audit_status='REJECTED' and deleted=0
        """)
    int resubmitOffer(@Param("couponId") Long couponId,@Param("merchantId") Long merchantId,
        @Param("couponName") String couponName,@Param("coverImageKey") String coverImageKey,@Param("description") String description,
        @Param("category") String category,@Param("originalPrice") BigDecimal originalPrice,@Param("salePrice") BigDecimal salePrice,
        @Param("stock") Integer stock,@Param("limitCount") Integer limitCount,@Param("groupEnabled") boolean groupEnabled,
        @Param("groupPeople") Integer groupPeople,@Param("groupTimeoutHours") Integer groupTimeoutHours,@Param("publishTime") LocalDateTime publishTime,
        @Param("expireTime") LocalDateTime expireTime,@Param("useStartTime") LocalDateTime useStartTime,
        @Param("useEndTime") LocalDateTime useEndTime,@Param("reservationRequired") boolean reservationRequired,
        @Param("refundable") boolean refundable,@Param("holidayAvailable") boolean holidayAvailable,
        @Param("stackable") boolean stackable,@Param("useInstructions") String useInstructions,@Param("now") LocalDateTime now);

    @Update("""
        update merchant_coupon_offer set stock=stock-#{quantity},updated_at=#{now}
        where id=#{couponId} and deleted=0 and audit_status='APPROVED' and offer_status='ACTIVE'
          and group_enabled=1 and stock>=#{quantity}
        """)
    int reserveGroupbuyStock(@Param("couponId") Long couponId,@Param("quantity") Integer quantity,@Param("now") LocalDateTime now);
}
