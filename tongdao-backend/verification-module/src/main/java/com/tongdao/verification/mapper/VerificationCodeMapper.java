package com.tongdao.verification.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongdao.verification.entity.VerificationCode;

@Mapper
public interface VerificationCodeMapper {

    @Insert("""
            insert into verification_code
                (id, verification_code, qr_content, biz_type, biz_id, order_id,
                 user_coupon_id, user_id, merchant_id, amount, code_status,
                 expire_at, verified_at, created_at, updated_at, deleted)
            values
                (#{id}, #{verificationCode}, #{qrContent}, #{bizType}, #{bizId}, #{orderId},
                 #{userCouponId}, #{userId}, #{merchantId}, #{amount}, #{codeStatus},
                 #{expireAt}, #{verifiedAt}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(VerificationCode code);

    @Select("""
            select id, verification_code, qr_content, biz_type, biz_id, order_id,
                   user_coupon_id, user_id, merchant_id, amount, code_status,
                   expire_at, verified_at, created_at, updated_at, deleted
            from verification_code
            where id = #{id}
              and deleted = 0
            limit 1
            """)
    VerificationCode findById(@Param("id") Long id);

    @Select("""
            select id, verification_code, qr_content, biz_type, biz_id, order_id,
                   user_coupon_id, user_id, merchant_id, amount, code_status,
                   expire_at, verified_at, created_at, updated_at, deleted
            from verification_code
            where verification_code = #{verificationCode}
              and deleted = 0
            limit 1
            """)
    VerificationCode findByCode(@Param("verificationCode") String verificationCode);

    @Select("""
            select id, verification_code, qr_content, biz_type, biz_id, order_id,
                   user_coupon_id, user_id, merchant_id, amount, code_status,
                   expire_at, verified_at, created_at, updated_at, deleted
            from verification_code
            where biz_type = #{bizType}
              and biz_id = #{bizId}
              and deleted = 0
            limit 1
            """)
    VerificationCode findByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    @Update("""
            update verification_code
            set code_status = 'VERIFIED',
                verified_at = #{verifiedAt},
                updated_at = #{verifiedAt}
            where id = #{id}
              and merchant_id = #{merchantId}
              and code_status = 'ACTIVE'
              and expire_at > #{verifiedAt}
              and deleted = 0
            """)
    int markVerified(@Param("id") Long id, @Param("merchantId") Long merchantId,
                     @Param("verifiedAt") LocalDateTime verifiedAt);

    @Update("""
            update verification_code
            set code_status = 'EXPIRED',
                updated_at = #{now}
            where id = #{id}
              and code_status = 'ACTIVE'
              and expire_at <= #{now}
              and deleted = 0
            """)
    int markExpired(@Param("id") Long id, @Param("now") LocalDateTime now);
}
