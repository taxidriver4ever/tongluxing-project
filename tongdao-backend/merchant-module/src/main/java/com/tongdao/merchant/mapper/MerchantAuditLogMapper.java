package com.tongdao.merchant.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商家操作审计数据访问接口。
 */
@Mapper
public interface MerchantAuditLogMapper {

    /**
     * 写入商家操作审计日志。
     */
    @Insert("""
            insert into merchant_audit_log(
                id, merchant_id, operator_id, operation_type, target_type,
                target_id, before_snapshot, after_snapshot, remark, created_at
            )
            values (
                #{id}, #{merchantId}, #{operatorId}, #{operationType}, #{targetType},
                #{targetId}, #{beforeSnapshot}, #{afterSnapshot}, #{remark}, #{now}
            )
            """)
    int insertLog(@Param("id") Long id,
                  @Param("merchantId") Long merchantId,
                  @Param("operatorId") Long operatorId,
                  @Param("operationType") String operationType,
                  @Param("targetType") String targetType,
                  @Param("targetId") Long targetId,
                  @Param("beforeSnapshot") String beforeSnapshot,
                  @Param("afterSnapshot") String afterSnapshot,
                  @Param("remark") String remark,
                  @Param("now") LocalDateTime now);
}
