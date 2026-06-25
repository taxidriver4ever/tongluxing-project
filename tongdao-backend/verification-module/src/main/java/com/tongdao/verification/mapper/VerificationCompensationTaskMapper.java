package com.tongdao.verification.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import com.tongdao.verification.entity.VerificationCompensationTask;

@Mapper
public interface VerificationCompensationTaskMapper {

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
