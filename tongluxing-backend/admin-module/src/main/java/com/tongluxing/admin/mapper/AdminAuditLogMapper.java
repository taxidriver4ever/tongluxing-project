package com.tongluxing.admin.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongluxing.admin.entity.AdminAuditLog;

/**
 * 后台审计日志 Mapper。
 */
@Mapper
public interface AdminAuditLogMapper {

    /** 插入一条后台审计日志。 */
    @Insert("""
            insert into admin_audit_log
                (id, operator_id, operator_name, action_type, target_module,
                 target_type, target_id, request_id, before_snapshot, after_snapshot,
                 operation_reason, operation_result, ip, user_agent, created_at)
            values
                (#{id}, #{operatorId}, #{operatorName}, #{actionType}, #{targetModule},
                 #{targetType}, #{targetId}, #{requestId}, #{beforeSnapshot}, #{afterSnapshot},
                 #{operationReason}, #{operationResult}, #{ip}, #{userAgent}, #{createdAt})
            """)
    void insert(AdminAuditLog log);

    /** 根据请求幂等 ID 查询审计日志。 */
    @Select("""
            select id, operator_id, operator_name, action_type, target_module,
                   target_type, target_id, request_id, before_snapshot, after_snapshot,
                   operation_reason, operation_result, ip, user_agent, created_at
            from admin_audit_log
            where request_id = #{requestId}
            limit 1
            """)
    AdminAuditLog findByRequestId(@Param("requestId") String requestId);

    /** 按筛选条件分页查询审计日志。 */
    @Select("""
            select id, operator_id, operator_name, action_type, target_module,
                   target_type, target_id, request_id, before_snapshot, after_snapshot,
                   operation_reason, operation_result, ip, user_agent, created_at
            from admin_audit_log
            where (#{operatorId} is null or operator_id = #{operatorId})
              and (#{actionType} is null or #{actionType} = '' or action_type = #{actionType})
              and (#{targetModule} is null or #{targetModule} = '' or target_module = #{targetModule})
              and (#{targetType} is null or #{targetType} = '' or target_type = #{targetType})
              and (#{targetId} is null or #{targetId} = '' or target_id = #{targetId})
              and (#{startTime} is null or created_at >= #{startTime})
              and (#{endTime} is null or created_at <= #{endTime})
            order by created_at desc, id desc
            limit #{offset}, #{size}
            """)
    List<AdminAuditLog> pageQuery(@Param("operatorId") Long operatorId,
                                  @Param("actionType") String actionType,
                                  @Param("targetModule") String targetModule,
                                  @Param("targetType") String targetType,
                                  @Param("targetId") String targetId,
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    /** 统计符合筛选条件的审计日志数量。 */
    @Select("""
            select count(1)
            from admin_audit_log
            where (#{operatorId} is null or operator_id = #{operatorId})
              and (#{actionType} is null or #{actionType} = '' or action_type = #{actionType})
              and (#{targetModule} is null or #{targetModule} = '' or target_module = #{targetModule})
              and (#{targetType} is null or #{targetType} = '' or target_type = #{targetType})
              and (#{targetId} is null or #{targetId} = '' or target_id = #{targetId})
              and (#{startTime} is null or created_at >= #{startTime})
              and (#{endTime} is null or created_at <= #{endTime})
            """)
    long countQuery(@Param("operatorId") Long operatorId,
                    @Param("actionType") String actionType,
                    @Param("targetModule") String targetModule,
                    @Param("targetType") String targetType,
                    @Param("targetId") String targetId,
                    @Param("startTime") LocalDateTime startTime,
                    @Param("endTime") LocalDateTime endTime);
}
