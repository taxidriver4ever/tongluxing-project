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
 *
 * <p>这里集中维护考核模块涉及的商家、订单、核销和等级配置 SQL。
 * Service 层只组合业务规则，不直接拼接 SQL，方便后续替换成 XML Mapper 或规则引擎。</p>
 */
@Mapper
public interface AssessmentMapper {

    /**
     * 按当前登录用户查找商家资料，用于商家端“我的考核”。
     */
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

    /**
     * 按商家 ID 查询有效商家资料，供内部重算和快照接口使用。
     */
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

    /**
     * 查询商家最新一条考核分记录，用于展示当前等级和下一等级差距。
     */
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

    /**
     * 按幂等请求号查询历史考核结果，Redis 缓存过期后仍可避免重复重算。
     */
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

    /**
     * 统计周期内支付成功订单数，作为商家履约活跃度的基础指标。
     */
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

    /**
     * 统计周期内已完成订单数，用于衡量最终履约完成情况。
     */
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

    /**
     * 统计周期内退款成功订单数，作为考核扣分项。
     */
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

    /**
     * 统计周期内核销成功记录数，用于反映到店/履约确认能力。
     */
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

    /**
     * 按分数匹配等级配置，返回佣金率、排序权重和排他半径等业务快照。
     */
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

    /**
     * 查询高于当前分数的最近等级，用于计算升级还差多少分。
     */
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

    /**
     * 写入或覆盖指定商家指定周期的考核结果。
     */
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
                request_id = values(request_id),
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

    /**
     * 同步商家资料中的考核快照，供其它模块快速读取。
     */
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

    /**
     * 写入人工调整明细，用于审计考核分变化来源。
     */
    @Insert("""
            insert into assessment_manual_adjustment(
                id, merchant_id, score_delta, reason, operator_id,
                request_id, created_at, updated_at, deleted
            )
            values (
                #{id}, #{merchantId}, #{scoreDelta}, #{reason}, #{operatorId},
                #{requestId}, #{now}, #{now}, 0
            )
            """)
    int insertManualAdjustment(@Param("id") Long id,
                               @Param("merchantId") Long merchantId,
                               @Param("scoreDelta") BigDecimal scoreDelta,
                               @Param("reason") String reason,
                               @Param("operatorId") Long operatorId,
                               @Param("requestId") String requestId,
                               @Param("now") LocalDateTime now);

    @Select("select count(*) from assessment_manual_adjustment where request_id=#{requestId} and deleted=0")
    int countManualAdjustmentByRequestId(@Param("requestId") String requestId);

    /**
     * 扫描所有已审核且启用的商家，作为月度批处理对象。
     */
    @Select("""
            select id merchantId
            from merchant_profile
            where audit_status = 'APPROVED'
              and status = 'ACTIVE'
              and deleted = 0
            """)
    List<Long> listActiveMerchantIds();
}
