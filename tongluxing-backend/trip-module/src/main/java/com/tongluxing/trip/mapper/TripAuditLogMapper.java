package com.tongluxing.trip.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 行程操作审计日志 Mapper。
 */
@Mapper
public interface TripAuditLogMapper {

    /**
     * 写入行程操作审计日志。
     */
    @Insert("""
            insert into trip_audit_log
                (id, trip_id, user_id, operation_type, before_json, after_json, remark, created_at)
            values
                (#{id}, #{tripId}, #{userId}, #{operationType}, #{beforeJson}, #{afterJson}, #{remark}, #{createdAt})
            """)
    void insert(
            @Param("id") Long id,
            @Param("tripId") Long tripId,
            @Param("userId") Long userId,
            @Param("operationType") String operationType,
            @Param("beforeJson") String beforeJson,
            @Param("afterJson") String afterJson,
            @Param("remark") String remark,
            @Param("createdAt") LocalDateTime createdAt
    );
}
