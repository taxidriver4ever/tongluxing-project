package com.tongluxing.assessment.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.assessment.dto.AssessmentQueryDTO;

/**
 * 商家考核数据库访问接口。
 */
@Mapper
public interface AssessmentMapper {

    @Select("""
            select id merchantId,
                   user_id userId,
                   merchant_name merchantName,
                   score,
                   merchant_level merchantLevel,
                   commission_rate commissionRate,
                   rank_weight rankWeight,
                   exclusion_radius_km exclusionRadiusKm
            from merchant_profile
            where user_id = #{userId}
              and deleted = 0
            limit 1
            """)
    AssessmentQueryDTO findMerchantByUserId(@Param("userId") Long userId);

    @Select("""
            select id merchantId,
                   user_id userId,
                   merchant_name merchantName,
                   score,
                   merchant_level merchantLevel,
                   commission_rate commissionRate,
                   rank_weight rankWeight,
                   exclusion_radius_km exclusionRadiusKm
            from merchant_profile
            where id = #{merchantId}
              and deleted = 0
            limit 1
            """)
    AssessmentQueryDTO findMerchantById(@Param("merchantId") Long merchantId);

    @Select("""
            select id,
                   merchant_id merchantId,
                   period,
                   total_score totalScore,
                   merchant_level merchantLevel,
                   commission_rate commissionRate,
                   rank_weight rankWeight,
                   exclusion_radius_km exclusionRadiusKm,
                   calculate_status calculateStatus,
                   calculated_at calculatedAt
            from assessment_merchant_score
            where merchant_id = #{merchantId}
              and deleted = 0
            order by calculated_at desc
            limit 1
            """)
    AssessmentQueryDTO findLatestScore(@Param("merchantId") Long merchantId);

    @Select("""
            select id,
                   merchant_id merchantId,
                   period,
                   total_score totalScore,
                   merchant_level merchantLevel,
                   commission_rate commissionRate,
                   rank_weight rankWeight,
                   exclusion_radius_km exclusionRadiusKm,
                   calculate_status calculateStatus,
                   calculated_at calculatedAt
            from assessment_merchant_score
            where request_id = #{requestId}
              and deleted = 0
            limit 1
            """)
    AssessmentQueryDTO findScoreByRequestId(@Param("requestId") String requestId);

    @Select("""
            select count(*)
            from order_trade
            where merchant_id = #{merchantId}
              and created_at >= #{startAt}
              and created_at < #{endAt}
              and payment_status = 'SUCCESS'
              and deleted = 0
            """)
    int countPaidOrders(@Param("merchantId") Long merchantId,
                        @Param("startAt") LocalDateTime startAt,
                        @Param("endAt") LocalDateTime endAt);

    @Select("""
            select count(*)
            from order_trade
            where merchant_id = #{merchantId}
              and created_at >= #{startAt}
              and created_at < #{endAt}
              and order_status = 'COMPLETED'
              and deleted = 0
            """)
    int countCompletedOrders(@Param("merchantId") Long merchantId,
                             @Param("startAt") LocalDateTime startAt,
                             @Param("endAt") LocalDateTime endAt);

    @Select("""
            select count(*)
            from order_trade
            where merchant_id = #{merchantId}
              and created_at >= #{startAt}
              and created_at < #{endAt}
              and refund_status = 'SUCCESS'
              and deleted = 0
            """)
    int countRefundedOrders(@Param("merchantId") Long merchantId,
                            @Param("startAt") LocalDateTime startAt,
                            @Param("endAt") LocalDateTime endAt);

    @Select("""
            select count(*)
            from verification_record
            where merchant_id = #{merchantId}
              and verified_at >= #{startAt}
              and verified_at < #{endAt}
              and verification_status = 'SUCCESS'
              and deleted = 0
            """)
    int countVerifiedRecords(@Param("merchantId") Long merchantId,
                             @Param("startAt") LocalDateTime startAt,
                             @Param("endAt") LocalDateTime endAt);

    @Select("""
            select level_code merchantLevel,
                   commission_rate commissionRate,
                   rank_weight rankWeight,
                   exclusion_radius_km exclusionRadiusKm,
                   min_score minScore,
                   max_score maxScore
            from assessment_level_mapping
            where mapping_status = 'ACTIVE'
              and deleted = 0
              and min_score <= #{score}
              and max_score >= #{score}
            order by min_score desc
            limit 1
            """)
    AssessmentQueryDTO findLevelMapping(@Param("score") BigDecimal score);

    @Select("""
            select level_code merchantLevel,
                   min_score minScore
            from assessment_level_mapping
            where mapping_status = 'ACTIVE'
              and deleted = 0
              and min_score > #{score}
            order by min_score
            limit 1
            """)
    AssessmentQueryDTO findNextLevel(@Param("score") BigDecimal score);

    @Insert("""
            insert into assessment_merchant_score(
                id, merchant_id, period, total_score, merchant_level,
                commission_rate, rank_weight, exclusion_radius_km,
                calculate_status, calculated_at, request_id,
                created_at, updated_at, deleted
            )
            values (
                #{id}, #{merchantId}, #{period}, #{score}, #{level},
                #{commissionRate}, #{rankWeight}, #{exclusionRadiusKm},
                'SUCCESS', #{now}, #{requestId},
                #{now}, #{now}, 0
            )
            on duplicate key update
                total_score = values(total_score),
                merchant_level = values(merchant_level),
                commission_rate = values(commission_rate),
                rank_weight = values(rank_weight),
                exclusion_radius_km = values(exclusion_radius_km),
                calculate_status = values(calculate_status),
                calculated_at = values(calculated_at),
                updated_at = values(updated_at)
            """)
    int upsertScore(@Param("id") Long id,
                    @Param("merchantId") Long merchantId,
                    @Param("period") String period,
                    @Param("score") BigDecimal score,
                    @Param("level") String level,
                    @Param("commissionRate") BigDecimal commissionRate,
                    @Param("rankWeight") BigDecimal rankWeight,
                    @Param("exclusionRadiusKm") BigDecimal exclusionRadiusKm,
                    @Param("requestId") String requestId,
                    @Param("now") LocalDateTime now);

    @Update("""
            update merchant_profile
            set score = #{score},
                merchant_level = #{level},
                commission_rate = #{commissionRate},
                rank_weight = #{rankWeight},
                exclusion_radius_km = #{exclusionRadiusKm},
                updated_at = #{now}
            where id = #{merchantId}
              and deleted = 0
            """)
    int updateMerchantAssessment(@Param("merchantId") Long merchantId,
                                 @Param("score") BigDecimal score,
                                 @Param("level") String level,
                                 @Param("commissionRate") BigDecimal commissionRate,
                                 @Param("rankWeight") BigDecimal rankWeight,
                                 @Param("exclusionRadiusKm") BigDecimal exclusionRadiusKm,
                                 @Param("now") LocalDateTime now);

    @Select("""
            select id merchantId
            from merchant_profile
            where audit_status = 'APPROVED'
              and status = 'ACTIVE'
              and deleted = 0
            """)
    List<Long> listActiveMerchantIds();
}
