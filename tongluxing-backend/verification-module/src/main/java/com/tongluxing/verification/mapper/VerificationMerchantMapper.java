package com.tongluxing.verification.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 核销模块只读解析当前登录账号对应的已审核商家。 */
@Mapper
public interface VerificationMerchantMapper {

    @Select("""
            select id
            from merchant_profile
            where user_id = #{userId}
              and audit_status = 'APPROVED'
              and status = 'ACTIVE'
              and deleted = 0
            limit 1
            """)
    Long findApprovedMerchantId(@Param("userId") Long userId);
}
