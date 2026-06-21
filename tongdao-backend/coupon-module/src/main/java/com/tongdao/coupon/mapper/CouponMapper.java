package com.tongdao.coupon.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CouponMapper {
    List<Map<String, Object>> findCoupons(@Param("userId") Long userId, @Param("status") String status,
                                          @Param("type") String type, @Param("offset") int offset,
                                          @Param("size") int size);

    long countCoupons(@Param("userId") Long userId, @Param("status") String status, @Param("type") String type);

    @Select("select cu.id,cu.template_id templateId,ct.coupon_name couponName,ct.coupon_type couponType,ct.issuer_id issuerId,ct.threshold_amount thresholdAmount,ct.discount_amount discountAmount,ct.scope_json scopeJson,cu.coupon_status couponStatus,cu.valid_start_at validStartAt,cu.valid_end_at validEndAt,cu.locked_order_id lockedOrderId,cu.used_order_id usedOrderId from coupon_user cu join coupon_template ct on ct.id=cu.template_id where cu.id=#{id} and cu.user_id=#{userId} and cu.deleted=0 and ct.deleted=0")
    Map<String, Object> findCoupon(@Param("id") Long id, @Param("userId") Long userId);

    @Select("select cu.id,ct.coupon_name couponName,least(ct.discount_amount,#{amount}) deductionAmount,cu.valid_end_at validEndAt,ct.scope_json scopeJson from coupon_user cu join coupon_template ct on ct.id=cu.template_id where cu.user_id=#{userId} and cu.coupon_status='AVAILABLE' and cu.valid_start_at<=now() and cu.valid_end_at>now() and ct.threshold_amount<=#{amount} and (ct.issuer_id is null or ct.issuer_id=#{merchantId}) and cu.deleted=0 and ct.deleted=0")
    List<Map<String, Object>> findAvailable(@Param("userId") Long userId, @Param("merchantId") Long merchantId,
                                            @Param("amount") BigDecimal amount);

    @Select("select * from coupon_template where id=#{id} and template_status='ACTIVE' and deleted=0 limit 1")
    Map<String, Object> findTemplate(Long id);

    @Select("select id,coupon_status couponStatus from coupon_user where user_id=#{userId} and template_id=#{templateId} and source_type=#{sourceType} and source_biz_id=#{sourceBizId} and deleted=0 limit 1")
    Map<String, Object> findBySource(@Param("userId") Long userId, @Param("templateId") Long templateId,
                                     @Param("sourceType") String sourceType, @Param("sourceBizId") String sourceBizId);

    @Insert("insert into coupon_user(id,user_id,template_id,source_type,source_biz_id,coupon_status,valid_start_at,valid_end_at,locked_order_id,used_order_id,used_at,created_at,updated_at,deleted) values(#{id},#{userId},#{templateId},#{sourceType},#{sourceBizId},'AVAILABLE',#{start},#{end},null,null,null,#{now},#{now},0)")
    int insertCoupon(@Param("id") Long id, @Param("userId") Long userId, @Param("templateId") Long templateId,
                     @Param("sourceType") String sourceType, @Param("sourceBizId") String sourceBizId,
                     @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                     @Param("now") LocalDateTime now);

    @Update("update coupon_template set claimed_quantity=claimed_quantity+1,updated_at=#{now} where id=#{id} and deleted=0 and claimed_quantity<total_quantity")
    int increaseClaimed(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("update coupon_user cu join coupon_template ct on ct.id=cu.template_id set cu.coupon_status='LOCKED',cu.locked_order_id=#{orderId},cu.updated_at=#{now} where cu.id=#{id} and cu.coupon_status='AVAILABLE' and cu.valid_start_at<=#{now} and cu.valid_end_at>#{now} and ct.threshold_amount<=#{amount} and cu.deleted=0 and ct.deleted=0")
    int lock(@Param("id") Long id, @Param("orderId") Long orderId, @Param("amount") BigDecimal amount,
             @Param("now") LocalDateTime now);

    @Select("select cu.id,cu.user_id userId,ct.discount_amount discountAmount from coupon_user cu join coupon_template ct on ct.id=cu.template_id where cu.id=#{id} and cu.locked_order_id=#{orderId} and cu.coupon_status='LOCKED' and cu.deleted=0")
    Map<String, Object> findLocked(@Param("id") Long id, @Param("orderId") Long orderId);

    @Select("select id from coupon_user where locked_order_id=#{orderId} and coupon_status='LOCKED' and deleted=0")
    List<Long> findLockedIds(Long orderId);

    @Update("update coupon_user set coupon_status='USED',used_order_id=#{orderId},used_at=#{now},updated_at=#{now} where locked_order_id=#{orderId} and coupon_status='LOCKED' and deleted=0")
    int confirm(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    @Update("update coupon_user set coupon_status='AVAILABLE',locked_order_id=null,updated_at=#{now} where locked_order_id=#{orderId} and coupon_status='LOCKED' and deleted=0")
    int release(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    @Select("select count(*) from coupon_user where user_id=#{userId} and coupon_status='AVAILABLE' and valid_end_at>now() and deleted=0")
    int countAvailable(Long userId);

    @Select("select count(*) from coupon_user where user_id=#{userId} and coupon_status='AVAILABLE' and valid_end_at between now() and date_add(now(),interval 7 day) and deleted=0")
    int countExpiring(Long userId);
}
