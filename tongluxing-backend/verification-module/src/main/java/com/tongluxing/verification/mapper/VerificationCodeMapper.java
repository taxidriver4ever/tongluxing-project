package com.tongluxing.verification.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.verification.entity.VerificationCode;

/**
 * 核销码 Mapper。
 */
@Mapper
public interface VerificationCodeMapper {

    /**
     * 新增核销码记录。
     */
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

    /**
     * 根据核销码 ID 查询记录。
     */
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

    /**
     * 根据核销码字符串查询记录。
     */
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

    /**
     * 根据业务类型和业务 ID 查询已生成的核销码。
     */
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

    /**
     * 将可用核销码标记为已核销，带商家和过期时间条件防并发误核销。
     */
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

    /**
     * 将已过期但仍为 ACTIVE 的核销码标记为 EXPIRED。
     */
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
