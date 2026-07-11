package com.tongluxing.merchant.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 商家推广带来注册用户关系 Mapper。
 */
@Mapper
public interface MerchantUserRelationMapper {

    @Select("""
            select id
            from merchant_user_relation
            where user_id = #{userId}
              and deleted = 0
            limit 1
            """)
    Long findRelationIdByUserId(@Param("userId") Long userId);

    @Insert("""
            insert into merchant_user_relation(
                id, merchant_id, promotion_code_id, promotion_code, user_id,
                registered_at, first_consumed_at, relation_status,
                created_at, updated_at, deleted
            )
            values (
                #{id}, #{merchantId}, #{promotionCodeId}, #{promotionCode}, #{userId},
                #{registeredAt}, null, 'BOUND',
                #{now}, #{now}, 0
            )
            """)
    int insertRelation(@Param("id") Long id,
                       @Param("merchantId") Long merchantId,
                       @Param("promotionCodeId") Long promotionCodeId,
                       @Param("promotionCode") String promotionCode,
                       @Param("userId") Long userId,
                       @Param("registeredAt") LocalDateTime registeredAt,
                       @Param("now") LocalDateTime now);

    @Select("""
            select count(*)
            from merchant_user_relation
            where merchant_id = #{merchantId}
              and promotion_code_id = #{promotionCodeId}
              and deleted = 0
            """)
    Long countByPromotion(@Param("merchantId") Long merchantId,
                          @Param("promotionCodeId") Long promotionCodeId);
}
