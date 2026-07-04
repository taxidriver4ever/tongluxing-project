package com.tongluxing.team.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 车队操作审计日志 Mapper。
 */
@Mapper
public interface TeamAuditLogMapper {

    /**
     * 写入车队操作审计日志。
     */
    @Insert("""
            insert into team_audit_log
                (id, team_id, operator_user_id, operation_type, before_json, after_json, remark,
                 created_at, updated_at, deleted)
            values
                (#{id}, #{teamId}, #{operatorUserId}, #{operationType}, #{beforeJson}, #{afterJson}, #{remark},
                 now(), now(), 0)
            """)
    void insert(@Param("id") Long id,
                @Param("teamId") Long teamId,
                @Param("operatorUserId") Long operatorUserId,
                @Param("operationType") String operationType,
                @Param("beforeJson") String beforeJson,
                @Param("afterJson") String afterJson,
                @Param("remark") String remark);
}
