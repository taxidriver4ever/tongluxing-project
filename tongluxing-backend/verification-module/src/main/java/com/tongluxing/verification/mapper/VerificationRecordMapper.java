package com.tongluxing.verification.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.verification.entity.VerificationRecord;

/**
 * 核销记录 Mapper。
 */
@Mapper
public interface VerificationRecordMapper {

    /**
     * 新增核销成功记录。
     */
    @Insert("""
            insert into verification_record
                (id, verification_code_id, verification_code, biz_type, biz_id,
                 order_id, user_coupon_id, user_id, merchant_id, operator_id,
                 amount, verification_status, location_name, longitude, latitude,
                 verified_at, reversal_status, created_at, updated_at, deleted)
            values
                (#{id}, #{verificationCodeId}, #{verificationCode}, #{bizType}, #{bizId},
                 #{orderId}, #{userCouponId}, #{userId}, #{merchantId}, #{operatorId},
                 #{amount}, #{verificationStatus}, #{locationName}, #{longitude}, #{latitude},
                 #{verifiedAt}, #{reversalStatus}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(VerificationRecord record);

    /**
     * 根据核销记录 ID 查询详情。
     */
    @Select("""
            select id, verification_code_id, verification_code, biz_type, biz_id,
                   order_id, user_coupon_id, user_id, merchant_id, operator_id,
                   amount, verification_status, location_name, longitude, latitude,
                   verified_at, reversal_status, created_at, updated_at, deleted
            from verification_record
            where id = #{id}
              and deleted = 0
            limit 1
            """)
    VerificationRecord findById(@Param("id") Long id);

    /**
     * 根据核销码 ID 查询核销记录，用于防止同一码重复核销。
     */
    @Select("""
            select id, verification_code_id, verification_code, biz_type, biz_id,
                   order_id, user_coupon_id, user_id, merchant_id, operator_id,
                   amount, verification_status, location_name, longitude, latitude,
                   verified_at, reversal_status, created_at, updated_at, deleted
            from verification_record
            where verification_code_id = #{verificationCodeId}
              and deleted = 0
            limit 1
            """)
    VerificationRecord findByCodeId(@Param("verificationCodeId") Long verificationCodeId);

    /**
     * 按商家、业务类型、状态和时间区间分页查询核销记录。
     */
    @Select("""
            select id, verification_code_id, verification_code, biz_type, biz_id,
                   order_id, user_coupon_id, user_id, merchant_id, operator_id,
                   amount, verification_status, location_name, longitude, latitude,
                   verified_at, reversal_status, created_at, updated_at, deleted
            from verification_record
            where deleted = 0
              and (#{merchantId} is null or merchant_id = #{merchantId})
              and (#{bizType} is null or #{bizType} = '' or biz_type = #{bizType})
              and (#{status} is null or #{status} = '' or verification_status = #{status})
              and (#{startTime} is null or verified_at >= #{startTime})
              and (#{endTime} is null or verified_at <= #{endTime})
            order by verified_at desc, id desc
            limit #{offset}, #{size}
            """)
    List<VerificationRecord> pageQuery(@Param("merchantId") Long merchantId,
                                       @Param("bizType") String bizType,
                                       @Param("status") String status,
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    /**
     * 统计符合查询条件的核销记录数量。
     */
    @Select("""
            select count(1)
            from verification_record
            where deleted = 0
              and (#{merchantId} is null or merchant_id = #{merchantId})
              and (#{bizType} is null or #{bizType} = '' or biz_type = #{bizType})
              and (#{status} is null or #{status} = '' or verification_status = #{status})
              and (#{startTime} is null or verified_at >= #{startTime})
              and (#{endTime} is null or verified_at <= #{endTime})
            """)
    long countQuery(@Param("merchantId") Long merchantId,
                    @Param("bizType") String bizType,
                    @Param("status") String status,
                    @Param("startTime") LocalDateTime startTime,
                    @Param("endTime") LocalDateTime endTime);

    /**
     * 更新核销记录的冲正状态。
     */
    @Update("""
            update verification_record
            set reversal_status = #{reversalStatus},
                updated_at = #{now}
            where id = #{id}
              and deleted = 0
            """)
    int updateReversalStatus(@Param("id") Long id, @Param("reversalStatus") String reversalStatus,
                             @Param("now") LocalDateTime now);
}
