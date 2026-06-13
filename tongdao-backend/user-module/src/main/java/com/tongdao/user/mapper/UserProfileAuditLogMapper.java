package com.tongdao.user.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserProfileAuditLogMapper {

    @Insert("""
            insert into user_profile_audit_log
                (id, user_id, biz_type, before_json, after_json, operator_id, operator_type, created_at)
            values
                (#{id}, #{userId}, #{bizType}, #{beforeJson}, #{afterJson}, #{operatorId}, #{operatorType}, #{createdAt})
            """)
    int insert(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("bizType") String bizType,
            @Param("beforeJson") String beforeJson,
            @Param("afterJson") String afterJson,
            @Param("operatorId") Long operatorId,
            @Param("operatorType") String operatorType,
            @Param("createdAt") LocalDateTime createdAt
    );
}
