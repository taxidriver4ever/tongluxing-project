package com.tongluxing.coupon.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.tongluxing.coupon.dto.CouponQueryDTO;
import com.tongluxing.coupon.dto.AdminCouponTemplateVO;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 优惠券模块数据库访问接口。
 *
 * <p>Mapper 聚焦优惠券模板、用户优惠券、锁券和订单结果流转的数据读写；
 * 业务规则由 Service 层组合这些数据操作完成。</p>
 */
@Mapper
public interface CouponMapper {

    @Insert("""
        insert into coupon_template(id,coupon_name,coupon_type,issuer_id,threshold_amount,discount_amount,scope_json,
          validity_type,valid_days,total_quantity,claimed_quantity,per_user_limit,template_status,created_at,updated_at,deleted)
        values(#{id},#{name},#{type},#{issuerId},#{threshold},#{discount},#{scopeJson},'DAYS_AFTER_CLAIM',#{validDays},
          #{quantity},0,#{perUserLimit},'ACTIVE',#{now},#{now},0)
        """)
    int insertAdminTemplate(@Param("id") Long id,@Param("name") String name,@Param("type") String type,
            @Param("issuerId") Long issuerId,@Param("threshold") BigDecimal threshold,@Param("discount") BigDecimal discount,
            @Param("scopeJson") String scopeJson,@Param("validDays") Integer validDays,@Param("quantity") Integer quantity,
            @Param("perUserLimit") Integer perUserLimit,@Param("now") LocalDateTime now);

    String ADMIN_TEMPLATE_SELECT = """
        select t.id templateId,t.coupon_name couponName,t.coupon_type couponType,t.issuer_id issuerId,
          t.threshold_amount thresholdAmount,t.discount_amount discountAmount,t.scope_json scopeJson,t.valid_days validDays,
          t.total_quantity totalQuantity,t.claimed_quantity claimedQuantity,t.per_user_limit perUserLimit,
          t.template_status templateStatus,
          (select count(*) from coupon_user u where u.template_id=t.id and u.deleted=0) issuedCount,
          (select count(*) from coupon_user u where u.template_id=t.id and u.source_type='CLAIM' and u.deleted=0) claimedCount,
          (select count(*) from coupon_user u where u.template_id=t.id and u.coupon_status='USED' and u.deleted=0) usedCount,
          (select count(*) from verification_record v join coupon_user u on u.id=v.user_coupon_id and u.deleted=0
             where u.template_id=t.id and v.verification_status='SUCCESS' and v.deleted=0) verifiedCount,
          t.created_at createdAt from coupon_template t
        """;

    @Select(ADMIN_TEMPLATE_SELECT + " where t.id=#{id} and t.deleted=0 limit 1")
    AdminCouponTemplateVO findAdminTemplate(@Param("id") Long id);

    @Select(ADMIN_TEMPLATE_SELECT + " where (#{status}='' or t.template_status=#{status}) and t.deleted=0 order by t.created_at desc")
    List<AdminCouponTemplateVO> findAdminTemplates(@Param("status") String status);

    @Update("update coupon_template set template_status=#{status},updated_at=#{now} where id=#{id} and deleted=0")
    int updateAdminTemplateStatus(@Param("id") Long id,@Param("status") String status,@Param("now") LocalDateTime now);

    @Update("update merchant_coupon_pool set pool_status=#{status},updated_at=#{now} where id=#{id} and source_type='PARTNER' and deleted=0")
    int updatePartnerPoolStatusIfPresent(@Param("id") Long id,@Param("status") String status,@Param("now") LocalDateTime now);

    @Select("select count(*) from merchant_coupon_pool where id=#{id} and source_type='PARTNER' and deleted=0")
    int isPartnerPoolTemplate(@Param("id") Long id);

    @Update("""
        update merchant_coupon_pool set used_stock=used_stock+1,updated_at=#{now}
        where id=#{id} and audit_status='APPROVED' and pool_status='ACTIVE' and used_stock<total_stock and deleted=0
        """)
    int consumePartnerPoolStock(@Param("id") Long id,@Param("now") LocalDateTime now);

    @Select(ADMIN_TEMPLATE_SELECT + " where t.template_status='ACTIVE' and t.deleted=0 and t.claimed_quantity<t.total_quantity and not exists (select 1 from merchant_coupon_offer o where o.id=t.id and o.deleted=0) order by t.created_at desc")
    List<AdminCouponTemplateVO> findClaimableTemplates();

    /**
     * 分页查询用户优惠券列表，支持按状态和类型过滤。
     *
     * <p>该 SQL 放在 XML 中，便于表达可选过滤条件。</p>
     */
    List<CouponQueryDTO> findCoupons(@Param("userId") Long userId, @Param("status") String status,
                                     @Param("type") String type, @Param("offset") int offset,
                                     @Param("size") int size);

    /**
     * 统计用户优惠券列表数量，和 findCoupons 使用同一套过滤条件。
     */
    long countCoupons(@Param("userId") Long userId, @Param("status") String status,
                      @Param("type") String type);

    /**
     * 查询用户单张优惠券详情。
     */
    @Select("""
            select cu.id,
                   cu.template_id templateId,
                   ct.coupon_name couponName,
                   ct.coupon_type couponType,
                   ct.issuer_id issuerId,
                   ct.threshold_amount thresholdAmount,
                   ct.discount_amount discountAmount,
                   ct.scope_json scopeJson,
                   cu.coupon_status couponStatus,
                   cu.valid_start_at validStartAt,
                   cu.valid_end_at validEndAt,
                   cu.locked_order_id lockedOrderId,
                   cu.used_order_id usedOrderId
            from coupon_user cu
            join coupon_template ct on ct.id = cu.template_id
            where cu.id = #{id}
              and cu.user_id = #{userId}
              and cu.deleted = 0
              and ct.deleted = 0
            """)
    CouponQueryDTO findCoupon(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 查询金额、有效期、状态和商户维度上可用的优惠券。
     *
     * <p>更细的 scope_json 规则在 Service 层解析，以便处理订单类型等复杂条件。</p>
     */
    @Select("""
            select cu.id,
                   ct.coupon_name couponName,
                   least(ct.discount_amount, #{amount}) deductionAmount,
                   cu.valid_end_at validEndAt,
                   ct.scope_json scopeJson
            from coupon_user cu
            join coupon_template ct on ct.id = cu.template_id
            where cu.user_id = #{userId}
              and cu.coupon_status = 'AVAILABLE'
              and cu.valid_start_at <= now()
              and cu.valid_end_at > now()
              and ct.threshold_amount <= #{amount}
              and (ct.issuer_id is null or ct.issuer_id = #{merchantId})
              and cu.deleted = 0
              and ct.deleted = 0
            """)
    List<CouponQueryDTO> findAvailable(@Param("userId") Long userId,
                                       @Param("merchantId") Long merchantId,
                                       @Param("amount") BigDecimal amount);

    /**
     * 查询可发放的优惠券模板。
     */
    @Select("""
            select *
            from coupon_template
            where id = #{id}
              and template_status = 'ACTIVE'
              and deleted = 0
            limit 1
            """)
    CouponQueryDTO findTemplate(Long id);

    /**
     * 根据发券来源查询用户已领取的券，用于幂等判断。
     */
    @Select("""
            select id,
                   coupon_status couponStatus
            from coupon_user
            where user_id = #{userId}
              and template_id = #{templateId}
              and source_type = #{sourceType}
              and source_biz_id = #{sourceBizId}
              and deleted = 0
            limit 1
            """)
    CouponQueryDTO findBySource(@Param("userId") Long userId, @Param("templateId") Long templateId,
                                @Param("sourceType") String sourceType,
                                @Param("sourceBizId") String sourceBizId);

    @Select("""
            select id,coupon_status couponStatus
            from coupon_user
            where user_id=#{userId} and template_id=#{templateId} and source_type=#{sourceType}
              and source_biz_id=#{sourceBizId} and deleted=0
            limit 1 for update
            """)
    CouponQueryDTO findBySourceForUpdate(@Param("userId") Long userId,@Param("templateId") Long templateId,
                                         @Param("sourceType") String sourceType,@Param("sourceBizId") String sourceBizId);

    /**
     * 写入用户优惠券，初始状态为 AVAILABLE。
     */
    @Insert("""
            insert into coupon_user(
                id, user_id, template_id, source_type, source_biz_id,
                coupon_status, valid_start_at, valid_end_at,
                locked_order_id, used_order_id, used_at,
                created_at, updated_at, deleted
            )
            values (
                #{id}, #{userId}, #{templateId}, #{sourceType}, #{sourceBizId},
                'AVAILABLE', #{start}, #{end},
                null, null, null,
                #{now}, #{now}, 0
            )
            """)
    int insertCoupon(@Param("id") Long id, @Param("userId") Long userId,
                     @Param("templateId") Long templateId, @Param("sourceType") String sourceType,
                     @Param("sourceBizId") String sourceBizId, @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end, @Param("now") LocalDateTime now);

    /**
     * 增加模板已领取数量。
     *
     * <p>通过 claimed_quantity &lt; total_quantity 在数据库层控制库存不会超发。</p>
     */
    @Update("""
            update coupon_template
            set claimed_quantity = claimed_quantity + 1,
                updated_at = #{now}
            where id = #{id}
              and deleted = 0
              and claimed_quantity < total_quantity
            """)
    int increaseClaimed(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Select("select count(*) from merchant_coupon_offer where id=#{templateId} and deleted=0")
    int isMerchantOfferTemplate(@Param("templateId") Long templateId);

    @Update("""
            update merchant_coupon_offer
            set stock=stock-1,sold_count=sold_count+1,updated_at=#{now}
            where id=#{templateId} and audit_status='APPROVED' and offer_status='ACTIVE'
              and publish_time<=#{now} and expire_time>#{now} and stock>0 and deleted=0
            """)
    int consumeMerchantOfferStock(@Param("templateId") Long templateId,@Param("now") LocalDateTime now);

    /**
     * 锁定用户优惠券。
     *
     * <p>只有 AVAILABLE、未过期、满足金额门槛的券才能被锁定。</p>
     */
    @Update("""
            update coupon_user cu
            join coupon_template ct on ct.id = cu.template_id
            set cu.coupon_status = 'LOCKED',
                cu.locked_order_id = #{orderId},
                cu.updated_at = #{now}
            where cu.id = #{id}
              and cu.coupon_status = 'AVAILABLE'
              and cu.valid_start_at <= #{now}
              and cu.valid_end_at > #{now}
              and ct.threshold_amount <= #{amount}
              and cu.deleted = 0
              and ct.deleted = 0
            """)
    int lock(@Param("id") Long id, @Param("orderId") Long orderId,
             @Param("amount") BigDecimal amount, @Param("now") LocalDateTime now);

    /**
     * 查询已经被指定订单锁定的优惠券。
     */
    @Select("""
            select cu.id,
                   cu.user_id userId,
                   ct.discount_amount discountAmount
            from coupon_user cu
            join coupon_template ct on ct.id = cu.template_id
            where cu.id = #{id}
              and cu.locked_order_id = #{orderId}
              and cu.coupon_status = 'LOCKED'
              and cu.deleted = 0
            """)
    CouponQueryDTO findLocked(@Param("id") Long id, @Param("orderId") Long orderId);

    /**
     * 查询指定订单当前锁定的优惠券 ID 列表。
     */
    @Select("""
            select id
            from coupon_user
            where locked_order_id = #{orderId}
              and coupon_status = 'LOCKED'
              and deleted = 0
            """)
    List<Long> findLockedIds(Long orderId);

    /**
     * 订单支付成功后，将已锁定优惠券确认为已使用。
     */
    @Update("""
            update coupon_user
            set coupon_status = 'USED',
                used_order_id = #{orderId},
                used_at = #{now},
                updated_at = #{now}
            where locked_order_id = #{orderId}
              and coupon_status = 'LOCKED'
              and deleted = 0
            """)
    int confirm(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    /**
     * 订单未支付成功或取消时，释放已锁定优惠券。
     */
    @Update("""
            update coupon_user
            set coupon_status = 'AVAILABLE',
                locked_order_id = null,
                updated_at = #{now}
            where locked_order_id = #{orderId}
              and coupon_status = 'LOCKED'
              and deleted = 0
            """)
    int release(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    /**
     * 查询订单是否已经确认用券，用于重复支付成功回调幂等。
     */
    @Select("""
            select count(1)
            from coupon_user
            where used_order_id = #{orderId}
              and coupon_status = 'USED'
              and deleted = 0
            """)
    int countUsedByOrder(Long orderId);

    /**
     * 查询订单仍锁定的优惠券数量，用于释放操作幂等。
     */
    @Select("""
            select count(1)
            from coupon_user
            where locked_order_id = #{orderId}
              and coupon_status = 'LOCKED'
              and deleted = 0
            """)
    int countLockedByOrder(Long orderId);

    /**
     * 统计用户当前仍在有效期内的可用券数量。
     */
    @Select("""
            select count(*)
            from coupon_user
            where user_id = #{userId}
              and coupon_status = 'AVAILABLE'
              and valid_end_at > now()
              and deleted = 0
            """)
    int countAvailable(Long userId);

    /**
     * 统计用户 7 天内即将过期的可用券数量。
     */
    @Select("""
            select count(*)
            from coupon_user
            where user_id = #{userId}
              and coupon_status = 'AVAILABLE'
              and valid_end_at between now() and date_add(now(), interval 7 day)
              and deleted = 0
            """)
    int countExpiring(Long userId);
}
