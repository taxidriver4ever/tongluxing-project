package com.tongluxing.verification.mapper;

import org.apache.ibatis.annotations.Insert;
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
}
