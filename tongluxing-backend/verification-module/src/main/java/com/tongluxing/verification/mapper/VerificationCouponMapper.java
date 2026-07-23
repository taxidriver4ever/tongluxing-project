package com.tongluxing.verification.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 核验用户券归属、状态和发行商，防止伪造券核销码。 */
@Mapper
public interface VerificationCouponMapper {
    @Select("""
        select count(*)
        from coupon_user u
        join coupon_template t on t.id = u.template_id and t.deleted = 0
        join merchant_profile m on m.id = #{merchantId}
          and m.audit_status = 'APPROVED'
          and m.status = 'ACTIVE'
          and m.deleted = 0
        where u.id = #{couponId}
          and u.user_id = #{userId}
          and u.coupon_status = 'AVAILABLE'
          and u.valid_end_at > now()
          and (t.issuer_id is null or t.issuer_id = #{merchantId})
          and u.deleted = 0
        """)
    int countUsable(@Param("couponId") Long couponId,@Param("userId") Long userId,@Param("merchantId") Long merchantId);
}
