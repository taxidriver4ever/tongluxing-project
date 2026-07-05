package com.tongluxing.verification.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;

import com.tongluxing.verification.entity.VerificationCompensationTask;

/**
 * 核销补偿任务 Mapper。
 */
@Mapper
public interface VerificationCompensationTaskMapper {

    /**
     * 新增待处理补偿任务。
     */
    @Insert("""
            insert into verification_compensation_task
                (id, biz_type, biz_id, idempotent_key, target_module,
                 request_payload, task_status, retry_count, next_retry_at,
                 last_error, created_at, updated_at)
            values
                (#{id}, #{bizType}, #{bizId}, #{idempotentKey}, #{targetModule},
                 #{requestPayload}, #{taskStatus}, #{retryCount}, #{nextRetryAt},
                 #{lastError}, #{createdAt}, #{updatedAt})
            """)
    void insert(VerificationCompensationTask task);

    /**
     * 查询到期待处理任务。
     */
    @Select("""
            select id, biz_type, biz_id, idempotent_key, target_module,
                   request_payload, task_status, retry_count, next_retry_at,
                   last_error, created_at, updated_at
            from verification_compensation_task
            where task_status in ('PENDING', 'FAILED')
              and (next_retry_at is null or next_retry_at <= #{now})
            order by created_at asc
            limit #{limit}
            """)
    List<VerificationCompensationTask> listDueTasks(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            update verification_compensation_task
            set task_status = 'SUCCESS',
                last_error = null,
                updated_at = #{now}
            where id = #{taskId}
            """)
    int markSuccess(@Param("taskId") Long taskId, @Param("now") LocalDateTime now);

    @Update("""
            update verification_compensation_task
            set task_status = 'FAILED',
                retry_count = retry_count + 1,
                next_retry_at = #{nextRetryAt},
                last_error = #{error},
                updated_at = #{now}
            where id = #{taskId}
            """)
    int markFailed(@Param("taskId") Long taskId, @Param("error") String error,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt, @Param("now") LocalDateTime now);
}
